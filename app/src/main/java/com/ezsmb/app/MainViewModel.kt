package com.ezsmb.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ezsmb.app.core.*
import com.ezsmb.app.core.Target
import com.ezsmb.app.transfer.TransferTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ScreenState(
    val page: String = "home", val devices: List<Device> = emptyList(), val scanning: Boolean = false,
    val busy: Boolean = false, val message: String = "", val shares: List<String> = emptyList(),
    val share: String = "", val path: String = "", val entries: List<Entry> = emptyList(),
    val login: Login? = null, val credentialTarget: Target? = null, val remember: Boolean = true,
    val enumRestricted: Boolean = false, val diagnostic: String = "",
    val anonymousTarget: Target? = null, val favorites: List<Favorite> = emptyList(), val previewRevision: Int = 0
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as EzApp
    val state = MutableStateFlow(ScreenState())
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var scanner: Job? = null
    private var operation: Job? = null
    private var credentialExpectedGuid = ""
    private var pendingFavoriteRebind: String? = null
    private var anonymousProbe: Job? = null
    private var anonymousGeneration = 0
    private var anonymousGuid = ""
    private fun cancelAnonymous() {
        ++anonymousGeneration; anonymousProbe?.cancel(); anonymousGuid = ""
        state.update { it.copy(anonymousTarget = null) }
    }
    fun checkAnonymous(target: Target?) {
        cancelAnonymous()
        if (target == null || state.value.credentialTarget?.host != target.host) return
        val version = anonymousGeneration
        anonymousProbe = viewModelScope.launch {
            delay(400)
            val current = java.util.concurrent.atomic.AtomicReference<SmbEngine.Link?>()
            try {
                val guid = coroutineScope {
                    val worker = async(Dispatchers.IO) {
                        app.engine.open(target, timeout = 2).use { link ->
                            current.set(link)
                            ensureActive()
                            check(credentialExpectedGuid.isEmpty() || credentialExpectedGuid == link.guid) { "GUID_CHANGED" }
                            if (app.engine.anonymousAccess(link, target)) link.guid else null
                        }
                    }
                    try { withTimeout(6500) { worker.await() } }
                    finally { withContext(NonCancellable + Dispatchers.IO) { current.get()?.close() }; worker.cancel() }
                }
                if (version == anonymousGeneration && guid != null) {
                    anonymousGuid = guid
                    state.update { it.copy(anonymousTarget = target) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Unsupported or inaccessible: keep normal login available. */ }
        }
    }
    fun connectAnonymous(target: Target, remember: Boolean) {
        if (state.value.anonymousTarget != target || anonymousGuid.isEmpty()) return
        val expected = anonymousGuid
        cancelAnonymous()
        work { login(target, Credentials.anonymous(), remember, expected) }
    }
    private var visible = false
    private var generation = 0
    private var scanGeneration = 0
    private val cm = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { viewModelScope.launch { if (visible && state.value.page == "home") scan() } }
        override fun onLost(network: Network) { viewModelScope.launch { if (visible && state.value.page == "home") scan() } }
    }
    init { cm.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback) }
    fun foreground(active: Boolean) { visible = active; if (active && state.value.page == "home") scan() else if (!active) stopScan() }
    fun stopScan() { ++scanGeneration; scanner?.cancel(); state.update { it.copy(scanning = false) } }
    fun scan() {
        scanner?.cancel()
        val version = ++scanGeneration
        scanner = viewModelScope.launch {
            try {
                val history = app.store.devices()
                val favorites = app.store.favorites()
                state.update { it.copy(devices = history, favorites = favorites, scanning = true, message = "") }
                app.discovery.scan(history).collect { found ->
                    app.store.discovered(found)
                    val updatedFavorites = app.store.favorites()
                    state.update { s -> s.copy(favorites = updatedFavorites, devices = (s.devices.filterNot { sameServer(it, found.guid, found.port) || (it.host == found.host && it.port == found.port && it.network == found.network) } + found)) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(message = e.message ?: "无法扫描网络") } }
            finally { if (version == scanGeneration) state.update { it.copy(scanning = false) } }
        }
    }
    private fun work(block: suspend () -> Unit) {
        operation?.cancel()
        val version = ++generation
        state.update { it.copy(busy = true, message = "", diagnostic = "") }
        operation = viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(message = friendlyError(e), diagnostic = diagnosticCode(e)) } }
            finally { if (version == generation) state.update { it.copy(busy = false) } }
        }
    }
    fun select(device: Device) {
        pendingFavoriteRebind = null
        stopScan()
        work {
            connectDevice(resolveDevice(device))
        }
    }
    private suspend fun resolveDevice(device: Device): Device {
        val network = app.discovery.lan()?.key ?: "manual"
        val candidate = state.value.devices.firstOrNull { sameServer(it, device.guid, device.port) && it.network == network && it.online } ?: device
        val checked = withContext(Dispatchers.IO) {
            runCatching { app.engine.open(Target(candidate.host, candidate.port), timeout = 2).use { link ->
                check(device.guid.isEmpty() || device.guid == link.guid) { "GUID_CHANGED" }
                candidate.copy(guid = link.guid, network = network, online = true)
            } }.getOrNull()
        }
        if (checked != null) { app.store.discovered(checked); return checked }
        check(device.guid.isNotEmpty()) { "无法连接此设备，请确认电脑已开机" }
        events.tryEmit("正在重新查找设备地址…")
        val found = app.discovery.scan(app.store.devices()).firstOrNull { sameServer(it, device.guid, device.port) }
            ?: error("没有找到原来的设备，请确认在同一局域网后刷新；电脑重启后可从附近设备重新连接，或在收藏菜单中重新关联设备")
        app.store.discovered(found)
        return found
    }
    private suspend fun connectDevice(current: Device, share: String = "", path: String = "") {
        val target = Target(current.host, current.port, share, path)
        credentialExpectedGuid = current.guid
        val saved = runCatching { app.store.saved(current) }.getOrNull()
        if (saved == null) state.update { it.copy(credentialTarget = target) }
        else try { login(target, saved.credentials, true, current.guid) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { state.update { it.copy(credentialTarget = target) }; throw e }
    }
    fun openFavorite(favorite: Favorite) {
        pendingFavoriteRebind = null
        stopScan()
        work {
            val device = resolveDevice(Device(favorite.host, favorite.port, favorite.deviceName, favorite.guid, favorite.network, saved = true))
            connectDevice(device, favorite.share, favorite.path)
        }
    }
    fun rebindFavorite(favorite: Favorite, device: Device) {
        stopScan()
        work {
            val current = resolveDevice(device)
            pendingFavoriteRebind = favorite.id
            connectDevice(current, favorite.share, favorite.path)
        }
    }
    fun toggleFavorite() {
        val s = state.value; val login = s.login ?: return
        if (s.page != "browser" || s.busy) return
        viewModelScope.launch {
            val existed = s.favorites.any { it.matches(login, s.share, s.path) }
            app.store.toggleFavorite(login, s.share, s.path)
            val favorites = app.store.favorites()
            state.update { it.copy(favorites = favorites) }
            events.emit(if (existed) "已取消收藏" else "已收藏到首页")
        }
    }
    fun removeFavorite(favorite: Favorite) = viewModelScope.launch {
        app.store.removeFavorite(favorite.id)
        val favorites = app.store.favorites(); state.update { it.copy(favorites = favorites) }
    }
    fun manual(input: String) {
        try { val target = Target.parse(input); pendingFavoriteRebind = null; credentialExpectedGuid = ""; stopScan(); state.update { it.copy(credentialTarget = target, message = "") } }
        catch (e: Exception) { state.update { it.copy(message = friendlyError(e)) } }
    }
    fun dismissCredentials() { pendingFavoriteRebind = null; cancelAnonymous(); state.update { it.copy(credentialTarget = null) } }
    fun authenticate(target: Target, credentials: Credentials, remember: Boolean) {
        cancelAnonymous(); work { login(target, credentials, remember, credentialExpectedGuid) }
    }
    private suspend fun login(target: Target, credentials: Credentials, remember: Boolean, expected: String = "") {
        val result = withContext(Dispatchers.IO) {
            app.engine.open(target).use { link ->
                link.authenticate(credentials, expected).use { session ->
                    val shares = runCatching { app.engine.shares(session) }
                    Triple(Login(target, credentials, link.guid, app.discovery.lan()?.key ?: "manual", state.value.devices.firstOrNull { it.guid == link.guid }?.name ?: target.host), shares.getOrDefault(emptyList()), shares.isFailure)
                }
            }
        }
        app.store.save(result.first, remember)
        state.update { it.copy(login = result.first, page = "shares", share = "", path = "", entries = emptyList(), shares = result.second, enumRestricted = result.third, credentialTarget = null, remember = remember, message = "") }
        if (target.share.isNotEmpty()) loadDirectory(result.first, target.share, target.path)
    }
    fun share(input: String) = work {
        val login = requireNotNull(state.value.login)
        val target = if (input.startsWith("smb://", true)) Target.parse(input) else Target.parse("smb://${login.target.host}:${login.target.port}/${input.replace('\\', '/')}")
        require(target.host.equals(login.target.host, true) && target.port == login.target.port) { "请输入当前服务器的共享路径" }
        require(target.share.isNotEmpty()) { "请输入共享名" }
        loadDirectory(login, target.share, target.path)
    }
    fun folder(entry: Entry) = work { val s = state.value; loadDirectory(requireNotNull(s.login), s.share, childPath(s.path, entry.name)) }
    fun refresh() { val s = state.value; if (s.page == "home") scan() else if (s.page == "browser") work {
        app.thumbnails.clear()
        loadDirectory(requireNotNull(s.login), s.share, s.path)
        state.update { it.copy(previewRevision = it.previewRevision + 1) }
    } else s.login?.let { work { login(it.target.copy(share = "", path = ""), it.credentials, s.remember, it.guid) } } }
    private suspend fun loadDirectory(login: Login, share: String, path: String) {
        val rebindId = pendingFavoriteRebind
        pendingFavoriteRebind = null
        val entries = withContext(Dispatchers.IO) {
            app.engine.open(login.target).use { link -> link.authenticate(login.credentials, login.guid).use { session -> app.engine.disk(session, share).use { app.engine.list(it, path) } } }
        }
        app.store.save(login.copy(target = login.target.copy(share = share, path = "")), state.value.remember)
        rebindId?.let { app.store.rebindFavorite(it, login) }
        val favorites = app.store.favorites()
        state.update { it.copy(page = "browser", share = share, path = path, entries = entries, favorites = favorites, message = "") }
    }
    fun back() {
        pendingFavoriteRebind = null
        val s = state.value
        if (s.busy) { operation?.cancel(); ++generation; state.update { it.copy(busy = false) }; return }
        val parent = parentLocation(s.page, s.path)
        if (parent.first == "browser") work { loadDirectory(requireNotNull(s.login), s.share, parent.second) }
        else if (parent.first == "shares") state.update { it.copy(page = "shares", entries = emptyList(), path = "", message = "") }
        else { state.update { it.copy(page = "home", login = null, message = "") }; if (visible) scan() }
    }
    fun download(entry: Entry) {
        val s = state.value; val login = s.login ?: return
        app.transfers.enqueue(TransferTask(name = entry.name, upload = false, login = login, share = s.share, path = s.path, guid = login.guid, total = entry.size))
        events.tryEmit("已加入下载")
    }
    fun upload(uris: List<Uri>, destination: ScreenState) {
        val login = destination.login ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val batch = java.util.UUID.randomUUID().toString()
            uris.forEach { uri ->
                try {
                    runCatching { app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: error("无法读取文件名")
                    childPath(destination.path, name)
                    app.transfers.enqueue(TransferTask(name = name, upload = true, login = login, share = destination.share, path = destination.path, source = uri.toString(), guid = login.guid, batch = batch))
                } catch (e: Exception) { events.emit(friendlyError(e)) }
            }
            events.emit("所选文件已处理，请查看传输队列")
        }
    }
    fun clearSaved() = viewModelScope.launch { app.store.clear(); app.thumbnails.clear(); events.emit("已清除保存的设备与凭据"); if (state.value.page == "home") scan() }
    override fun onCleared() { cm.unregisterNetworkCallback(networkCallback); super.onCleared() }
}
