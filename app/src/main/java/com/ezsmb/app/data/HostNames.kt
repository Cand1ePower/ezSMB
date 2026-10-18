package com.ezsmb.app.data

import android.net.Network
import com.ezsmb.app.core.NetbiosName
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

fun computerName(network: Network, host: String): String? = runCatching {
    DatagramSocket().use { socket ->
        network.bindSocket(socket)
        socket.connect(InetAddress.getByName(host), 137)
        socket.soTimeout = 800
        val id = Random.nextInt(65536)
        val query = NetbiosName.query(id)
        socket.send(DatagramPacket(query, query.size))
        val response = DatagramPacket(ByteArray(4096), 4096)
        socket.receive(response)
        NetbiosName.parse(response.data.copyOf(response.length), id)
    }
}.getOrNull()
