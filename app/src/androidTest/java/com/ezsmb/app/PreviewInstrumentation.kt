package com.ezsmb.app

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import com.ezsmb.app.core.*
import com.ezsmb.app.core.Target
import com.ezsmb.app.data.Thumbnails
import kotlinx.coroutines.runBlocking
import java.util.Properties

/** Separate test APK. Credentials are pushed into app-private storage, never packaged or logged. */
class PreviewInstrumentation : Instrumentation() {
    private var mode: String? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); mode = arguments?.getString("mode"); start() }
    override fun onStart() {
        if (mode == "favorites") { testFavorites(); return }
        val result = Bundle()
        val config = java.io.File(targetContext.filesDir, "preview-test.properties")
        try {
            val p = Properties().apply { config.inputStream().use { load(it) } }
            require(p.getProperty("host") == "127.0.0.1")
            val engine = SmbEngine()
            val target = Target("127.0.0.1", p.getProperty("port").toInt())
            val credentials = if (p.getProperty("anonymous") == "true") Credentials.anonymous() else Credentials(p.getProperty("user"), p.getProperty("password"))
            val share = p.getProperty("share")
            val guid = engine.open(target).use { it.guid }
            if (credentials.anonymous) {
                engine.open(target).use { check(engine.anonymousAccess(it, target)) }
                engine.open(target).use { check(!engine.anonymousAccess(it, target.copy(share = "DOES_NOT_EXIST"))) }
                engine.open(target).use { link -> link.authenticate(credentials, guid).use { session -> engine.disk(session, share).use { disk ->
                    val name = ".ezsmb-part-${java.util.UUID.randomUUID()}"
                    val bytes = ByteArray(1024) { (it % 251).toByte() }
                    try {
                        engine.createTemp(disk, name).use { output ->
                            var position = 0
                            while (position < bytes.size) { val n = output.write(bytes, position.toLong(), position, bytes.size - position).toInt(); check(n > 0); position += n }
                            output.flush()
                        }
                        val received = ByteArray(bytes.size)
                        engine.read(disk, name).use { input ->
                            var position = 0
                            while (position < received.size) { val n = input.read(received, position.toLong(), position, received.size - position); check(n > 0); position += n }
                        }
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        check(digest.digest(bytes).contentEquals(digest.digest(received)))
                    } finally { disk.rm(name) }
                } } }
                result.putString("anonymous", "PASS enumeration, inaccessible share hidden, 1KB upload/read SHA256")
            }
            val login = Login(target, credentials, guid, "isolated-instrumentation")
            val entries = engine.open(target).use { link -> link.authenticate(credentials, guid).use { session -> engine.disk(session, share).use { engine.list(it, "") } } }
            val loader = Thumbnails(engine)
            runBlocking {
                for (name in listOf("preview-image-folder", "preview-video-folder", "preview-empty-folder")) {
                    val entry = entries.single { it.name == name && it.directory }
                    val bitmap = loader.load(login, share, "", entry)
                    if (name == "preview-empty-folder") check(bitmap == null) else {
                        check(bitmap != null && bitmap.width in 1..768 && bitmap.height in 1..768)
                        check(loader.load(login, share, "", entry) === bitmap)
                    }
                    result.putString(name, if (bitmap == null) "PASS folder icon fallback" else "PASS media cover and cache")
                }
                for (name in listOf("preview-test.jpg", "preview-test.mp4")) {
                    val entry = entries.single { it.name == name }
                    val bitmap = requireNotNull(loader.load(login, share, "", entry)) { "Preview unavailable: $name" }
                    check(bitmap.width in 1..768 && bitmap.height in 1..768)
                    check(loader.load(login, share, "", entry) === bitmap)
                    result.putString(name, "PASS ${bitmap.width}x${bitmap.height}; memory cache hit")
                }
            }
            loader.clear()
            result.putString("stream", "JPEG and MP4 decoded over real SMB on Android\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAILED: ${e.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, result)
        } finally { config.delete() }
    }
    private fun testFavorites() {
        val result = Bundle()
        try {
            runBlocking {
                val store = com.ezsmb.app.data.DeviceStore(targetContext)
                val guid = "test-${java.util.UUID.randomUUID()}"
                val login = Login(Target("192.0.2.1"), Credentials.anonymous(), guid, "test-network", "Test fixture")
                var id: String? = null
                try {
                    store.toggleFavorite(login, "共享", "中文/子目录")
                    val favorite = store.favorites().single { it.guid == guid }; id = favorite.id
                    check(favorite.path == "中文\\子目录")
                    check(com.ezsmb.app.data.DeviceStore(targetContext).favorites().any { it.id == id })
                    store.discovered(Device("192.0.2.77", guid = guid, network = "test-network", name = "Test fixture"))
                    check(store.favorites().single { it.id == id }.host == "192.0.2.77")
                    store.discovered(Device("192.0.2.88", guid = "wrong-guid", network = "test-network"))
                    check(store.favorites().single { it.id == id }.host == "192.0.2.77")
                    store.rebindFavorite(favorite.id, login.copy(guid = "$guid-new", target = Target("192.0.2.99")))
                    check(store.favorites().single { it.id == id }.guid == "$guid-new")
                } finally { id?.let { store.removeFavorite(it) } }
                check(store.favorites().none { it.id == id })
            }
            result.putString("stream", "PASS favorite persistence, Chinese path, simulated DHCP address update, wrong identity rejection, explicit rebind and removal\n")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAILED: ${e.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
