package dev.youximi.appmapper.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

sealed class CurrentAppResult {
    data class Active(val app: ActiveApp) : CurrentAppResult()
    object Launcher : CurrentAppResult()
    object Unknown : CurrentAppResult()
}

class UsageAppReader(private val context: Context) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    private var lastForegroundPackage: String? = null

    fun hasUsageAccess(): Boolean {
        val end = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 60_000, end)
        return !stats.isNullOrEmpty()
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun readCurrentApp(): CurrentAppResult {
        val end = System.currentTimeMillis()
        val begin = end - EventLookbackMs
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var currentPackage = lastForegroundPackage

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                -> currentPackage = event.packageName
            }
        }

        val packageName = currentPackage ?: run {
            lastForegroundPackage = null
            return CurrentAppResult.Unknown
        }
        if (isLauncher(packageName)) {
            lastForegroundPackage = null
            return CurrentAppResult.Launcher
        }
        lastForegroundPackage = packageName

        val appInfo = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        val label = appInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
        return CurrentAppResult.Active(
            ActiveApp(
                appId = "android:$packageName",
                packageName = packageName,
                displayName = label,
                iconPngBase64 = AppIconEncoder.encodePngBase64(context, packageName),
            ),
        )
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return launchers.any { it.activityInfo.packageName == packageName }
    }

    companion object {
        private const val EventLookbackMs = 10 * 60_000L
    }
}
