package com.ezsmb.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ezsmb.app.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

data class Lan(val network: Network, val key: String, val addresses: List<String>)
class Discovery(private val context: Context, private val engine: SmbEngine) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    fun lan(): Lan? = cm.allNetworks.firstNotNullOfOrNull { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
        if (!isPhysicalLan(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))) return@firstNotNullOfOrNull null
        val properties = cm.getLinkProperties(network) ?: return@firstNotNullOfOrNull null
        val addr = properties.linkAddresses.firstOrNull { it.address is Inet4Address && it.address.isSiteLocalAddress } ?: return@firstNotNullOfOrNull null
        val ip = addr.address.hostAddress ?: return@firstNotNullOfOrNull null
        // Network context is an additional boundary; server GUID is always checked before credentials.
        val key = "${properties.interfaceName}|${ip.substringBeforeLast('.')}|${addr.prefixLength}|${properties.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress}"
        Lan(network, key, scanCandidates(ip, addr.prefixLength))
    }
    @Suppress("DEPRECATION")
    fun scan(history: List<Device>): Flow<Device> = channelFlow {
        val lan = lan() ?: throw IllegalStateException("没有可扫描的 Wi-Fi 或以太网，请连接局域网，也可以手动连接")
        val permits = Semaphore(24)
        val seen = ConcurrentHashMap.newKeySet<String>()
        val scope = this
        fun probe(host: String, port: Int, name: String = host) {
            if (!seen.add("$host:$port")) return
            launch(Dispatchers.IO) {
                permits.withPermit {
                    try {
                        lan.network.socketFactory.createSocket().use { it.connect(InetSocketAddress(host, port), 500) }
                        ensureActive()
                        engine.open(Target(host, port), TimedSockets(lan.network.socketFactory, 700), 2).use { link ->
                            ensureActive()
                            val previous = history.firstOrNull { it.guid == link.guid && it.network == lan.key }
                            val device = Device(host, port, if (name != host) name else previous?.name ?: host, link.guid, lan.key, online = true, saved = previous != null)
                            send(device)
                            if (name == host) (link.probeName() ?: computerName(lan.network, host))?.let { resolved -> ensureActive(); send(device.copy(name = resolved)) }
                        }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { /* A failed probe is not an online SMB server. */ }
                }
            }
        }
        history.filter { it.network == lan.key }.forEach { probe(it.host, it.port) }
        val nsd = context.getSystemService(NsdManager::class.java)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!scope.isActive) return
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val addr = info.host ?: return
                        if (scope.isActive && addr.isSiteLocalAddress) probe(addr.hostAddress ?: return, info.port, info.serviceName)
                    }
                })
            }
        }
        var started = false
        try {
            runCatching { nsd.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, listener); started = true }
            lan.addresses.forEach { probe(it, 445) }
            delay(12000)
        } finally {
            if (started) runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }.flowOn(Dispatchers.IO)
}
