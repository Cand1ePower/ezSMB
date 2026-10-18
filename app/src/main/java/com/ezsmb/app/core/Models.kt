package com.ezsmb.app.core

import java.net.URI
import java.util.Locale

/** A VPN may advertise WIFI as its underlying transport; never scan its tunnel CIDR. */
fun isPhysicalLan(wifi: Boolean, ethernet: Boolean, vpn: Boolean): Boolean = !vpn && (wifi || ethernet)

data class Target(val host: String, val port: Int = 445, val share: String = "", val path: String = "") {
    companion object {
        fun parse(input: String): Target {
            val text = input.trim().replace('\\', '/')
            val uri = URI((if (text.startsWith("smb://", true)) text else "smb://${text.removePrefix("//")}").replace(" ", "%20"))
            require(uri.scheme.equals("smb", true) && uri.userInfo == null && uri.query == null && uri.fragment == null) { "请输入不含账户密码的 SMB 地址" }
            val host = uri.host ?: throw IllegalArgumentException("请输入有效的 IP 或主机名")
            require(host.isNotBlank())
            val port = if (uri.port == -1) 445 else uri.port
            require(port in 1..65535) { "端口应为 1–65535" }
            val pieces = remotePath(uri.path.orEmpty()).split('\\').filter { it.isNotEmpty() }
            return Target(host, port, pieces.firstOrNull().orEmpty(), pieces.drop(1).joinToString("\\"))
        }
    }
}

fun remotePath(value: String): String {
    val parts = value.replace('/', '\\').split('\\').filter { it.isNotEmpty() }
    require(parts.none { it == "." || it == ".." || it.any { c -> c == '\u0000' || c == ':' || c < ' ' } }) { "路径包含不安全的字符" }
    return parts.joinToString("\\")
}
fun childPath(parent: String, name: String): String {
    require(name.isNotEmpty() && !name.contains('/') && !name.contains('\\')) { "文件名无效" }
    return remotePath(if (parent.isEmpty()) name else "$parent\\$name")
}
fun renamed(name: String, number: Int): String {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    return name.substring(0, dot) + " ($number)" + name.substring(dot)
}
data class Credentials(val username: String, val password: String, val domain: String = "", val anonymous: Boolean = false) {
    init { require(!anonymous || (username.isEmpty() && password.isEmpty() && domain.isEmpty())) }
    companion object { fun anonymous() = Credentials("", "", anonymous = true) }
    override fun toString() = "Credentials([redacted])"
}
data class Device(val host: String, val port: Int = 445, val name: String = host, val guid: String = "", val network: String = "", val online: Boolean = false, val saved: Boolean = false) {
    val identity get() = "$network|${guid.ifEmpty { "$host:$port" }}"
}
data class Entry(val name: String, val directory: Boolean, val size: Long, val modified: Long)
data class Login(val target: Target, val credentials: Credentials, val guid: String, val network: String, val displayName: String = target.host)

data class Favorite(val id: String, val guid: String, val network: String, val host: String, val port: Int,
    val deviceName: String, val share: String, val path: String) {
    val title get() = path.substringAfterLast('\\').ifEmpty { share }
    fun matches(login: Login, share: String, path: String) = guid == login.guid && port == login.target.port &&
        this.share == share && this.path == remotePath(path)
}

fun parentLocation(page: String, path: String): Pair<String, String> = when {
    page == "browser" && remotePath(path).isNotEmpty() -> "browser" to remotePath(path).substringBeforeLast('\\', "")
    page == "browser" -> "shares" to ""
    else -> "home" to ""
}

fun sameServer(device: Device, guid: String, port: Int) = guid.isNotEmpty() && device.guid == guid && device.port == port

fun privateV4(ip: Long): Boolean = (ip ushr 24 == 10L) || (ip ushr 20 == 0xac1L) || (ip ushr 16 == 0xc0a8L)
fun ipv4Number(ip: String): Long = ip.split('.').also { require(it.size == 4) }.fold(0L) { n, p -> (n shl 8) or p.toInt().also { require(it in 0..255) }.toLong() }
fun ipv4Text(ip: Long) = (3 downTo 0).joinToString(".") { ((ip ushr (it * 8)) and 255).toString() }
fun scanCandidates(ip: String, prefix: Int): List<String> {
    val self = ipv4Number(ip)
    if (!privateV4(self) || prefix !in 1..30) return emptyList()
    val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
    val network = self and mask
    val broadcast = network or (mask xor 0xffffffffL)
    val localBlock = self and 0xffffff00L
    return (maxOf(network + 1, localBlock)..minOf(broadcast - 1, localBlock + 255))
        .filter { it != self && privateV4(it) }.take(256).map(::ipv4Text)
}
val naturalNames = Comparator<String> { a, b ->
    val regex = Regex("\\d+|\\D+")
    val aa = regex.findAll(a.lowercase(Locale.ROOT)).map { it.value }.toList()
    val bb = regex.findAll(b.lowercase(Locale.ROOT)).map { it.value }.toList()
    var result = 0
    for (i in 0 until minOf(aa.size, bb.size)) {
        result = if (aa[i][0].isDigit() && bb[i][0].isDigit()) aa[i].toBigInteger().compareTo(bb[i].toBigInteger()) else aa[i].compareTo(bb[i])
        if (result != 0) break
    }
    if (result == 0) aa.size.compareTo(bb.size) else result
}
fun friendlyError(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(12).toList()
    val detail = causes.joinToString(" ") { it.message.orEmpty() }.uppercase(Locale.ROOT)
    return when {
        "LOGON_FAILURE" in detail || "WRONG_PASSWORD" in detail -> "无法登录，请检查用户名、密码和域"
        "ACCESS_DENIED" in detail -> "没有访问权限，请检查共享和文件夹权限"
        "DISK_FULL" in detail || "NO SPACE" in detail || "ENOSPC" in detail -> "空间不足，文件未完成"
        "BAD_NETWORK_NAME" in detail -> "找不到共享，请检查共享名"
        "COLLISION" in detail -> "已有同名文件，请选择其他名称或明确允许覆盖"
        "GUID" in detail -> "服务器身份发生变化，请重新连接并确认账户"
        "SOURCE_CHANGED" in detail -> "源文件已变化，请重新传输"
        causes.any { it is java.net.UnknownHostException } -> "无法解析电脑地址，请检查主机名或输入 IP"
        causes.any { it is java.net.ConnectException } -> "没有连接到共享服务，请确认电脑在线、端口和防火墙允许连接"
        causes.any { it is java.net.SocketTimeoutException } -> "连接超时，请检查 Wi-Fi、电脑状态和共享服务"
        "NOT_SUPPORTED" in detail || "NEGOTIAT" in detail -> "无法建立兼容的 SMB2/3 连接，请查看服务器安全配置"
        error is IllegalArgumentException -> error.message ?: "输入无效"
        else -> "连接或传输未完成，请检查网络和共享服务后重试"
    }
}
fun diagnosticCode(error: Throwable): String = Regex("STATUS_[A-Z_]+").find(generateSequence(error) { it.cause }.take(12).joinToString(" ") { it.message.orEmpty() })?.value ?: error.javaClass.simpleName
