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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class ConnectionPhase { Idle, Connecting, Connected, Reconnecting, Failed }

data class ConnectionState(
    val serverId: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val isTemporary: Boolean = false,
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val message: String = "未连接",
) {
    val isConnecting: Boolean
        get() = phase == ConnectionPhase.Connecting || phase == ConnectionPhase.Reconnecting
}

object SyncStatus {
    const val LocalNetworkPermissionRequired = "未获局域网访问权限，请允许后连接"
    val connection = MutableStateFlow(ConnectionState())
    val isRunning = MutableStateFlow(false)
    val operationError = MutableStateFlow<String?>(null)
}

class ForegroundSyncService : Service() {
    private enum class SessionKind { Temporary, Remembered }

    private sealed interface Command {
        data class Start(val startId: Int, val request: PairingRequest?, val serverId: String?) : Command
        data class Forget(val startId: Int, val serverId: String) : Command
        data class Finished(val generation: Long) : Command
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val client = TcpJsonClient()
    private var syncJob: Job? = null
    private var activeSession: SessionKind? = null
    private var targetServerId: String? = null
    private var generation = 0L
    private var currentStartId = 0
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
        scope.launch {
            // Serialize commands; only the current session may stop this service.
            for (command in commands) {
                when (command) {
                    is Command.Start -> {
                        currentStartId = command.startId
                        try {
                            startSession(command)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (failure: Exception) {
                            failConnection("无法读取或保存配对记录，请检查应用存储后重试")
                            AppLogger.write(this@ForegroundSyncService, "Start failed: ${failure.javaClass.simpleName}.")
                            commands.trySend(Command.Finished(generation))
                        }
                    }
                    is Command.Forget -> {
                        currentStartId = command.startId
                        forgetComputer(command)
                    }
                    is Command.Finished -> if (command.generation == generation) {
                        SyncStatus.isRunning.value = false
                        stopSelfResult(currentStartId)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionForget) {
            intent.getStringExtra("serverId")?.let { commands.trySend(Command.Forget(startId, it)) }
        } else {
            val request = if (intent?.action == ActionPair) {
                val host = intent.getStringExtra("host") ?: ""
                val port = intent.getIntExtra("port", 8765)
                when (intent.getStringExtra("mode")) {
                    "temporary" -> PairingRequest.Temporary(host, port, intent.getStringExtra("code") ?: "")
                    "remembered" -> PairingRequest.Remembered(host, port,
                        intent.getStringExtra("serverId") ?: "", intent.getStringExtra("fingerprint") ?: "",
                        intent.getStringExtra("enrollToken") ?: "")
                    else -> null
                }
            } else null
            commands.trySend(Command.Start(startId, request,
                if (intent?.action == ActionStart) intent.getStringExtra("serverId") else null))
        }
        return START_STICKY
    }

    private suspend fun startSession(command: Command.Start) {
        generation += 1
        client.close()
        syncJob?.cancelAndJoin()
        activeSession = null
        targetServerId = null
        SyncStatus.operationError.value = null
        SyncStatus.isRunning.value = true
        val request = command.request
        SyncStatus.connection.value = ConnectionState(
            serverId = (request as? PairingRequest.Remembered)?.serverId ?: command.serverId,
            host = request?.host, port = request?.port, isTemporary = request is PairingRequest.Temporary,
            phase = ConnectionPhase.Connecting, message = "正在连接电脑",
        )
        if (!hasLocalNetworkAccess(this)) {
            stopForMissingLocalNetworkPermission()
            commands.trySend(Command.Finished(generation))
            return
        }
        val store = PairingStore(this)
        // Read even for enrollment so a damaged existing list cannot be overwritten.
        val state = store.read()
        val saved = if (request != null) null else if (command.serverId != null)
            store.select(command.serverId) else state.selectedComputer
        if (request == null && saved == null) {
            failConnection("请先在设备管理中选择电脑，或扫码添加电脑")
            commands.trySend(Command.Finished(generation))
            return
        }
        targetServerId = (request as? PairingRequest.Remembered)?.serverId ?: saved?.serverId
        SyncStatus.connection.value = SyncStatus.connection.value.copy(
            serverId = targetServerId, host = request?.host ?: saved?.host, port = request?.port ?: saved?.port,
        )
        val sessionGeneration = generation
        syncJob = scope.launch {
            try { runSyncLoop(request, saved) }
            finally { commands.trySend(Command.Finished(sessionGeneration)) }
        }
    }

    private suspend fun forgetComputer(command: Command.Forget) {
        SyncStatus.operationError.value = null
        val isTarget = command.serverId == targetServerId
        try {
            if (isTarget) {
                generation += 1
                syncJob?.cancelAndJoin()
                syncJob = null
                if (activeSession == SessionKind.Remembered && client.isConnected && hasLocalNetworkAccess(this))
                    runCatching { client.send(forgetJson()) }
                client.close()
                activeSession = null
            }
            PairingStore(this).remove(command.serverId)?.let { deleteKey(it.keyAlias) }
            if (isTarget) {
                targetServerId = null
                SyncStatus.connection.value = ConnectionState(message = "已忘记电脑，请选择其他电脑")
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            SyncStatus.operationError.value = "忘记电脑失败，请检查应用存储后重试"
            AppLogger.write(this, "Forget failed: ${failure.javaClass.simpleName}.")
            if (isTarget) {
                failConnection("忘记电脑失败，同步已停止，请重试")
            }
        } finally {
            // A newer forget command can supersede stopSelfResult while no session remains.
            if (syncJob?.isActive != true) {
                SyncStatus.isRunning.value = false
                stopSelfResult(command.startId)
            }
        }
    }

    override fun onDestroy() {
        SyncStatus.isRunning.value = false
        scope.cancel()
        client.close()
        activeSession = null
        if (SyncStatus.connection.value.phase in listOf(ConnectionPhase.Connecting,
                ConnectionPhase.Connected, ConnectionPhase.Reconnecting))
            SyncStatus.connection.value = SyncStatus.connection.value.copy(phase = ConnectionPhase.Idle, message = "已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForMissingLocalNetworkPermission() {
        client.close()
        activeSession = null
        failConnection(SyncStatus.LocalNetworkPermissionRequired)
        AppLogger.write(this, "Local network permission is missing; sync stopped.")
    }

    private fun failConnection(message: String) {
        SyncStatus.connection.value = SyncStatus.connection.value.copy(phase = ConnectionPhase.Failed, message = message)
    }

    private suspend fun runSyncLoop(initialRequest: PairingRequest?, initialComputer: PairedComputer?) {
        val settings = SettingsStore(this)
        val store = PairingStore(this)
        val reader = UsageAppReader(this)
        val deviceId = settings.getDeviceId()
        var lastHeartbeat = 0L
        var lastResponse = 0L
        var retryMs = 1000L
        var request = initialRequest
        var saved = initialComputer
        var hasConnected = false

        while (currentCoroutineContext().isActive) {
            if (!hasLocalNetworkAccess(this)) {
                stopForMissingLocalNetworkPermission()
                return
            }
            var resumingSaved = false
            try {
                if (!client.isConnected) {
                    val pairingRequest = request
                    SyncStatus.connection.value = SyncStatus.connection.value.copy(
                        phase = if (hasConnected) ConnectionPhase.Reconnecting else ConnectionPhase.Connecting,
                        message = if (hasConnected) "连接中断，正在重试" else "正在连接电脑",
                    )
                    if (pairingRequest != null) {
                        val name = android.os.Build.MODEL ?: "Android"
                        var keyAlias: String? = null
                        try {
                            val key = if (pairingRequest is PairingRequest.Remembered) DeviceKeyStore.create() else null
                            keyAlias = key?.first
                            val ack = client.pair(pairingRequest, deviceId, name, key?.second)
                            if (pairingRequest is PairingRequest.Remembered) {
                                val computer = PairedComputer(pairingRequest.host, pairingRequest.port,
                                    ack.getString("serverId"), pairingRequest.fingerprint, deviceId,
                                    keyAlias!!, ack.optString("serverName", "AppMapper"), System.currentTimeMillis())
                                val previous = try { store.save(computer) }
                                catch (_: Exception) {
                                    if (hasLocalNetworkAccess(this)) runCatching { client.send(forgetJson()) }
                                    throw PairingRejected("device_storage_failed")
                                }
                                keyAlias = null
                                previous?.let { if (it.keyAlias != computer.keyAlias) deleteKey(it.keyAlias) }
                                saved = computer
                            }
                            request = null
                            activeSession = if (pairingRequest is PairingRequest.Remembered)
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
                    hasConnected = true
                    SyncStatus.connection.value = SyncStatus.connection.value.copy(
                        phase = ConnectionPhase.Connected,
                        message = if (activeSession == SessionKind.Temporary) "临时连接中" else "已连接",
                    )
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
                currentCoroutineContext().ensureActive()
                if (!hasLocalNetworkAccess(this)) {
                    stopForMissingLocalNetworkPermission()
                    return
                }
                val wasTemporary = activeSession == SessionKind.Temporary ||
                    request is PairingRequest.Temporary
                client.close()
                val wasRemembered = activeSession == SessionKind.Remembered || resumingSaved
                activeSession = null
                if (rejected.reason == "computer_identity_changed" && resumingSaved && hasConnected) {
                    SyncStatus.connection.value = SyncStatus.connection.value.copy(
                        phase = ConnectionPhase.Reconnecting, message = "电脑身份不匹配，正在重试",
                    )
                    delay(retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(30000L)
                    continue
                }
                request = null
                val message = if (wasRemembered && (rejected.reason == "pairing_required" ||
                        rejected.reason == "authentication_failed")) {
                    try {
                        saved?.let { computer -> store.remove(computer.serverId)?.let { deleteKey(it.keyAlias) } }
                        "电脑已移除配对，请重新扫码"
                    } catch (_: Exception) {
                        "电脑已拒绝配对，本地记录无法移除"
                    }
                } else {
                    when (rejected.reason) {
                        "invalid_pairing_code" -> "验证码已失效，请重新输入"
                        "invalid_enroll_token" -> "二维码已失效，请重新扫码"
                        "computer_identity_changed" -> "电脑身份不匹配，请重新扫码"
                        "device_storage_failed", "paired_data_unavailable" -> "本机配对记录无法保存或读取，请检查应用存储"
                        "max_devices_reached" -> "电脑连接数已满，请先断开其他设备"
                        else -> if (wasTemporary) "电脑拒绝临时连接，请重新输入验证码"
                            else "电脑拒绝连接，请重新扫码或查看连接日志"
                    }
                }
                AppLogger.write(this, "Pairing rejected: ${rejected.reason}.")
                failConnection(message)
                return
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!hasLocalNetworkAccess(this)) {
                    stopForMissingLocalNetworkPermission()
                    return
                }
                val wasTemporary = activeSession == SessionKind.Temporary || request is PairingRequest.Temporary
                client.close()
                activeSession = null
                lastAppId = null
                if (wasTemporary) {
                    failConnection(if (hasConnected) "临时连接已结束，需要重新输入验证码" else connectionFailureMessage(failure))
                    AppLogger.write(this, "Temporary connection ended: ${failure.javaClass.simpleName}.")
                    return
                }
                if (!hasConnected) {
                    failConnection(connectionFailureMessage(failure))
                    AppLogger.write(this, "Connection failed: ${failure.javaClass.simpleName}.")
                    return
                }
                SyncStatus.connection.value = SyncStatus.connection.value.copy(
                    phase = ConnectionPhase.Reconnecting, message = "连接中断，正在重试",
                )
                AppLogger.write(this, "Connection interrupted: ${failure.javaClass.simpleName}.")
                delay(retryMs)
                retryMs = (retryMs * 2).coerceAtMost(30000L)
            }
        }
    }

    private fun connectionFailureMessage(failure: Exception): String = when (failure) {
        is SocketTimeoutException -> "连接超时，请确认电脑端已启动，手机与电脑处于同一局域网"
        is UnknownHostException -> "无法解析电脑地址，请检查地址或重新扫码"
        is ConnectException -> "无法连接电脑，请检查电脑端是否启动、IP 地址与端口是否正确"
        is SSLException -> "安全连接失败，请检查电脑端或重新扫码配对"
        else -> "连接失败，请检查网络与电脑端；详细原因可在连接日志中查看"
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

        fun start(context: Context, serverId: String? = null) {
            if (!canStart(context)) return
            context.startForegroundService(Intent(context, ForegroundSyncService::class.java).apply {
                action = ActionStart
                if (serverId != null) putExtra("serverId", serverId)
            })
        }

        fun forget(context: Context, serverId: String) {
            context.startService(Intent(context, ForegroundSyncService::class.java).apply {
                action = ActionForget
                putExtra("serverId", serverId)
            })
        }

        private fun canStart(context: Context): Boolean {
            if (hasLocalNetworkAccess(context)) return true
            SyncStatus.connection.value = ConnectionState(phase = ConnectionPhase.Failed,
                message = SyncStatus.LocalNetworkPermissionRequired)
            return false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundSyncService::class.java))
            SyncStatus.connection.value = SyncStatus.connection.value.copy(phase = ConnectionPhase.Idle, message = "已停止")
        }
    }
}
