package dev.youximi.appmapper.data

import android.content.Context
import java.util.UUID

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("appmapper", Context.MODE_PRIVATE)

    init {
        // Version 1 saved a rotating pairing code here; it is never a durable credential.
        if (listOf("code", "host", "port").any(prefs::contains))
            prefs.edit().remove("code").remove("host").remove("port").commit()
    }

    fun getPollingMs(): Long = prefs.getLong("pollingMs", 1000L)

    fun savePollingMs(value: Long) {
        prefs.edit().putLong("pollingMs", value).apply()
    }

    fun getDynamicColorEnabled(): Boolean = prefs.getBoolean("dynamicColorEnabled", true)

    fun saveDynamicColorEnabled(value: Boolean) {
        prefs.edit().putBoolean("dynamicColorEnabled", value).apply()
    }

    fun getThemeColor(): String? = prefs.getString("themeColor", null)

    fun saveThemeColor(value: String) {
        prefs.edit().putString("themeColor", value).apply()
    }

    fun getDeviceId(): String {
        val existing = prefs.getString("deviceId", null)
        if (existing != null) return existing
        val created = "android-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString("deviceId", created).apply()
        return created
    }
}
