package dev.youximi.appmapper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class TcpJsonClient {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    suspend fun connect(target: PairingTarget, hello: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        close()
        val nextSocket = Socket()
        nextSocket.connect(InetSocketAddress(target.host, target.port), 5000)
        nextSocket.soTimeout = 5000
        socket = nextSocket
        writer = BufferedWriter(OutputStreamWriter(nextSocket.getOutputStream(), Charsets.UTF_8))
        reader = BufferedReader(InputStreamReader(nextSocket.getInputStream(), Charsets.UTF_8))
        send(hello)
        reader?.readLine()?.let { JSONObject(it) }
    }

    suspend fun send(json: JSONObject) = withContext(Dispatchers.IO) {
        val out = writer ?: error("TCP client is not connected")
        out.write(json.toString())
        out.write("\n")
        out.flush()
    }

    fun close() {
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }
}
