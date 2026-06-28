package dev.youximi.appmapper.data

import dev.youximi.appmapper.BuildConfig
import org.json.JSONObject

const val ProtocolVersion = 1

data class PairingTarget(
    val host: String,
    val port: Int,
    val code: String,
)

data class ActiveApp(
    val appId: String,
    val packageName: String,
    val displayName: String,
    val iconPngBase64: String?,
)

fun helloJson(deviceId: String, deviceName: String, pairingCode: String): JSONObject =
    JSONObject()
        .put("type", "hello")
        .put("protocolVersion", ProtocolVersion)
        .put("pairingCode", pairingCode)
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("androidVersion", android.os.Build.VERSION.SDK_INT)
        .put("appVersion", BuildConfig.VERSION_NAME)
        .put("timestamp", System.currentTimeMillis())

fun activeAppJson(deviceId: String, sequence: Long, app: ActiveApp, screenOn: Boolean, locked: Boolean): JSONObject =
    JSONObject()
        .put("type", "active_app")
        .put("deviceId", deviceId)
        .put("sequence", sequence)
        .put(
            "app",
            JSONObject()
                .put("appId", app.appId)
                .put("packageName", app.packageName)
                .put("displayName", app.displayName)
                .put("iconPngBase64", app.iconPngBase64),
        )
        .put(
            "state",
            JSONObject()
                .put("screenOn", screenOn)
                .put("locked", locked),
        )
        .put("timestamp", System.currentTimeMillis())

fun idleJson(deviceId: String, sequence: Long, reason: String): JSONObject =
    JSONObject()
        .put("type", "idle")
        .put("deviceId", deviceId)
        .put("sequence", sequence)
        .put("reason", reason)
        .put("timestamp", System.currentTimeMillis())

fun heartbeatJson(deviceId: String): JSONObject =
    JSONObject()
        .put("type", "heartbeat")
        .put("deviceId", deviceId)
        .put("timestamp", System.currentTimeMillis())
