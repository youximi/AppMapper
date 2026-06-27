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
import dev.youximi.appmapper.data.ActiveApp
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.CurrentAppResult
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.TcpJsonClient
import dev.youximi.appmapper.data.UsageAppReader
import dev.youximi.appmapper.data.activeAppJson
import dev.youximi.appmapper.data.heartbeatJson
import dev.youximi.appmapper.data.helloJson
import dev.youximi.appmapper.data.idleJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForegroundSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = TcpJsonClient()
    private var syncJob: Job? = null
    private var sequence = 0L
    private var lastAppId: String? = null
    private var lastIdleReason: String? = null
    private var waitingForTargetLogged = false
    private var missingUsageAccessLogged = false
    private var unknownCurrentAppLogged = false

    override fun onCreate() {
        super.onCreate()
        AppLogger.write(this, "Foreground service created.")
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (syncJob?.isActive != true) {
            AppLogger.write(this, "Foreground sync loop starting.")
            syncJob = scope.launch { runSyncLoop() }
        } else {
            AppLogger.write(this, "Foreground sync loop already running.")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLogger.write(this, "Foreground service destroyed.")
        syncJob?.cancel()
        client.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runSyncLoop() {
        val settings = SettingsStore(this)
        val reader = UsageAppReader(this)
        val deviceId = settings.getDeviceId()
        var lastHeartbeat = 0L

        while (currentCoroutineContext().isActive) {
            val target = settings.getTarget()
            if (target.host.isBlank() || target.code.isBlank()) {
                if (!waitingForTargetLogged) {
                    AppLogger.write(this, "Waiting for pairing target.")
                    waitingForTargetLogged = true
                }
                delay(1000)
                continue
            }
            waitingForTargetLogged = false

            runCatching {
                if (!client.isConnected) {
                    AppLogger.write(this, "Connecting to ${target.host}:${target.port}.")
                    val ack = client.connect(
                        target,
                        helloJson(
                            deviceId = deviceId,
                            deviceName = android.os.Build.MODEL ?: "Android",
                            pairingCode = target.code,
                        ),
                    )
                    if (ack?.optBoolean("accepted") != true) {
                        AppLogger.write(this, "Pairing rejected. ack=${ack?.optString("message") ?: "null"}")
                        client.close()
                        delay(3000)
                        return@runCatching
                    }
                    AppLogger.write(this, "Connected and paired with ${target.host}:${target.port}.")
                    lastAppId = null
                    lastIdleReason = null
                    unknownCurrentAppLogged = false
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

                        CurrentAppResult.Unknown -> {
                            sendUnknownIfNoActiveWindow(deviceId, "usage_events_empty")
                        }
                    }
                }

                val now = System.currentTimeMillis()
                if (now - lastHeartbeat > 5000) {
                    client.send(heartbeatJson(deviceId))
                    lastHeartbeat = now
                }
            }.onFailure {
                if (it is CancellationException) throw it
                AppLogger.write(this, "Sync loop failure. Closing TCP client and retrying.", it)
                client.close()
                lastAppId = null
                delay(3000)
            }

            delay(settings.getPollingMs())
        }
    }

    private suspend fun sendActiveIfNeeded(deviceId: String, app: ActiveApp) {
        if (lastAppId == app.appId) return
        sequence += 1
        AppLogger.write(this, "Sending active_app seq=$sequence app=${app.displayName} package=${app.packageName} icon=${!app.iconPngBase64.isNullOrBlank()}.")
        client.send(
            activeAppJson(
                deviceId = deviceId,
                sequence = sequence,
                app = app,
                screenOn = currentIdleReason() != "screen_off",
                locked = currentIdleReason() == "locked",
            ),
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

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            getString(R.string.foreground_service_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val ChannelId = "appmapper_sync"
        private const val NotificationId = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ForegroundSyncService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundSyncService::class.java))
        }
    }
}
