package com.leapauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GitHubReleaseCheckerTest {

    private fun projectDir(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile ?: return@generateSequence null }
            .first { File(it, "app").isDirectory }
    }

    private val sampleJson = """
        [
          {
            "tag_name": "v4.7.17",
            "name": "4.7.17",
            "body": "1. 旧版本说明",
            "draft": false,
            "html_url": "https://github.com/xiaojiekeji/chekong/releases/tag/v4.7.17",
            "assets": [
              {"name": "leapauto-4.7.17.apk", "browser_download_url": "https://example.com/4.7.17.apk"}
            ]
          },
          {
            "tag_name": "v4.7.18",
            "name": "4.7.18",
            "body": "1. 组件锁车状态圆点\n2. 首页未锁车口径",
            "draft": false,
            "html_url": "https://github.com/xiaojiekeji/chekong/releases/tag/v4.7.18",
            "assets": [
              {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
              {"name": "leapauto-4.7.18.apk", "browser_download_url": "https://example.com/4.7.18.apk"}
            ]
          },
          {
            "tag_name": "v4.7.19-draft",
            "draft": true,
            "assets": [
              {"name": "leapauto.apk", "browser_download_url": "https://example.com/draft.apk"}
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `picks the highest version that ships an apk asset`() {
        val release = GitHubReleaseChecker.parseReleases(sampleJson)
        assertEquals("4.7.18", release?.versionName)
        assertEquals("https://example.com/4.7.18.apk", release?.downloadUrl)
        assertEquals(
            "https://github.com/xiaojiekeji/chekong/releases/tag/v4.7.18",
            release?.pageUrl
        )
        assertTrue(release?.updateDescription?.contains("首页未锁车口径") == true)
    }

    @Test
    fun `tag prefix and non numeric tags are normalised or skipped`() {
        assertEquals("4.7.18", GitHubReleaseChecker.normalizeVersion("v4.7.18"))
        assertEquals("4.7.18", GitHubReleaseChecker.normalizeVersion("  V4.7.18 "))
        assertNull(GitHubReleaseChecker.parseReleases("[]"))
        assertNull(GitHubReleaseChecker.parseReleases("not json"))
    }

    @Test
    fun `version comparison drives update availability`() {
        val latest = GitHubReleaseChecker.parseReleases(sampleJson)!!
        val available = GitHubReleaseChecker.checkFromJson("4.7.16", listOf(latest))
        assertTrue(available is VersionUpdateState.UpdateAvailable)
        assertEquals(latest, (available as VersionUpdateState.UpdateAvailable).latestRelease)

        val upToDate = GitHubReleaseChecker.checkFromJson("4.7.18", listOf(latest))
        assertTrue(upToDate is VersionUpdateState.UpToDate)
    }

    @Test
    fun `static manifest is parsed before hitting the api`() {
        val manifest = """
            {
              "versionName": "v4.7.20",
              "apkUrl": "https://github.com/xiaojiekeji/chekong/releases/download/v4.7.20/leapauto-4.7.20-debug.apk",
              "pageUrl": "https://github.com/xiaojiekeji/chekong/releases/tag/v4.7.20",
              "notes": "1. 修复若干问题"
            }
        """.trimIndent()
        val release = GitHubReleaseChecker.parseManifest(manifest)
        assertEquals("4.7.20", release?.versionName)
        assertEquals(
            "https://github.com/xiaojiekeji/chekong/releases/download/v4.7.20/leapauto-4.7.20-debug.apk",
            release?.downloadUrl
        )
        assertEquals("1. 修复若干问题", release?.updateDescription)
        assertNull(GitHubReleaseChecker.parseManifest("{}"))
        assertNull(GitHubReleaseChecker.parseManifest("oops"))
    }

    @Test
    fun `repo ships an update manifest matching the current release`() {
        val manifest = File(projectDir(), "update.json").readText()
        val parsed = requireNotNull(GitHubReleaseChecker.parseManifest(manifest))
        val version = File(projectDir(), "app/build.gradle.kts").readText()
            .substringAfter("apkVersionName = ").substringAfter('"').substringBefore('"')
        assertEquals(version, parsed.versionName)
        assertTrue(parsed.downloadUrl?.contains("/xiaojiekeji/chekong/releases/download/") == true)
    }

    @Test
    fun `no published release means up to date`() {
        val state = GitHubReleaseChecker.checkFromJson("4.7.18", emptyList())
        assertTrue(state is VersionUpdateState.UpToDate)
        assertEquals("暂无发布版本", (state as VersionUpdateState.UpToDate).latestRelease.updateDescription)
    }

    @Test
    fun `repo deleted by the author degrades to a readable failure`() {
        val state = GitHubReleaseChecker.repoRemovedState("4.7.18")
        assertTrue(state is VersionUpdateState.Failed)
        assertTrue((state as VersionUpdateState.Failed).message.contains("作者删除"))
    }

    @Test
    fun `update channel and repo link point at the new repository`() {
        val checker = File(projectDir(), "app/src/main/java/com/leapauto/app/VersionUpdate.kt").readText()
        val links = File(projectDir(), "app/src/main/java/com/leapauto/app/ExternalLinks.kt").readText()
        val installer = File(projectDir(), "app/src/main/java/com/leapauto/app/AppUpdateInstaller.kt").readText()
        val main = File(projectDir(), "app/src/main/java/com/leapauto/app/MainActivity.kt").readText()

        assertTrue(checker.contains("const val OWNER = \"xiaojiekeji\""))
        assertTrue(checker.contains("const val REPO = \"chekong\""))
        assertTrue(checker.contains("https://api.github.com/repos/"))
        assertTrue(checker.contains("MANIFEST_URL"))
        assertTrue(checker.contains("update.json"))
        assertTrue(checker.contains("HTTP_NOT_FOUND"))
        assertTrue(checker.contains("UpdateSourceRemovedException"))
        assertTrue(links.contains("https://github.com/xiaojiekeji/chekong"))
        assertTrue(installer.contains("release.downloadUrl"))
        assertTrue(main.contains("GitHubReleaseChecker.check("))
        assertTrue(main.contains("GitHubReleaseChecker.WEB_URL"))
    }
}
