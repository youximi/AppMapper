package dev.youximi.appmapper.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdateCheckerTest {
    private fun release(tag: String = "Version.0.1.0.Release14", body: Any = "更新说明"): JSONObject {
        val build = tag.substringAfterLast("Release")
        val version = tag.removePrefix("Version.").substringBefore(".Release")
        return JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", false)
            .put("body", body).put("assets", JSONArray().put(JSONObject()
                .put("name", "appmapper.android." + version + "." + build + ".apk")
                .put("state", "uploaded").put("size", 1024)))
    }

    @Test fun legacyVersionsUseBuildNumbers() {
        val info = GitHubUpdateChecker.parseRelease(release().toString())
        assertEquals("0.1.0", info.versionName)
        assertTrue(info.isNewerThan(13))
        assertFalse(info.isNewerThan(14))
        assertFalse(info.isNewerThan(15))
    }

    @Test fun currentTagAndDownloadPageAreRecognized() {
        val info = GitHubUpdateChecker.parseRelease(release("Version.0.1.15.Release15").toString())
        assertEquals(15, info.buildNumber)
        assertEquals("https://github.com/youximi/AppMapper/releases/tag/Version.0.1.15.Release15", info.releaseUrl)
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

    @Test(expected = IllegalArgumentException::class)
    fun requiresCompletedUpload() {
        val json = release()
        json.getJSONArray("assets").getJSONObject(0).put("state", "starter")
        GitHubUpdateChecker.parseRelease(json.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownVersionFormat() {
        GitHubUpdateChecker.parseRelease(release().put("tag_name", "v0.1.15").toString())
    }
}
