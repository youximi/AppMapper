package dev.youximi.appmapper

import android.app.Activity
import android.content.Intent
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.PairingTarget
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.UsageAppReader
import dev.youximi.appmapper.service.ForegroundSyncService

class AppCoordinator(
    private val activity: Activity,
    private val settings: SettingsStore,
    private val usageReader: UsageAppReader,
) {
    fun loadInitialState(): AppState {
        val target = settings.getTarget()
        return AppState(
            pairingTarget = target,
            pollingMs = settings.getPollingMs(),
            hasUsageAccess = usageReader.hasUsageAccess(),
        )
    }

    fun saveTarget(target: PairingTarget) {
        AppLogger.write(activity, "Pairing target saved: ${target.host}:${target.port}, codeLength=${target.code.length}.")
        settings.saveTarget(target)
    }

    fun savePollingMs(value: Long) {
        AppLogger.write(activity, "Polling interval saved: ${value}ms.")
        settings.savePollingMs(value)
    }

    fun openUsageAccessSettings() {
        AppLogger.write(activity, "Opening usage access settings.")
        activity.startActivity(usageReader.usageAccessIntent())
    }

    fun startService() {
        AppLogger.write(activity, "Start requested from UI.")
        ForegroundSyncService.start(activity)
    }

    fun stopService() {
        AppLogger.write(activity, "Stop requested from UI.")
        ForegroundSyncService.stop(activity)
    }

    fun readLogs(): String = AppLogger.read(activity)

    fun clearLogs() {
        AppLogger.clear(activity)
    }

    fun exportLogs() {
        AppLogger.write(activity, "Logs export requested.")
        val logs = AppLogger.read(activity).ifBlank { "AppMapper log is empty." }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AppMapper logs")
            putExtra(Intent.EXTRA_TEXT, logs)
        }
        activity.startActivity(Intent.createChooser(sendIntent, "导出 AppMapper 日志"))
    }

    fun usageAccessGranted(): Boolean = usageReader.hasUsageAccess()
}

data class AppState(
    val pairingTarget: PairingTarget,
    val pollingMs: Long,
    val hasUsageAccess: Boolean,
)
