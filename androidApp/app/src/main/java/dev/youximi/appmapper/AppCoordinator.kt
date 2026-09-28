package dev.youximi.appmapper

import android.app.Activity
import android.content.Intent
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.PairedComputer
import dev.youximi.appmapper.data.PairingStore
import dev.youximi.appmapper.data.PairingRequest
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.UsageAppReader
import dev.youximi.appmapper.service.ForegroundSyncService

class AppCoordinator(
    private val activity: Activity,
    private val settings: SettingsStore,
    private val usageReader: UsageAppReader,
) {
    private val pairing = PairingStore(activity)

    fun loadInitialState(): AppState = AppState(
        pairedComputer = runCatching { pairing.read() }.getOrNull(),
        pollingMs = settings.getPollingMs(),
        hasUsageAccess = usageReader.hasUsageAccess(),
    )

    fun pair(request: PairingRequest) {
        AppLogger.write(activity, "Pairing requested for ${request.host}:${request.port}.")
        ForegroundSyncService.pair(activity, request)
    }

    fun forgetComputer() {
        ForegroundSyncService.forget(activity)
    }

    fun savePollingMs(value: Long) {
        settings.savePollingMs(value)
    }

    fun openUsageAccessSettings() {
        activity.startActivity(usageReader.usageAccessIntent())
    }

    fun startService() = ForegroundSyncService.start(activity)
    fun stopService() = ForegroundSyncService.stop(activity)
    fun readLogs(): String = AppLogger.read(activity)
    fun clearLogs() = AppLogger.clear(activity)

    fun exportLogs() {
        val logs = AppLogger.read(activity).ifBlank { "AppMapper log is empty." }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AppMapper logs")
            putExtra(Intent.EXTRA_TEXT, logs)
        }
        activity.startActivity(Intent.createChooser(sendIntent, "导出 AppMapper 日志"))
    }
}

data class AppState(
    val pairedComputer: PairedComputer?,
    val pollingMs: Long,
    val hasUsageAccess: Boolean,
)
