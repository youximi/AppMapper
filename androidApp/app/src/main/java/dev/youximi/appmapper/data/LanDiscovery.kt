package dev.youximi.appmapper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections

data class DiscoveredComputer(val host: String, val port: Int)

/** Finds a known server ID on the local IPv4 network; TLS pinning authenticates the result. */
object LanDiscovery {
    private const val port = 8766

    suspend fun find(serverId: String): DiscoveredComputer? = withContext(Dispatchers.IO) {
        if (serverId.isBlank()) return@withContext null
        val request = "appmapper-discover-v2|$serverId".toByteArray(Charsets.US_ASCII)
        val expected = "appmapper-discovery-v2|$serverId|"
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            val broadcasts = mutableSetOf(InetAddress.getByName("255.255.255.255"))
            runCatching {
                Collections.list(NetworkInterface.getNetworkInterfaces())
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.interfaceAddresses }
                    .mapNotNullTo(broadcasts) { it.broadcast }
            }
            broadcasts.forEach { address ->
                runCatching { socket.send(DatagramPacket(request, request.size, address, port)) }
            }
            val deadline = System.currentTimeMillis() + 1600
            while (System.currentTimeMillis() < deadline) {
                val bytes = ByteArray(256)
                val reply = DatagramPacket(bytes, bytes.size)
                try { socket.receive(reply) } catch (_: SocketTimeoutException) { continue }
                val message = String(reply.data, reply.offset, reply.length, Charsets.US_ASCII)
                if (message.startsWith(expected)) {
                    val tcpPort = message.removePrefix(expected).toIntOrNull()
                    if (tcpPort != null && tcpPort in 1..65535)
                        return@withContext DiscoveredComputer(reply.address.hostAddress ?: continue, tcpPort)
                }
            }
            null
        }
    }
}
