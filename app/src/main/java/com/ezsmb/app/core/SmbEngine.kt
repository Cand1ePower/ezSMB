package com.ezsmb.app.core

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.RPCTransport
import com.rapid7.client.dcerpc.Interface
import com.hierynomus.smbj.share.PipeShare
import com.hierynomus.mssmb2.SMB2ImpersonationLevel
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/** A separate session per operation/transfer: closing a transfer cannot abort browsing. */
class SmbEngine {
    fun open(target: Target, factory: SocketFactory = TimedSockets(), timeout: Long = 15): Link {
        val client = SMBClient(SmbConfig.builder().withSocketFactory(factory)
            .withTimeout(timeout, TimeUnit.SECONDS).withSoTimeout(timeout, TimeUnit.SECONDS)
            .withSigningRequired(true).withBufferSize(256 * 1024).build())
        try { return Link(client, client.connect(target.host, target.port)) }
        catch (e: Throwable) { client.close(); throw e }
    }

    class Link(private val client: SMBClient, val connection: Connection) : Closeable {
        val guid: String get() = connection.connectionContext.serverGuid.toString()
        val dialect: String get() = connection.connectionContext.negotiatedProtocol.dialect.toString()
        /** Discovery-only connection: send NTLM Type 1, read server's Type 2 name, then close.
         * Never sends Type 3, a password, or attempts guest login. Name is a display hint only.
         */
        fun probeName(): String? = runCatching {
            val context = connection.connectionContext
            val auth = com.hierynomus.smbj.auth.NtlmAuthenticator().apply { init(SmbConfig.createDefaultConfig()) }
            val token = auth.authenticate(AuthenticationContext("", charArrayOf(), ""), byteArrayOf(), context).negToken
            val buffer = com.hierynomus.protocol.commons.buffer.Buffer.PlainBuffer(com.hierynomus.protocol.commons.buffer.Endian.LE)
            token.write(buffer)
            val request = com.hierynomus.mssmb2.messages.SMB2SessionSetup(context.negotiatedProtocol.dialect,
                setOf(com.hierynomus.mssmb2.messages.SMB2SessionSetup.SMB2SecurityMode.SMB2_NEGOTIATE_SIGNING_REQUIRED), context.clientCapabilities)
            request.securityBuffer = buffer.compactData
            val response = connection.send<com.hierynomus.mssmb2.messages.SMB2SessionSetup>(request).get(3, TimeUnit.SECONDS)
            if (response.header.statusCode != 0xc0000016L) return@runCatching null
            val challenge = com.hierynomus.ntlm.messages.NtlmChallenge()
            val target = com.hierynomus.spnego.NegTokenTarg().read(response.securityBuffer)
            challenge.read(com.hierynomus.protocol.commons.buffer.Buffer.PlainBuffer(target.responseToken, com.hierynomus.protocol.commons.buffer.Endian.LE))
            val info = challenge.targetInfo ?: return@runCatching null
            val pair = info.getAvPair<com.hierynomus.ntlm.av.AvPairString>(com.hierynomus.ntlm.av.AvId.MsvAvNbComputerName)
            pair?.value?.trim()?.takeIf { it.isNotEmpty() && it.length <= 255 && it.none { c -> c.isISOControl() } }
        }.getOrNull()
        fun authenticate(credentials: Credentials, expectedGuid: String = ""): Session {
            check(expectedGuid.isEmpty() || expectedGuid == guid) { "GUID_CHANGED" }
            if (credentials.anonymous) {
                check(!connection.connectionContext.isServerRequiresSigning) { "服务器要求签名，请使用账户登录" }
                return connection.authenticate(AuthenticationContext.anonymous())
            }
            require(credentials.username.isNotBlank()) { "请输入用户名" }
            val password = credentials.password.toCharArray()
            val domain = credentials.domain.ifEmpty { if ('\\' in credentials.username) credentials.username.substringBefore('\\') else "" }
            val user = if ('\\' in credentials.username) credentials.username.substringAfter('\\') else credentials.username
            return try {
                connection.authenticate(AuthenticationContext(user, password, domain)).also { session ->
                    if (session.isAnonymous || session.isGuest) {
                        runCatching { session.close() }
                        error("服务器未验证此账户，请检查账户信息")
                    }
                }
            }
            finally { password.fill('\u0000') }
        }
        override fun close() { runCatching { connection.close(true) }; client.close() }
    }

    /** Read-only proof of usable anonymous access; never treats session setup alone as success. */
    fun anonymousAccess(link: Link, target: Target): Boolean {
        link.authenticate(Credentials.anonymous()).use { session ->
            val names = if (target.share.isNotEmpty()) listOf(target.share) else shares(session).take(3)
            return names.any { name ->
                runCatching { disk(session, name).use { list(it, target.path) }; true }.getOrDefault(false)
            }
        }
    }

    fun shares(session: Session): List<String> {
        return (session.connectShare("IPC$") as PipeShare).use { ipc ->
            ipc.open("srvsvc", SMB2ImpersonationLevel.Impersonation,
                setOf(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE), null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OPEN, null).use { pipe ->
                val transport = object : RPCTransport() {
                    override fun transact(packetOut: ByteArray, packetIn: ByteArray) = pipe.transact(packetOut, packetIn)
                    override fun read(packetIn: ByteArray) = pipe.read(packetIn)
                    override fun write(packetOut: ByteArray) {
                        var offset = 0
                        while (offset < packetOut.size) { val n = pipe.write(packetOut, offset, packetOut.size - offset); check(n > 0); offset += n }
                    }
                }
                transport.bind(Interface.SRVSVC_V3_0, Interface.NDR_32BIT_V2)
                ServerService(transport).shares1.filter { (it.type and 0xffff) == 0 && !it.netName.equals("IPC$", true) }.map { it.netName }.sortedWith(naturalNames)
            }
        }
    }
    fun disk(session: Session, name: String): DiskShare = session.connectShare(name) as? DiskShare ?: error("不是磁盘共享")
    /** Read one directory only, with bounded entries; closing the directory also closes its iterator handle. */
    fun previewEntries(share: DiskShare, path: String, checkBudget: () -> Unit): List<Entry> {
        return share.openDirectory(remotePath(path), setOf(AccessMask.FILE_LIST_DIRECTORY), null,
            SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { directory ->
            val entries = mutableListOf<Entry>()
            val iterator = directory.iterator()
            var count = 0
            while (count < 200) {
                checkBudget()
                if (!iterator.hasNext()) break
                val item = iterator.next(); count++
                if (item.fileName == "." || item.fileName == "..") continue
                entries += Entry(item.fileName, item.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L,
                    item.endOfFile, item.lastWriteTime.toEpochMillis())
            }
            folderPreviewCandidates(entries)
        }
    }
    // Some SMB2 servers use NO_SUCH_FILE rather than OBJECT_NAME_NOT_FOUND.
    private fun absentAware(block: () -> Boolean): Boolean = try { block() }
        catch (e: com.hierynomus.mssmb2.SMBApiException) {
            if (e.statusCode == 0xc000000fL) false else throw e
        }
    fun fileExists(share: DiskShare, path: String) = absentAware { share.fileExists(remotePath(path)) }
    fun folderExists(share: DiskShare, path: String) = absentAware { share.folderExists(remotePath(path)) }
    fun list(share: DiskShare, path: String): List<Entry> = share.list(remotePath(path)).filter { it.fileName != "." && it.fileName != ".." }.map {
        Entry(it.fileName, it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L, it.endOfFile, it.lastWriteTime.toEpochMillis())
    }.sortedWith(compareByDescending<Entry> { it.directory }.thenComparator { a, b -> naturalNames.compare(a.name, b.name) })

    fun read(share: DiskShare, path: String): File = share.openFile(remotePath(path), setOf(AccessMask.GENERIC_READ), null,
        setOf(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN, setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE))
    fun createTemp(share: DiskShare, path: String): File = share.openFile(remotePath(path),
        setOf(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ, AccessMask.DELETE), setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
        emptySet(), SMB2CreateDisposition.FILE_CREATE, setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE))
}

/** SMBJ's default factory has no TCP connect deadline. Bound every socket explicitly. */
class TimedSockets(private val base: SocketFactory = SocketFactory.getDefault(), private val timeout: Int = 3000) : SocketFactory() {
    override fun createSocket(): Socket = base.createSocket()
    private fun connect(host: InetAddress, port: Int, local: InetAddress? = null, localPort: Int = 0): Socket {
        val socket = base.createSocket()
        try { if (local != null) socket.bind(InetSocketAddress(local, localPort)); socket.connect(InetSocketAddress(host, port), timeout); return socket }
        catch (e: Throwable) { socket.close(); throw e }
    }
    override fun createSocket(host: String, port: Int) = connect(InetAddress.getByName(host), port)
    override fun createSocket(host: InetAddress, port: Int) = connect(host, port)
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = connect(InetAddress.getByName(host), port, local, localPort)
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = connect(host, port, local, localPort)
}
