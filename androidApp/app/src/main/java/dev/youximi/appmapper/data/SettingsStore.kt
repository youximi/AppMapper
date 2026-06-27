package dev.youximi.appmapper.data

import android.content.Context
import java.util.UUID

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("appmapper", Context.MODE_PRIVATE)

    fun getTarget(): PairingTarget =
        PairingTarget(
            host = prefs.getString("host", "") ?: "",
            port = prefs.getInt("port", 8765),
            code = prefs.getString("code", "") ?: "",
        )

    fun saveTarget(target: PairingTarget) {
        prefs.edit()
            .putString("host", target.host)
            .putInt("port", target.port)
            .putString("code", target.code)
            .apply()
    }

    fun getPollingMs(): Long = prefs.getLong("pollingMs", 1000L)

    fun savePollingMs(value: Long) {
        prefs.edit().putLong("pollingMs", value).apply()
    }

    fun getDeviceId(): String {
        val existing = prefs.getString("deviceId", null)
        if (existing != null) return existing
        val created = "android-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString("deviceId", created).apply()
        return created
    }
}
