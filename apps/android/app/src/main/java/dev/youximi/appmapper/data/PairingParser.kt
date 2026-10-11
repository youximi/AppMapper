package dev.youximi.appmapper.data

import android.net.Uri

object PairingParser {
    fun parseUri(value: String): PairingRequest.Remembered? {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "appmapper" || uri.host != "connect") return null

        val host = uri.getQueryParameter("host")?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val serverId = uri.getQueryParameter("serverId")?.takeIf { it.isNotBlank() } ?: return null
        val fingerprint = uri.getQueryParameter("fingerprint")?.lowercase()
            ?.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
            ?: return null
        val enrollToken = uri.getQueryParameter("enrollToken")?.lowercase()
            ?.takeIf { it.length == 32 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
            ?: return null
        return PairingRequest.Remembered(host, port, serverId, fingerprint, enrollToken)
    }
}
