package dev.youximi.appmapper.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.EOFException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class PairingRejected(val reason: String) : Exception(reason)

class TcpJsonClient {
    private var socket: SSLSocket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    suspend fun pair(request: PairingRequest, deviceId: String, name: String,
                     publicKeySpki: String?): JSONObject = withContext(Dispatchers.IO) {
        val fingerprint = (request as? PairingRequest.Remembered)?.fingerprint
        open(request.host, request.port, fingerprint)
        send(pairHelloJson(deviceId, name, request, publicKeySpki))
        val ack = readRequired()
        checkAck(ack)
        if (request is PairingRequest.Remembered && ack.optString("serverId") != request.serverId)
            throw PairingRejected("computer_identity_changed")
        ack
    }

    suspend fun resume(computer: PairedComputer, name: String): JSONObject = withContext(Dispatchers.IO) {
        open(computer.host, computer.port, computer.fingerprint)
        send(resumeHelloJson(computer.deviceId, name))
        val challenge = readRequired()
        if (challenge.optString("type") == "error")
            throw PairingRejected(challenge.optString("code"))
        if (challenge.optString("type") != "challenge") throw PairingRejected("invalid_challenge")
        val nonce = challenge.getString("nonce")
        val payload = "appmapper-v2|${computer.serverId}|${computer.deviceId}|$nonce".toByteArray(Charsets.UTF_8)
        send(proofJson(DeviceKeyStore.sign(computer.keyAlias, payload)))
        val ack = readRequired()
        checkAck(ack)
        if (ack.optString("serverId") != computer.serverId) throw PairingRejected("computer_identity_changed")
        ack
    }

    private fun open(host: String, port: Int, fingerprint: String?) {
        close()
        val expected = fingerprint?.lowercase()
        var identityMismatch = false
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                throw CertificateException("Client certificates are not accepted")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val actual = MessageDigest.getInstance("SHA-256")
                    .digest(chain.firstOrNull()?.publicKey?.encoded ?: throw CertificateException("No certificate"))
                    .joinToString("") { "%02x".format(it) }
                if (expected != null && actual != expected) {
                    identityMismatch = true
                    throw CertificateException("Computer identity does not match")
                }
            }
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trust), SecureRandom())
        val next = context.socketFactory.createSocket() as SSLSocket
        try {
            next.connect(InetSocketAddress(host, port), 5000)
            next.soTimeout = 5000
            next.startHandshake()
            socket = next
            writer = BufferedWriter(OutputStreamWriter(next.outputStream, Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(next.inputStream, Charsets.UTF_8))
        } catch (failure: Exception) {
            next.close()
            if (identityMismatch && failure is SSLHandshakeException)
                throw PairingRejected("computer_identity_changed")
            throw failure
        }
    }

    private fun checkAck(ack: JSONObject) {
        if (ack.optString("type") == "error") throw PairingRejected(ack.optString("code"))
        if (ack.optString("type") != "hello_ack" || !ack.optBoolean("accepted"))
            throw PairingRejected("invalid_ack")
    }

    private fun readRequired(): JSONObject = JSONObject(reader?.readLine() ?: throw EOFException("Computer disconnected"))

    suspend fun send(json: JSONObject) = withContext(Dispatchers.IO) {
        val out = writer ?: error("TCP client is not connected")
        out.write(json.toString())
        out.write("\n")
        out.flush()
    }

    suspend fun poll(): JSONObject? = withContext(Dispatchers.IO) {
        val input = reader ?: return@withContext null
        socket?.soTimeout = 100
        try {
            JSONObject(input.readLine() ?: throw EOFException("Computer disconnected"))
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    fun close() {
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }
}
