package dev.youximi.appmapper.data

import dev.youximi.appmapper.BuildConfig
import org.json.JSONObject

const val ProtocolVersion = 2

sealed interface PairingRequest {
    val host: String
    val port: Int

    data class Temporary(
        override val host: String,
        override val port: Int,
        val code: String,
    ) : PairingRequest

    data class Remembered(
        override val host: String,
        override val port: Int,
        val serverId: String,
        val fingerprint: String,
        val enrollToken: String,
    ) : PairingRequest
}

data class ActiveApp(
    val appId: String,
    val packageName: String,
    val displayName: String,
    val iconPngBase64: String?,
)

fun pairHelloJson(deviceId: String, deviceName: String, request: PairingRequest,
                  publicKeySpki: String?): JSONObject {
    val hello = JSONObject()
        .put("type", "hello").put("protocolVersion", ProtocolVersion)
        .put("deviceId", deviceId).put("deviceName", deviceName)
        .put("androidVersion", android.os.Build.VERSION.SDK_INT)
        .put("appVersion", BuildConfig.VERSION_NAME)
    return when (request) {
        is PairingRequest.Temporary -> hello.put("mode", "pair_temporary")
            .put("pairingCode", request.code)
        is PairingRequest.Remembered -> hello.put("mode", "pair_remember")
            .put("enrollToken", request.enrollToken)
            .put("publicKeySpki", requireNotNull(publicKeySpki))
    }
}

fun resumeHelloJson(deviceId: String, deviceName: String): JSONObject =
    JSONObject().put("type", "hello").put("protocolVersion", ProtocolVersion)
        .put("mode", "resume").put("deviceId", deviceId).put("deviceName", deviceName)

fun proofJson(signature: String): JSONObject =
    JSONObject().put("type", "proof").put("signature", signature)

fun forgetJson(): JSONObject = JSONObject().put("type", "forget")

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
