package com.ezsmb.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ezsmb.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.ezData by preferencesDataStore("devices")
class DeviceStore(private val context: Context) {
    private val records = stringPreferencesKey("records")
    private val favoriteRecords = stringPreferencesKey("favorites")
    suspend fun favorites(): List<Favorite> = withContext(Dispatchers.IO) {
        val rows = JSONArray(context.ezData.data.first()[favoriteRecords] ?: "[]")
        (0 until rows.length()).map { rows.getJSONObject(it) }.map {
            Favorite(it.getString("id"), it.getString("guid"), it.getString("network"), it.getString("host"), it.getInt("port"), it.getString("name"), it.getString("share"), it.getString("path"))
        }
    }
    suspend fun toggleFavorite(login: Login, share: String, path: String) = withContext(Dispatchers.IO) {
        val normalized = remotePath(path)
        context.ezData.edit { prefs ->
            val rows = JSONArray(prefs[favoriteRecords] ?: "[]")
            val next = JSONArray(); var removed = false
            (0 until rows.length()).map { rows.getJSONObject(it) }.forEach {
                if (it.getString("guid") == login.guid && it.getInt("port") == login.target.port && it.getString("share") == share && it.getString("path") == normalized) removed = true else next.put(it)
            }
            if (!removed) next.put(JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("guid", login.guid).put("network", login.network)
                .put("host", login.target.host).put("port", login.target.port).put("name", login.displayName).put("share", share).put("path", normalized))
            prefs[favoriteRecords] = next.toString()
        }
    }
    suspend fun removeFavorite(id: String) = withContext(Dispatchers.IO) {
        context.ezData.edit { prefs ->
            val rows = JSONArray(prefs[favoriteRecords] ?: "[]"); val next = JSONArray()
            (0 until rows.length()).map { rows.getJSONObject(it) }.filterNot { it.getString("id") == id }.forEach(next::put)
            prefs[favoriteRecords] = next.toString()
        }
    }
    suspend fun rebindFavorite(id: String, login: Login) = withContext(Dispatchers.IO) {
        context.ezData.edit { prefs ->
            val rows = JSONArray(prefs[favoriteRecords] ?: "[]")
            (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("id") == id }?.apply {
                put("guid", login.guid); put("network", login.network); put("host", login.target.host)
                put("port", login.target.port); put("name", login.displayName)
            }
            prefs[favoriteRecords] = rows.toString()
        }
    }
    /** Update locations only after SMB negotiation; credentials never migrate between network contexts. */
    suspend fun discovered(device: Device) = withContext(Dispatchers.IO) {
        if (device.guid.isEmpty()) return@withContext
        context.ezData.edit { prefs ->
            for (key in listOf(records, favoriteRecords)) {
                val rows = JSONArray(prefs[key] ?: "[]"); var changed = false
                (0 until rows.length()).map { rows.getJSONObject(it) }.forEach { row ->
                    if (row.getString("guid") == device.guid && row.getInt("port") == device.port &&
                        (key == favoriteRecords || row.getString("network") == device.network)) {
                        if (row.getString("host") != device.host || row.optString("name") != device.name || row.getString("network") != device.network) {
                            row.put("host", device.host).put("name", device.name).put("network", device.network); changed = true
                        }
                    }
                }
                if (changed) prefs[key] = rows.toString()
            }
        }
    }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey("ezsmb.credentials", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("ezsmb.credentials", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(text: String, identity: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(identity.toByteArray()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }
    private fun decrypt(text: String, identity: String): String {
        val data = Base64.decode(text, Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))); updateAAD(identity.toByteArray())
            String(doFinal(data.copyOfRange(12, data.size)))
        }
    }
    private suspend fun rows() = JSONArray(context.ezData.data.first()[records] ?: "[]")
    suspend fun devices(): List<Device> = withContext(Dispatchers.IO) {
        val array = rows()
        (0 until array.length()).map { array.getJSONObject(it) }.map {
            Device(it.getString("host"), it.getInt("port"), it.optString("name", it.getString("host")), it.getString("guid"), it.getString("network"), saved = true)
        }
    }
    suspend fun saved(device: Device): Login? = withContext(Dispatchers.IO) {
        // Never release a password based on IP alone. Caller negotiates GUID before lookup.
        if (device.guid.isEmpty()) return@withContext null
        val array = rows()
        val row = (0 until array.length()).map { array.getJSONObject(it) }.firstOrNull {
            it.getString("guid") == device.guid && it.getString("network") == device.network && it.getInt("port") == device.port
        } ?: return@withContext null
        val secret = row.optString("secret")
        if (secret.isEmpty()) return@withContext null
        val payload = JSONObject(decrypt(secret, device.identity))
        Login(Target(device.host, device.port, row.optString("share")), Credentials(payload.getString("user"), payload.getString("password"), payload.getString("domain"), payload.optBoolean("anonymous", false)), device.guid, device.network, device.name)
    }
    suspend fun save(login: Login, remember: Boolean) = withContext(Dispatchers.IO) {
        val device = Device(login.target.host, login.target.port, guid = login.guid, network = login.network)
        val secret = if (remember) encrypt(JSONObject().put("user", login.credentials.username).put("password", login.credentials.password).put("domain", login.credentials.domain).put("anonymous", login.credentials.anonymous).toString(), device.identity) else ""
        context.ezData.edit { prefs ->
            val old = JSONArray(prefs[records] ?: "[]")
            val next = JSONArray()
            (0 until old.length()).map { old.getJSONObject(it) }.filterNot { it.getString("guid") == login.guid && it.getString("network") == login.network }.takeLast(29).forEach(next::put)
            next.put(JSONObject().put("name", login.displayName).put("host", login.target.host).put("port", login.target.port).put("guid", login.guid).put("network", login.network).put("share", login.target.share).put("secret", secret))
            prefs[records] = next.toString()
        }
    }
    suspend fun clear() { context.ezData.edit { it.clear() } }
}
