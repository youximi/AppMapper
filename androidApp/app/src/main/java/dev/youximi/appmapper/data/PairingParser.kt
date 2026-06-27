package dev.youximi.appmapper.data

import android.net.Uri

object PairingParser {
    fun parseUri(value: String): PairingTarget? {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "appmapper" || uri.host != "connect") return null

        val host = uri.getQueryParameter("host")?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
        val code = uri.getQueryParameter("code")?.takeIf { it.all(Char::isDigit) } ?: return null
        return PairingTarget(host = host, port = port, code = code)
    }
}
