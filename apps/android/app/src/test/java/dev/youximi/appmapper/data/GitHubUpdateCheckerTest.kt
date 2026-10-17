package dev.youximi.appmapper.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdateCheckerTest {
    private fun release(tag: String = "Version.0.1.0.Release14", body: Any = "更新说明",
                        assetName: String? = null): JSONObject {
        val match = requireNotNull(Regex("""^Version\.(\d+\.\d+\.\d+)\.(Release|Experiment_C)(\d+)$""").matchEntire(tag))
        val version = match.groupValues[1]
        val build = match.groupValues[3]
        return JSONObject().put("tag_name", tag).put("draft", false)
            .put("prerelease", match.groupValues[2] == "Experiment_C")
            .put("body", body).put("assets", JSONArray().put(JSONObject()
                .put("name", assetName ?: "appmapper.android." + version + "." + build + ".apk")
                .put("state", "uploaded").put("size", 1024)))
    }

    @Test fun legacyVersionsUseBuildNumbers() {
        val info = GitHubUpdateChecker.parseRelease(release().toString())
        assertEquals("0.1.0", info.versionName)
        assertTrue(info.isNewerThan(13))
        assertFalse(info.isNewerThan(14))
        assertFalse(info.isNewerThan(15))
    }

    @Test fun legacyDuplicatePackageNameIsRecognized() {
        val info = GitHubUpdateChecker.parseRelease(release("Version.0.1.16.Release16",
            assetName = "appmapper.android.0.1.16.16.apk").toString())
        assertEquals("0.1.16", info.versionName)
        assertEquals(16, info.buildNumber)
        assertEquals("https://github.com/youximi/AppMapper/releases/tag/Version.0.1.16.Release16", info.releaseUrl)
    }

    @Test fun shortPackageNameIsRecognized() {
        val info = GitHubUpdateChecker.parseRelease(release("Version.0.1.17.Release17",
            assetName = "appmapper.android.0.1.17.apk").toString())
        assertEquals("0.1.17", info.versionName)
        assertEquals(17, info.buildNumber)
        assertTrue(info.isNewerThan(16))
        assertFalse(info.isNewerThan(17))
        assertEquals("https://github.com/youximi/AppMapper/releases/tag/Version.0.1.17.Release17", info.releaseUrl)
    }

    @Test fun preservesCommitBodiesAndBlankLines() {
        val changes = "ecbc8dc: ci: 新版本 (Youximi)\n\n"+
            "eb5592c: feat: 离线许可 (Youximi)\n\n添加项目介绍\n支持按库查看\n"
        val notes = "Release Build\r\nVersion: test\r\n\r\nChanges since last build\r\n\r\n" +
            changes.replace("\n", "\r\n") + "\r\nFull Comparison: link\r\nCorresponding source: link"
        assertEquals(changes.trim(), GitHubUpdateChecker.extractNotes(notes))
    }

    @Test fun customNotesAreDisplayedWithoutParsing() {
        assertEquals("自定义说明\n\n完整正文", GitHubUpdateChecker.extractNotes("自定义说明\r\n\r\n完整正文"))
    }

    @Test fun emptyNotesHaveReadableFallback() {
        val info = GitHubUpdateChecker.parseRelease(release(body = JSONObject.NULL).toString())
        assertEquals("此版本未提供更新说明", info.notes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPrereleases() {
        GitHubUpdateChecker.parseRelease(release().put("prerelease", true).toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDrafts() {
        GitHubUpdateChecker.parseRelease(release().put("draft", true).toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresPlatformPackage() {
        GitHubUpdateChecker.parseRelease(release().put("assets", JSONArray()).toString())
    }

    @Test fun requiresCompletedUpload() {
        for (name in listOf("appmapper.android.0.1.17.apk", "appmapper.android.0.1.17.17.apk")) {
            val json = release("Version.0.1.17.Release17", assetName = name)
            json.getJSONArray("assets").getJSONObject(0).put("state", "starter")
            assertThrows(IllegalArgumentException::class.java) {
                GitHubUpdateChecker.parseRelease(json.toString())
            }
        }
    }

    @Test fun requiresNonEmptyPackage() {
        for (name in listOf("appmapper.android.0.1.17.apk", "appmapper.android.0.1.17.17.apk")) {
            val json = release("Version.0.1.17.Release17", assetName = name)
            json.getJSONArray("assets").getJSONObject(0).put("size", 0)
            assertThrows(IllegalArgumentException::class.java) {
                GitHubUpdateChecker.parseRelease(json.toString())
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPackageForDifferentVersion() {
        GitHubUpdateChecker.parseRelease(release("Version.0.1.17.Release17",
            assetName = "appmapper.android.0.1.18.apk").toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherPlatformPackage() {
        GitHubUpdateChecker.parseRelease(release("Version.0.1.17.Release17",
            assetName = "appmapper.windows.0.1.17.zip").toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownVersionFormat() {
        GitHubUpdateChecker.parseRelease(release().put("tag_name", "v0.1.15").toString())
    }

    @Test fun testingChannelAcceptsPrereleasesAndPreservesWarning() {
        val info = GitHubUpdateChecker.parseRelease(release("Version.0.1.103.Experiment_C103",
            body = "滚动测试版\n\nChanges since last build\n\n新功能\nFull Comparison: link",
            assetName = "appmapper.android.0.1.103.apk").toString(), includePrereleases = true)
        assertEquals(103, info.buildNumber)
        assertEquals("测试版（极不稳定，仅供测试）\n\n新功能", info.notes)
        assertEquals("https://github.com/youximi/AppMapper/releases/tag/Version.0.1.103.Experiment_C103", info.releaseUrl)
        assertFalse(info.isNewerThan(103))
        assertFalse(info.isNewerThan(104))
    }

    @Test fun prereleaseLegacyPackageNameIsRecognized() {
        val info = GitHubUpdateChecker.parseRelease(release("Version.0.1.103.Experiment_C103").toString(), true)
        assertEquals(103, info.buildNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stableChannelRejectsExperimentTags() {
        GitHubUpdateChecker.parseRelease(release("Version.0.1.103.Experiment_C103").toString())
    }

    @Test fun rejectsInconsistentReleaseMarkersInBothChannels() {
        val inconsistent = listOf(
            release("Version.0.1.103.Experiment_C103").put("prerelease", false),
            release("Version.0.1.104.Release104").put("prerelease", true),
        )
        for (json in inconsistent) for (includePrereleases in listOf(false, true)) {
            assertThrows(IllegalArgumentException::class.java) {
                GitHubUpdateChecker.parseRelease(json.toString(), includePrereleases)
            }
        }
    }

    @Test fun stableChannelSelectsHighestStableBuild() {
        val releases = JSONArray().put(release("Version.0.1.105.Experiment_C105"))
            .put(release("Version.0.1.102.Release102"))
            .put(release("Version.0.1.104.Release104"))
        assertEquals(104, GitHubUpdateChecker.selectRelease(releases.toString())?.buildNumber)
        assertEquals(105, GitHubUpdateChecker.selectRelease(releases.toString(), true)?.buildNumber)
    }

    @Test fun testingChannelAlsoSelectsNewerStableBuild() {
        val releases = JSONArray().put(release("Version.0.1.103.Experiment_C103"))
            .put(release("Version.0.1.104.Release104"))
        assertEquals(104, GitHubUpdateChecker.selectRelease(releases.toString(), true)?.buildNumber)
    }

    @Test fun selectsLatestApplicablePlatformPackage() {
        val releases = JSONArray().put(release("Version.0.1.108.Release108").put("draft", true))
            .put(release("Version.0.1.107.Experiment_C107", assetName = "appmapper.windows.0.1.107.zip"))
            .put(release("Version.0.1.106.Experiment_C106").put("assets", JSONArray()))
            .put(release("Version.0.1.105.Experiment_C105").apply {
                getJSONArray("assets").getJSONObject(0).put("state", "starter")
            })
            .put(release("Version.0.1.104.Release104"))
        assertEquals(104, GitHubUpdateChecker.selectRelease(releases.toString(), true)?.buildNumber)
    }

    @Test fun ignoresUnrecognizedIndividualReleases() {
        val releases = JSONArray().put(JSONObject()).put("unrecognized entry")
            .put(release("Version.0.1.105.Release105").put("tag_name", "v0.1.105"))
            .put(release("Version.0.1.104.Release104"))
        assertEquals(104, GitHubUpdateChecker.selectRelease(releases.toString(), true)?.buildNumber)
    }

    @Test fun noApplicableReleaseHasNoCandidate() {
        assertNull(GitHubUpdateChecker.selectRelease("[]", true))
        val releases = JSONArray().put(release("Version.0.1.103.Experiment_C103"))
        assertNull(GitHubUpdateChecker.selectRelease(releases.toString()))
    }

    @Test(expected = JSONException::class)
    fun malformedPageIsNotAnEmptyReleaseList() {
        GitHubUpdateChecker.selectRelease("invalid JSON", true)
    }
}
