package dev.youximi.appmapper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.AppUpdateInfo
import dev.youximi.appmapper.data.GitHubUpdateChecker
import dev.youximi.appmapper.data.DeviceKeyStore
import dev.youximi.appmapper.data.PairingState
import dev.youximi.appmapper.data.PairingStore
import dev.youximi.appmapper.data.PairingRequest
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.UsageAppReader
import dev.youximi.appmapper.service.ForegroundSyncService
import dev.youximi.appmapper.service.ConnectionState
import dev.youximi.appmapper.service.SyncStatus

class AppCoordinator(
    private val activity: Activity,
    private val settings: SettingsStore,
    private val usageReader: UsageAppReader,
) {
    private val pairing = PairingStore(activity)

    val updateState = GitHubUpdateChecker.state
    val includePrereleases: Boolean get() = settings.getIncludePrereleases()
    fun checkUpdatesOnStartup() = GitHubUpdateChecker.checkOnStartup(includePrereleases)
    fun checkUpdates() = GitHubUpdateChecker.check(includePrereleases)

    fun setUpdateChannel(includePrereleases: Boolean) {
        if (this.includePrereleases == includePrereleases) return
        settings.saveIncludePrereleases(includePrereleases)
        settings.saveLastNotifiedBuild(0)
        checkUpdates()
    }

    fun shouldNotifyUpdate(release: AppUpdateInfo): Boolean =
        release.isNewerThan(BuildConfig.VERSION_CODE) &&
            release.buildNumber > settings.getLastNotifiedBuild()

    fun markUpdateNotified(release: AppUpdateInfo) {
        settings.saveLastNotifiedBuild(maxOf(settings.getLastNotifiedBuild(), release.buildNumber))
    }

    fun openRelease(release: AppUpdateInfo): String? = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl)))
        null
    } catch (_: android.content.ActivityNotFoundException) {
        "无法打开浏览器，请安装或启用浏览器后重试"
    }

    fun loadInitialState(): AppState {
        val saved = runCatching { pairing.read() }
        return AppState(
            pairing = saved.getOrDefault(PairingState()),
            pairingError = if (saved.isFailure) "无法读取配对记录，请检查应用存储后重试" else null,
            pollingMs = settings.getPollingMs(),
            hasUsageAccess = usageReader.hasUsageAccess(),
            hasLocalNetworkAccess = hasLocalNetworkAccess(activity),
        )
    }

    fun pair(request: PairingRequest) {
        AppLogger.write(activity, "Pairing requested for ${request.host}:${request.port}.")
        ForegroundSyncService.pair(activity, request)
    }

    fun connectComputer(serverId: String) {
        ForegroundSyncService.start(activity, serverId)
    }

    fun forgetComputer(serverId: String) {
        if (SyncStatus.isRunning.value) {
            ForegroundSyncService.forget(activity, serverId)
            return
        }
        SyncStatus.operationError.value = null
        try {
            val wasSelected = pairing.read().selectedServerId == serverId
            val removed = pairing.remove(serverId)
            removed?.let {
                runCatching { DeviceKeyStore.delete(it.keyAlias) }.onFailure { failure ->
                    AppLogger.write(activity, "Key cleanup failed: ${failure.javaClass.simpleName}.")
                }
            }
            if (wasSelected || SyncStatus.connection.value.serverId == serverId)
                SyncStatus.connection.value = ConnectionState(message = "已忘记电脑，请选择其他电脑")
        } catch (failure: Exception) {
            SyncStatus.operationError.value = "忘记电脑失败，请检查应用存储后重试"
            AppLogger.write(activity, "Forget failed: ${failure.javaClass.simpleName}.")
        }
    }

    fun savePollingMs(value: Long) {
        settings.savePollingMs(value)
    }

    fun openUsageAccessSettings() {
        activity.startActivity(usageReader.usageAccessIntent())
    }

    fun openAppPermissionSettings() {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)))
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
    val pairing: PairingState,
    val pairingError: String?,
    val pollingMs: Long,
    val hasUsageAccess: Boolean,
    val hasLocalNetworkAccess: Boolean,
)

internal fun hasLocalNetworkAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(context,
        Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
