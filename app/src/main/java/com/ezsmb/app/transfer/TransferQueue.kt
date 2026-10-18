package com.ezsmb.app.transfer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.ezsmb.app.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class ConflictChoice { SKIP, RENAME, OVERWRITE }
data class TransferTask(
    val id: String = UUID.randomUUID().toString(), val name: String, val upload: Boolean,
    val login: Login?, val share: String, val path: String, val source: String = "", val guid: String,
    val state: TransferState = TransferState.QUEUED, val done: Long = 0, val total: Long = -1,
    val speed: Long = 0, val error: String = "", val result: String = "", val batch: String = ""
) { val active get() = state in setOf(TransferState.QUEUED, TransferState.PREPARING, TransferState.RUNNING, TransferState.FINALIZING) }
data class Conflict(val id: String, val name: String, val upload: Boolean, val batch: String, val overwriteAllowed: Boolean)

class TransferQueue(private val context: Context, private val engine: SmbEngine) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableTasks = MutableStateFlow<List<TransferTask>>(emptyList())
    val tasks = mutableTasks.asStateFlow()
    val conflict = MutableStateFlow<Conflict?>(null)
    private val answers = ConcurrentHashMap<String, CompletableDeferred<ConflictChoice>>()
    private val batchChoices = ConcurrentHashMap<String, ConflictChoice>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val links = ConcurrentHashMap<String, SmbEngine.Link>()
    private val streams = ConcurrentHashMap<String, java.io.Closeable>()
    private val journal = AtomicFile(java.io.File(context.noBackupFilesDir, "transfers.json"))
    private val journalLock = Mutex()
    private val ready = scope.async { restore() }
    private val workerLock = Mutex()
    private val commitGate = Any()

    private suspend fun restore() {
        if (!journal.baseFile.exists()) return
        runCatching {
            val list = JSONArray(journal.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            mutableTasks.value = (0 until list.length()).map { list.getJSONObject(it) }.map {
                val pendingUri = it.optString("pending")
                if (pendingUri.isNotEmpty()) {
                    val uri = Uri.parse(pendingUri)
                    val pending = context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.IS_PENDING), null, null, null)?.use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 } ?: false
                    if (pending) context.contentResolver.delete(uri, null, null)
                }
                TransferTask(id = it.getString("id"), name = it.getString("name"), upload = it.getBoolean("upload"), login = null,
                    share = it.getString("share"), path = it.getString("path"), source = it.optString("source"), guid = it.getString("guid"),
                    state = TransferState.INTERRUPTED, error = "上次传输被中断，请连接原服务器后重试")
            }
        }.onFailure { mutableTasks.value = emptyList() }
    }
    private suspend fun persist() = journalLock.withLock {
        val rows = JSONArray()
        mutableTasks.value.filter { it.state != TransferState.COMPLETED && it.state != TransferState.CANCELLED }.forEach {
            rows.put(JSONObject().put("id", it.id).put("name", it.name).put("upload", it.upload).put("share", it.share)
                .put("path", it.path).put("source", it.source).put("guid", it.guid).put("pending", if (!it.upload && it.result.startsWith("content:")) it.result else ""))
        }
        val out = journal.startWrite()
        try { out.write(rows.toString().toByteArray()); journal.finishWrite(out) } catch (e: Exception) { journal.failWrite(out); throw e }
    }
    fun enqueue(task: TransferTask) { scope.launch {
        ready.await()
        mutableTasks.update { list -> list.filter { it.active } + list.filter { !it.active }.takeLast(30) + task }
        try {
            persist()
            ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java))
        } catch (_: Exception) { update(task.id) { it.copy(state = TransferState.FAILED, error = "无法启动后台传输，请回到应用后重试") }; persist() }
    } }
    private fun update(id: String, transform: (TransferTask) -> TransferTask) {
        mutableTasks.update { list -> list.map { old ->
            if (old.id != id) old else transform(old).let { next -> if (old.state.canTransitionTo(next.state)) next else old }
        } }
    }
    fun answer(choice: ConflictChoice, applyToBatch: Boolean = false) { conflict.value?.let {
        if (applyToBatch && it.batch.isNotEmpty()) batchChoices[it.batch] = choice
        answers[it.id]?.complete(choice)
    } }
    fun cancel(id: String) {
        synchronized(commitGate) {
            val task = mutableTasks.value.find { it.id == id } ?: return
            // The commit transition and cancel decision share a lock.
            if (task.state == TransferState.FINALIZING || !task.active) return
            cancelled.add(id)
        }
        answers[id]?.complete(ConflictChoice.SKIP)
        scope.launch { streams[id]?.let { runCatching { it.close() } }; links[id]?.let { runCatching { it.close() } }; update(id) { if (it.state == TransferState.QUEUED) it.copy(state = TransferState.CANCELLED) else it }; persist() }
    }
    fun interruptAll(message: String) {
        mutableTasks.value.filter { it.active }.forEach { cancel(it.id) }
        mutableTasks.update { list -> list.map { if (it.active && it.state != TransferState.FINALIZING) it.copy(state = TransferState.INTERRUPTED, error = message) else it } }
        scope.launch { persist() }
    }
    fun retry(task: TransferTask, currentLogin: Login?) {
        val login = currentLogin?.takeIf { it.guid == task.guid } ?: task.login
        if (login == null) { update(task.id) { it.copy(error = "请先连接原服务器，再重试") }; return }
        enqueue(task.copy(id = UUID.randomUUID().toString(), login = login, state = TransferState.QUEUED, done = 0, speed = 0, error = "", result = ""))
        mutableTasks.update { it.filterNot { old -> old.id == task.id } }
    }
    private fun checkCancel(id: String) { if (id in cancelled) throw CancellationException("已取消") }
    private fun beginFinalizing(task: TransferTask, count: Long) = synchronized(commitGate) {
        checkCancel(task.id)
        update(task.id) { it.copy(state = TransferState.FINALIZING, done = count, total = count) }
    }
    private suspend fun choose(task: TransferTask, overwriteAllowed: Boolean = task.upload): ConflictChoice {
        batchChoices[task.batch]?.let { return it }
        val answer = CompletableDeferred<ConflictChoice>()
        answers[task.id] = answer
        conflict.value = Conflict(task.id, task.name, task.upload, task.batch, overwriteAllowed)
        return try { answer.await().also { checkCancel(task.id) } } finally { answers.remove(task.id); conflict.value = null }
    }
    suspend fun runQueue() = workerLock.withLock {
        ready.await()
        while (true) {
            val task = mutableTasks.value.firstOrNull { it.state == TransferState.QUEUED } ?: break
            try {
                checkCancel(task.id)
                update(task.id) { it.copy(state = TransferState.PREPARING) }; persist()
                transfer(task)
            } catch (e: Exception) {
                val cancel = e is CancellationException || task.id in cancelled
                update(task.id) { if (it.state == TransferState.INTERRUPTED || it.state == TransferState.COMPLETED) it else it.copy(state = if (cancel) TransferState.CANCELLED else TransferState.FAILED, speed = 0, error = (if (cancel) "已取消" else friendlyError(e)) + if (it.error.isNotEmpty()) "\n${it.error}" else "") }
            } finally { streams.remove(task.id); links.remove(task.id); cancelled.remove(task.id); persist() }
        }
    }

    private suspend fun transfer(task: TransferTask) {
        val login = requireNotNull(task.login)
        engine.open(login.target).use { link ->
            links[task.id] = link
            checkCancel(task.id)
            link.authenticate(login.credentials, login.guid).use { session ->
                engine.disk(session, task.share).use { disk ->
                    if (task.upload) upload(task, disk) else download(task, disk)
                }
            }
        }
    }
    private fun progress(task: TransferTask, total: Long): (Long) -> Unit {
        var lastTime = System.nanoTime(); var lastBytes = 0L
        update(task.id) { it.copy(state = TransferState.RUNNING, total = total) }
        return { count ->
            checkCancel(task.id)
            val now = System.nanoTime()
            if (now - lastTime >= 150_000_000L || count == total) {
                val speed = ((count - lastBytes).toDouble() * 1e9 / (now - lastTime).coerceAtLeast(1)).toLong()
                update(task.id) { it.copy(done = count, speed = speed) }; lastBytes = count; lastTime = now
            }
        }
    }
    private suspend fun upload(task: TransferTask, disk: com.hierynomus.smbj.share.DiskShare) {
        val resolver = context.contentResolver
        val uri = Uri.parse(task.source)
        var total = -1L
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) total = it.getLong(0) }
        var destination = childPath(task.path, task.name)
        var replace = false
        if (engine.fileExists(disk, destination) || engine.folderExists(disk, destination)) {
            when (choose(task)) {
                ConflictChoice.SKIP -> throw CancellationException()
                ConflictChoice.OVERWRITE -> { require(!engine.folderExists(disk, destination)) { "不能覆盖文件夹" }; replace = true }
                ConflictChoice.RENAME -> { var n = 1; do { destination = childPath(task.path, renamed(task.name, n++)) } while (engine.fileExists(disk, destination) || engine.folderExists(disk, destination)) }
            }
        }
        val temp = childPath(task.path, ".ezsmb-part-${task.id}")
        var ownsTemp = false; var committed = false
        try {
            engine.createTemp(disk, temp).use { file ->
                ownsTemp = true
                val report = progress(task, total)
                var count = 0L
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取选中的文件" }
                    streams[task.id] = input
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        checkCancel(task.id)
                        val n = input.read(buffer)
                        if (n == -1) break
                        if (n == 0) continue
                        var offset = 0
                        while (offset < n) { checkCancel(task.id); val written = file.write(buffer, count, offset, n - offset).toInt(); check(written > 0); offset += written; count += written; report(count) }
                    }
                }
                check(total < 0 || total == count) { "SOURCE_CHANGED" }
                beginFinalizing(task, count)
                file.flush()
                check(file.fileInformation.standardInformation.endOfFile == count) { "写入长度不一致" }
                file.rename(destination, replace)
                committed = true
                update(task.id) { it.copy(state = TransferState.COMPLETED, speed = 0, result = destination) }
            }
        } finally {
            if (ownsTemp && !committed) {
                val cleaned = runCatching { disk.rm(temp) }.isSuccess
                if (!cleaned) update(task.id) { it.copy(error = "远端可能残留本任务临时文件：.ezsmb-part-${task.id}") }
            }
        }
    }
    private suspend fun download(task: TransferTask, disk: com.hierynomus.smbj.share.DiskShare) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        var output: Uri? = null
        var committed = false
        engine.read(disk, childPath(task.path, task.name)).use { file ->
            val before = file.fileInformation
            val total = before.standardInformation.endOfFile
            val existing = resolver.query(collection, arrayOf(MediaStore.Downloads._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME), "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?", arrayOf(task.name, com.ezsmb.app.downloadDirectory), null)?.use {
                if (it.moveToFirst()) android.content.ContentUris.withAppendedId(collection, it.getLong(0)) to (it.getString(1) == context.packageName && it.count == 1) else null
            }
            var replace: Uri? = null
            if (existing != null) when (choose(task, existing.second)) {
                ConflictChoice.SKIP -> throw CancellationException()
                ConflictChoice.OVERWRITE -> { check(existing.second); replace = existing.first }
                ConflictChoice.RENAME -> Unit
            }
            try {
                output = resolver.insert(collection, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, task.name)
                    put(MediaStore.Downloads.MIME_TYPE, MimeTypeMap.getSingleton().getMimeTypeFromExtension(task.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) ?: "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, com.ezsmb.app.downloadDirectory); put(MediaStore.Downloads.IS_PENDING, 1)
                }) ?: error("无法创建下载文件")
                update(task.id) { it.copy(result = output.toString()) }
                persist()
                val report = progress(task, total)
                var count = 0L
                resolver.openOutputStream(output, "w").use { stream ->
                    requireNotNull(stream)
                    streams[task.id] = stream
                    val buffer = ByteArray(256 * 1024)
                    while (count < total) {
                        checkCancel(task.id)
                        val n = file.read(buffer, count, 0, minOf(buffer.size.toLong(), total - count).toInt())
                        check(n > 0) { "远端文件提前结束" }
                        stream.write(buffer, 0, n); count += n; report(count)
                    }
                    stream.flush()
                }
                val after = file.fileInformation
                check(count == total && after.standardInformation.endOfFile == total && before.basicInformation.lastWriteTime == after.basicInformation.lastWriteTime) { "SOURCE_CHANGED" }
                beginFinalizing(task, count)
                check(resolver.update(output, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) == 1)
                committed = true
                // Preserve the old download until the new complete file is visible. This is not an atomic filesystem replace.
                var warning = ""
                replace?.let { old ->
                    runCatching {
                        check(resolver.delete(old, null, null) == 1)
                        check(resolver.update(output, ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, task.name) }, null, null) == 1)
                    }.onFailure { warning = "文件已完整下载，但同名替换未全部完成，请在 Downloads/ezSMB 查看新文件" }
                }
                update(task.id) { it.copy(state = TransferState.COMPLETED, speed = 0, result = output.toString(), error = warning) }
            } finally { if (!committed) output?.let { resolver.delete(it, null, null) } }
        }
    }
}
