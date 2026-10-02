package dev.youximi.appmapper.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.youximi.appmapper.R
import dev.youximi.appmapper.hasLocalNetworkAccess
import dev.youximi.appmapper.data.ActiveApp
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.CurrentAppResult
import dev.youximi.appmapper.data.DeviceKeyStore
import dev.youximi.appmapper.data.PairedComputer
import dev.youximi.appmapper.data.PairingRejected
import dev.youximi.appmapper.data.PairingRequest
import dev.youximi.appmapper.data.PairingStore
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.TcpJsonClient
import dev.youximi.appmapper.data.UsageAppReader
import dev.youximi.appmapper.data.activeAppJson
import dev.youximi.appmapper.data.forgetJson
import dev.youximi.appmapper.data.heartbeatJson
import dev.youximi.appmapper.data.idleJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

object SyncStatus {
    const val LocalNetworkPermissionRequired = "未获局域网访问权限，请允许后连接"
    val text = MutableStateFlow("未连接")
    val isRunning = MutableStateFlow(false)
}

class ForegroundSyncService : Service() {
    private enum class SessionKind { Temporary, Remembered }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = TcpJsonClient()
    private var syncJob: Job? = null
    @Volatile private var pendingRequest: PairingRequest? = null
    @Volatile private var activeSession: SessionKind? = null
    @Volatile private var waitingForUser = false
    private var sequence = 0L
    private var lastAppId: String? = null
    private var lastIdleReason: String? = null
    private var missingUsageAccessLogged = false
    private var unknownCurrentAppLogged = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NotificationId,
            NotificationCompat.Builder(this, ChannelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("AppMapper")
                .setContentText("正在同步当前前台 App")
                .setOngoing(true)
                .build(),
        )
        SyncStatus.isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionForget) {
            val running = syncJob
            running?.cancel()
            scope.launch {
                running?.join()
                val store = PairingStore(this@ForegroundSyncService)
                try {
                    val saved = store.read()
                    if (activeSession == SessionKind.Remembered && client.isConnected &&
                        hasLocalNetworkAccess(this@ForegroundSyncService))
                        runCatching { client.send(forgetJson()) }
                    client.close()
                    activeSession = null
                    store.clear()
                    saved?.let { deleteKey(it.keyAlias) }
                    pendingRequest = null
                    waitingForUser = true
                    SyncStatus.text.value = "已忘记电脑，需要重新扫码"
                } catch (failure: Exception) {
                    waitingForUser = true
                    SyncStatus.text.value = "移除失败，请重试"
                    AppLogger.write(this@ForegroundSyncService, "Forget failed: ${failure.javaClass.simpleName}.")
                } finally {
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }
        if (!hasLocalNetworkAccess(this)) {
            stopForMissingLocalNetworkPermission()
            return START_NOT_STICKY
        }
        if (intent?.action == ActionPair) {
            val host = intent.getStringExtra("host") ?: ""
            val port = intent.getIntExtra("port", 8765)
            pendingRequest = when (intent.getStringExtra("mode")) {
                "temporary" -> PairingRequest.Temporary(host, port, intent.getStringExtra("code") ?: "")
                "remembered" -> PairingRequest.Remembered(host, port,
                    intent.getStringExtra("serverId") ?: "", intent.getStringExtra("fingerprint") ?: "",
                    intent.getStringExtra("enrollToken") ?: "")
                else -> null
            }
            waitingForUser = false
            activeSession = null
            client.close()
        } else if (intent?.action == ActionStart) {
            pendingRequest = null
            waitingForUser = false
            activeSession = null
            client.close()
        }
        if (syncJob?.isActive != true) syncJob = scope.launch { runSyncLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        SyncStatus.isRunning.value = false
        syncJob?.cancel()
        client.close()
        activeSession = null
        if (!waitingForUser) SyncStatus.text.value = "已停止"
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForMissingLocalNetworkPermission() {
        waitingForUser = true
        pendingRequest = null
        client.close()
        activeSession = null
        syncJob?.cancel()
        SyncStatus.isRunning.value = false
        SyncStatus.text.value = SyncStatus.LocalNetworkPermissionRequired
        AppLogger.write(this, "Local network permission is missing; sync stopped.")
        stopSelf()
    }

    private suspend fun runSyncLoop() {
        val settings = SettingsStore(this)
        val store = PairingStore(this)
        val reader = UsageAppReader(this)
        val deviceId = settings.getDeviceId()
        var lastHeartbeat = 0L
        var lastResponse = 0L
        var retryMs = 1000L

        while (currentCoroutineContext().isActive) {
            if (!hasLocalNetworkAccess(this)) {
                stopForMissingLocalNetworkPermission()
                return
            }
            if (waitingForUser && pendingRequest == null) {
                delay(1000)
                continue
            }
            var resumingSaved = false
            try {
                if (!client.isConnected) {
                    val request = pendingRequest
                    val saved = if (request != null) runCatching { store.read() }.getOrNull()
                        else try { store.read() }
                        catch (_: Exception) { throw PairingRejected("paired_data_unavailable") }
                    if (request == null && saved == null) {
                        SyncStatus.text.value = "等待连接"
                        delay(1000)
                        continue
                    }
                    SyncStatus.text.value = "正在连接电脑"
                    if (request != null) {
                        val name = android.os.Build.MODEL ?: "Android"
                        var keyAlias: String? = null
                        try {
                            val key = if (request is PairingRequest.Remembered) DeviceKeyStore.create() else null
                            keyAlias = key?.first
                            val ack = client.pair(request, deviceId, name, key?.second)
                            if (request is PairingRequest.Remembered) {
                                val computer = PairedComputer(request.host, request.port,
                                    ack.getString("serverId"), request.fingerprint, deviceId,
                                    keyAlias!!, ack.optString("serverName", "AppMapper"))
                                try { store.save(computer) }
                                catch (_: Exception) {
                                    if (hasLocalNetworkAccess(this)) runCatching { client.send(forgetJson()) }
                                    throw PairingRejected("device_storage_failed")
                                }
                                keyAlias = null
                                saved?.let { if (it.keyAlias != computer.keyAlias) deleteKey(it.keyAlias) }
                            }
                            pendingRequest = null
                            activeSession = if (request is PairingRequest.Remembered)
                                SessionKind.Remembered else SessionKind.Temporary
                        } finally {
                            if (keyAlias != null) deleteKey(keyAlias)
                        }
                    } else {
                        resumingSaved = true
                        val computer = saved!!
                        val name = android.os.Build.MODEL ?: "Android"
                        client.resume(computer, name)
                        activeSession = SessionKind.Remembered
                    }
                    SyncStatus.text.value = if (activeSession == SessionKind.Temporary) "临时连接中" else "已连接"
                    AppLogger.write(this, if (activeSession == SessionKind.Temporary)
                        "Temporary connection established." else "Authenticated connection established.")
                    lastAppId = null
                    lastIdleReason = null
                    unknownCurrentAppLogged = false
                    lastHeartbeat = 0L
                    lastResponse = System.currentTimeMillis()
                    retryMs = 1000L
                }

                if (!hasLocalNetworkAccess(this)) {
                    stopForMissingLocalNetworkPermission()
                    return
                }
                val idleReason = currentIdleReason()
                val hasUsageAccess = reader.hasUsageAccess()
                if (!hasUsageAccess && idleReason == null && !missingUsageAccessLogged) {
                    AppLogger.write(this, "Usage access permission is missing.")
                    missingUsageAccessLogged = true
                } else if (hasUsageAccess) {
                    missingUsageAccessLogged = false
                }
                if (idleReason != null) {
                    sendIdleIfChanged(deviceId, idleReason)
                } else if (!hasUsageAccess) {
                    sendUnknownIfNoActiveWindow(deviceId, "usage_access_missing")
                } else {
                    when (val result = reader.readCurrentApp()) {
                        is CurrentAppResult.Active -> {
                            unknownCurrentAppLogged = false
                            sendActiveIfNeeded(deviceId, result.app)
                        }
                        CurrentAppResult.Launcher -> {
                            unknownCurrentAppLogged = false
                            sendIdleIfChanged(deviceId, "launcher")
                        }
                        CurrentAppResult.Unknown -> sendUnknownIfNoActiveWindow(deviceId, "usage_events_empty")
                    }
                }
                val now = System.currentTimeMillis()
                if (now - lastHeartbeat >= 5000) {
                    client.send(heartbeatJson(deviceId))
                    lastHeartbeat = now
                }
                client.poll()?.let { message ->
                    if (message.optString("type") == "heartbeat_ack") lastResponse = System.currentTimeMillis()
                    if (message.optString("type") == "error")
                        throw PairingRejected(message.optString("code"))
                }
                if (now - lastResponse > 15000) throw IOException("Heartbeat timed out")
                delay(settings.getPollingMs())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (rejected: PairingRejected) {
                if (!hasLocalNetworkAccess(this)) {
                    stopForMissingLocalNetworkPermission()
                    return
                }
                val wasTemporary = activeSession == SessionKind.Temporary ||
                    pendingRequest is PairingRequest.Temporary
                client.close()
                val wasRemembered = activeSession == SessionKind.Remembered || resumingSaved
                activeSession = null
                if (rejected.reason == "computer_identity_changed" && resumingSaved) {
                    SyncStatus.text.value = "电脑身份不匹配，正在重试"
                    delay(retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(30000L)
                    continue
                }
                pendingRequest = null
                waitingForUser = true
                if (wasRemembered && (rejected.reason == "pairing_required" ||
                        rejected.reason == "authentication_failed")) {
                    try {
                        val old = store.read()
                        store.clear()
                        old?.let { deleteKey(it.keyAlias) }
                        SyncStatus.text.value = "电脑已移除配对，请重新扫码"
                    } catch (_: Exception) {
                        SyncStatus.text.value = "电脑已拒绝配对，本地记录无法移除"
                    }
                } else {
                    SyncStatus.text.value = when (rejected.reason) {
                        "invalid_pairing_code" -> "验证码已失效，请重新输入"
                        "invalid_enroll_token" -> "二维码已失效，请重新扫码"
                        "computer_identity_changed" -> "电脑身份不匹配，请重新扫码"
                        "device_storage_failed", "paired_data_unavailable" -> "本机配对记录无法保存或读取，请检查应用存储"
                        "max_devices_reached" -> "电脑连接数已满，请先断开其他设备"
                        else -> if (wasTemporary) "临时连接失败，请重新输入验证码（${rejected.reason}）"
                            else "配对失败，请重新扫码（${rejected.reason}）"
                    }
                }
                AppLogger.write(this, "Pairing rejected: ${rejected.reason}.")
            } catch (failure: Exception) {
                if (!hasLocalNetworkAccess(this)) {
                    stopForMissingLocalNetworkPermission()
                    return
                }
                val wasTemporary = activeSession == SessionKind.Temporary
                client.close()
                activeSession = null
                lastAppId = null
                if (wasTemporary) {
                    waitingForUser = true
                    SyncStatus.text.value = "临时连接已结束，需要重新输入验证码"
                    AppLogger.write(this, "Temporary connection ended: ${failure.javaClass.simpleName}.")
                    continue
                }
                SyncStatus.text.value = "连接中断，正在重试"
                AppLogger.write(this, "Connection interrupted: ${failure.javaClass.simpleName}.")
                delay(retryMs)
                retryMs = (retryMs * 2).coerceAtMost(30000L)
            }
        }
    }

    private suspend fun sendActiveIfNeeded(deviceId: String, app: ActiveApp) {
        if (lastAppId == app.appId) return
        sequence += 1
        AppLogger.write(this, "Sending active_app seq=$sequence app=${app.displayName} package=${app.packageName} icon=${!app.iconPngBase64.isNullOrBlank()}.")
        client.send(
            activeAppJson(deviceId, sequence, app,
                screenOn = currentIdleReason() != "screen_off",
                locked = currentIdleReason() == "locked"),
        )
        lastAppId = app.appId
        lastIdleReason = null
    }

    private suspend fun sendIdleIfChanged(deviceId: String, reason: String) {
        if (lastAppId == null && lastIdleReason == reason) return
        sequence += 1
        AppLogger.write(this, "Sending idle seq=$sequence reason=$reason.")
        client.send(idleJson(deviceId, sequence, reason))
        lastAppId = null
        lastIdleReason = reason
        unknownCurrentAppLogged = false
    }

    private suspend fun sendUnknownIfNoActiveWindow(deviceId: String, detail: String) {
        if (lastAppId != null) {
            if (!unknownCurrentAppLogged) {
                AppLogger.write(this, "Current app is unknown ($detail); keeping active mapper for $lastAppId.")
                unknownCurrentAppLogged = true
            }
            return
        }
        sendIdleIfChanged(deviceId, "unknown")
    }

    private fun currentIdleReason(): String? {
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isInteractive) return "screen_off"
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager.isKeyguardLocked) return "locked"
        return null
    }

    private fun deleteKey(alias: String) {
        try { DeviceKeyStore.delete(alias) }
        catch (failure: Exception) {
            AppLogger.write(this, "Key cleanup failed: ${failure.javaClass.simpleName}.")
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ChannelId,
            getString(R.string.foreground_service_channel), NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        private const val ChannelId = "appmapper_sync"
        private const val NotificationId = 1001
        private const val ActionPair = "dev.youximi.appmapper.PAIR"
        private const val ActionForget = "dev.youximi.appmapper.FORGET"
        private const val ActionStart = "dev.youximi.appmapper.START"

        fun pair(context: Context, request: PairingRequest) {
            if (!canStart(context)) return
            context.startForegroundService(Intent(context, ForegroundSyncService::class.java).apply {
                action = ActionPair
                putExtra("host", request.host)
                putExtra("port", request.port)
                when (request) {
                    is PairingRequest.Temporary -> {
                        putExtra("mode", "temporary")
                        putExtra("code", request.code)
                    }
                    is PairingRequest.Remembered -> {
                        putExtra("mode", "remembered")
                        putExtra("serverId", request.serverId)
                        putExtra("fingerprint", request.fingerprint)
                        putExtra("enrollToken", request.enrollToken)
                    }
                }
            })
        }

        fun start(context: Context) {
            if (!canStart(context)) return
            context.startForegroundService(Intent(context, ForegroundSyncService::class.java).apply {
                action = ActionStart
            })
        }

        fun forget(context: Context) {
            context.startService(Intent(context, ForegroundSyncService::class.java).apply { action = ActionForget })
        }

        private fun canStart(context: Context): Boolean {
            if (hasLocalNetworkAccess(context)) return true
            SyncStatus.text.value = SyncStatus.LocalNetworkPermissionRequired
            return false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundSyncService::class.java))
            SyncStatus.text.value = "已停止"
        }
    }
}
