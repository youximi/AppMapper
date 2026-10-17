package dev.youximi.appmapper.data

import dev.youximi.appmapper.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateCheckPhase { Idle, Checking, Current, Available, Failed }

data class AppUpdateInfo(
    val versionName: String,
    val buildNumber: Int,
    val notes: String,
    val releaseUrl: String,
) {
    fun isNewerThan(installedBuild: Int): Boolean = buildNumber > installedBuild
}

data class UpdateCheckState(
    val phase: UpdateCheckPhase = UpdateCheckPhase.Idle,
    val release: AppUpdateInfo? = null,
    val message: String = "尚未检查更新",
)

object GitHubUpdateChecker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(UpdateCheckState())
    val state = mutableState.asStateFlow()
    private var startupChecked = false

    // Process-scoped state survives activity recreation and never retains an Activity.
    @Synchronized
    fun checkOnStartup(includePrereleases: Boolean = false) {
        if (startupChecked) return
        startupChecked = true
        check(includePrereleases)
    }

    @Synchronized
    fun check(includePrereleases: Boolean = false) {
        if (mutableState.value.phase == UpdateCheckPhase.Checking) return
        mutableState.value = UpdateCheckState(
            phase = UpdateCheckPhase.Checking, message = "正在检查更新…",
        )
        scope.launch {
            mutableState.value = try {
                val release = fetchRelease(includePrereleases)
                val newer = release?.isNewerThan(BuildConfig.VERSION_CODE) == true
                UpdateCheckState(
                    phase = if (newer) UpdateCheckPhase.Available else UpdateCheckPhase.Current,
                    release = release,
                    message = when {
                        release == null -> "当前通道暂未发布适用版本"
                        newer -> "发现新版本 " + release.versionName
                        else -> "当前通道暂无更新"
                    },
                )
            } catch (failure: IOException) {
                UpdateCheckState(UpdateCheckPhase.Failed,
                    message = failure.message ?: "无法连接 GitHub，请检查网络后重试")
            } catch (_: IllegalArgumentException) {
                UpdateCheckState(UpdateCheckPhase.Failed, message = "发布信息不完整或格式无法识别，请稍后重试")
            } catch (_: JSONException) {
                UpdateCheckState(UpdateCheckPhase.Failed, message = "无法读取发布信息，请稍后重试")
            }
        }
    }

    private fun fetchRelease(includePrereleases: Boolean): AppUpdateInfo? {
        var latest: AppUpdateInfo? = null
        var url: String? = "https://api.github.com/repos/youximi/AppMapper/releases?per_page=100"
        while (url != null) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "AppMapper/" + BuildConfig.VERSION_NAME)
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                when (val status = connection.responseCode) {
                    200 -> {
                        val page = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        latest = listOfNotNull(latest, selectRelease(page, includePrereleases)).maxByOrNull { it.buildNumber }
                    }
                    404 -> throw IOException("尚未找到发布信息，请稍后重试")
                    403, 429 -> throw IOException("GitHub 请求受限，请稍后手动重试")
                    else -> throw IOException("GitHub 请求失败（HTTP " + status + "），请稍后重试")
                }
                url = Regex("<([^>]+)>;\\s*rel=\"next\"").find(connection.getHeaderField("Link").orEmpty())?.groupValues?.get(1)
            } finally {
                connection.disconnect()
            }
        }
        return latest
    }

    internal fun selectRelease(json: String, includePrereleases: Boolean = false): AppUpdateInfo? {
        val releases = JSONArray(json)
        return (0 until releases.length()).mapNotNull { index ->
            try {
                parseRelease(releases.getJSONObject(index).toString(), includePrereleases)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: JSONException) {
                null
            }
        }.maxByOrNull { it.buildNumber }
    }

    internal fun parseRelease(json: String, includePrereleases: Boolean = false): AppUpdateInfo {
        val release = JSONObject(json)
        val isPrerelease = release.getBoolean("prerelease")
        require(!release.getBoolean("draft") && (includePrereleases || !isPrerelease))
        val tag = release.getString("tag_name")
        val match = Regex("""^Version\.(\d+\.\d+\.\d+)\.(Release|Experiment_C)([1-9]\d*)$""").matchEntire(tag)
        requireNotNull(match)
        require((match.groupValues[2] == "Experiment_C") == isPrerelease)
        val version = match.groupValues[1]
        val build = match.groupValues[3].toInt()
        val assetName = "appmapper.android." + version + ".apk"
        val legacyAssetName = "appmapper.android." + version + "." + build + ".apk"
        val assets = release.getJSONArray("assets")
        require((0 until assets.length()).any { index ->
            val asset = assets.getJSONObject(index)
            val name = asset.optString("name")
            (name == assetName || name == legacyAssetName) &&
                asset.optString("state") == "uploaded" && asset.optLong("size") > 0
        })
        val changes = extractNotes(release.optString("body", ""))
        val notes = if (isPrerelease) "测试版（极不稳定，仅供测试）\n\n$changes" else changes
        return AppUpdateInfo(version, build, notes,
            "https://github.com/youximi/AppMapper/releases/tag/" + tag)
    }

    internal fun extractNotes(body: String): String {
        val normalized = body.replace("\r\n", "\n").trim()
        val marker = "Changes since last build\n"
        val start = normalized.indexOf(marker)
        if (start < 0) return normalized.ifBlank { "此版本未提供更新说明" }
        val changes = normalized.substring(start + marker.length)
        val end = changes.lastIndexOf("\nFull Comparison:")
        return (if (end >= 0) changes.substring(0, end) else changes).trim()
            .ifBlank { "此版本未提供更新说明" }
    }
}
