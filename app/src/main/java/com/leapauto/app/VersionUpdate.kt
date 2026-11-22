package com.leapauto.app

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class PgyerRelease(
    val versionName: String,
    val buildNumber: Int?,
    val updateDescription: String?,
    val buildKey: String? = null,
    /** GitHub Release 里的 APK 资产直链（新仓库更新通道使用）。 */
    val downloadUrl: String? = null,
    /** Release 页面地址，下载失败时作为兜底跳转。 */
    val pageUrl: String? = null,
    /** 备用下载直链（国内 CDN 镜像），主直链失败时重试使用。 */
    val mirrorUrl: String? = null
)

sealed interface VersionUpdateState {
    object Idle : VersionUpdateState

    data class Checking(val currentVersion: String) : VersionUpdateState

    data class UpToDate(
        val currentVersion: String,
        val latestRelease: PgyerRelease
    ) : VersionUpdateState

    data class UpdateAvailable(
        val currentVersion: String,
        val latestRelease: PgyerRelease
    ) : VersionUpdateState

    data class Failed(
        val currentVersion: String,
        val message: String
    ) : VersionUpdateState
}

object VersionUpdatePromptPolicy {
    fun shouldShow(
        state: VersionUpdateState,
        handledVersion: String?,
        loggedIn: Boolean = true,
        onVehicleTab: Boolean = true
    ): Boolean {
        val update = state as? VersionUpdateState.UpdateAvailable ?: return false
        return update.latestRelease.versionName != handledVersion
    }
}

object VersionComparator {
    fun compare(left: String, right: String): Int {
        val leftParts = parse(left)
        val rightParts = parse(right)
        if (leftParts == null || rightParts == null) {
            return left.trim().compareTo(right.trim())
        }
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return 0
    }

    private fun parse(version: String): List<Int>? {
        val normalized = version.trim()
        if (!normalized.matches(Regex("\\d+(?:\\.\\d+){0,2}"))) return null
        return normalized.split('.').map { it.toInt() }
    }
}

object PgyerPageParser {
    private const val lineBreakMarker = "\u0000LEAPAUTO_LINE_BREAK\u0000"

    private val versionPattern = Regex(
        "版本\\s*[:：]\\s*(\\d+(?:\\.\\d+){1,2})\\s*(?:\\(\\s*build\\s*(\\d+)\\s*\\))?",
        RegexOption.IGNORE_CASE
    )
    private val descriptionPattern = Regex(
        "<div\\s+class=\\\"update-description\\\"[^>]*>(.*?)</div>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val buildKeyPattern = Regex(
        "(?:/app/build/|/app/install/|(?:aKey|appKey|buildKey)\\s*=\\s*['\"])([a-f0-9]{32})",
        RegexOption.IGNORE_CASE
    )

    fun parse(html: String): PgyerRelease? {
        val plainText = htmlToText(html)
        val versionMatch = versionPattern.find(plainText) ?: return null
        val description = descriptionPattern.find(html)?.groupValues?.get(1)
            ?.let(::normalizeUpdateDescription)
        val buildKey = buildKeyPattern.find(html)?.groupValues?.get(1)
        return PgyerRelease(
            versionName = versionMatch.groupValues[1],
            buildNumber = versionMatch.groupValues.getOrNull(2)?.toIntOrNull(),
            updateDescription = description,
            buildKey = buildKey
        )
    }

    private fun htmlToText(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    fun formatUpdateDescription(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return value
            .replace("\\\\n", "\n")
            .replace("\\n", "\n")
            .replace(Regex("(\\d+)\\\\([\\.、\\)])"), "$1$2")
            .replace(Regex("(?<=[；;。])\\s*(?=\\d+[\\.、\\)])"), "\n")
            .replace(Regex("(?<!\n|^)\\s+(?=\\d+[\\.、\\)])"), "\n")
            .lineSequence()
            .map { line -> line.trim().replace(Regex("[\\t ]+"), " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun normalizeUpdateDescription(value: String): String? = value
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), lineBreakMarker)
        .let(::htmlToText)
        .replace(lineBreakMarker, "\n")
        .let(::formatUpdateDescription)
}

object PgyerUpdateChecker {
    const val DOWNLOAD_URL = "https://www.pgyer.com/lingpaozhikong"

    fun check(currentVersion: String): VersionUpdateState {
        val latestRelease = fetchLatestRelease()
        return if (VersionComparator.compare(currentVersion, latestRelease.versionName) < 0) {
            VersionUpdateState.UpdateAvailable(currentVersion, latestRelease)
        } else {
            VersionUpdateState.UpToDate(currentVersion, latestRelease)
        }
    }

    fun fetchLatestRelease(): PgyerRelease {
        val connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("蒲公英返回 HTTP ${connection.responseCode}")
            }
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return PgyerPageParser.parse(html)
                ?: throw IOException("未找到蒲公英最新版本信息")
        } finally {
            connection.disconnect()
        }
    }
}

/** 更新源（GitHub 仓库/版本）已被作者删除时抛出，用于给出可读提示。 */
class UpdateSourceRemovedException(message: String) : IOException(message)

/**
 * 自动更新通道：改为读取 chekong 仓库的 GitHub Releases。
 *
 * - 取最近若干条 release，挑版本号最高且带 APK 资产的一条；
 * - 仓库或版本被作者删除（HTTP 404）时返回可读的失败状态，不抛崩溃；
 * - 仓库还没有发布任何版本时按「已是最新」处理。
 */
object GitHubReleaseChecker {
    const val OWNER = "xiaojiekeji"
    const val REPO = "chekong"
    const val WEB_URL = "https://github.com/$OWNER/$REPO/releases"
    const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10"

    /**
     * 仓库内静态清单（raw CDN 分发，不受 GitHub API 每小时 60 次的匿名限流影响），
     * 发版时由维护者更新 update.json 即可，App 端优先读它。
     */
    const val MANIFEST_URL = "https://raw.githubusercontent.com/$OWNER/$REPO/master/update.json"

    fun check(currentVersion: String): VersionUpdateState {
        val release = try {
            fetchLatestRelease()
        } catch (e: UpdateSourceRemovedException) {
            return repoRemovedState(currentVersion)
        }
        return checkFromJson(currentVersion, release?.let { listOf(it) } ?: emptyList())
    }

    /** 仓库/版本已被作者删除时的展示状态（静默失败，不弹更新提示）。 */
    fun repoRemovedState(currentVersion: String): VersionUpdateState =
        VersionUpdateState.Failed(currentVersion, "更新源已被作者删除（仓库不可用）")

    /** 纯函数：按版本比较决定状态，便于单测覆盖。 */
    fun checkFromJson(currentVersion: String, releases: List<PgyerRelease>): VersionUpdateState {
        val latest = releases.maxWithOrNull { a, b -> VersionComparator.compare(a.versionName, b.versionName) }
            ?: return VersionUpdateState.UpToDate(
                currentVersion,
                PgyerRelease(currentVersion, null, "暂无发布版本")
            )
        return if (VersionComparator.compare(currentVersion, latest.versionName) < 0) {
            VersionUpdateState.UpdateAvailable(currentVersion, latest)
        } else {
            VersionUpdateState.UpToDate(currentVersion, latest)
        }
    }

    fun fetchLatestRelease(): PgyerRelease? {
        val fromManifest = try {
            fetchManifest()
        } catch (e: UpdateSourceRemovedException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
        if (fromManifest != null && !fromManifest.downloadUrl.isNullOrBlank()) return fromManifest
        return fetchViaApi()
    }

    /** 读取 update.json 清单。 */
    fun fetchManifest(): PgyerRelease? {
        val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw UpdateSourceRemovedException("清单已不存在")
            if (code !in 200..299) throw IOException("更新清单返回 HTTP $code")
            return parseManifest(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** 解析 update.json：{versionName, apkUrl, pageUrl, notes}。 */
    fun parseManifest(json: String): PgyerRelease? {
        val obj = kotlin.runCatching { org.json.JSONObject(json) }.getOrNull() ?: return null
        val version = normalizeVersion(obj.optString("versionName"))
        if (!version.matches(Regex("\\d+(?:\\.\\d+){0,2}"))) return null
        val apkUrl = obj.optString("apkUrl").takeIf { it.isNotBlank() } ?: return null
        val mirrorUrl = obj.optString("apkUrlMirror").takeIf { it.isNotBlank() }
        return PgyerRelease(
            versionName = version,
            buildNumber = null,
            updateDescription = obj.optString("notes").takeIf { it.isNotBlank() },
            buildKey = null,
            downloadUrl = apkUrl,
            pageUrl = obj.optString("pageUrl").takeIf { it.isNotBlank() },
            mirrorUrl = mirrorUrl
        )
    }

    private fun fetchViaApi(): PgyerRelease? {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LeapAuto/${BuildConfig.VERSION_NAME}")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_NOT_FOUND ->
                    throw UpdateSourceRemovedException("仓库或版本已被作者删除")
                code == HttpURLConnection.HTTP_FORBIDDEN || code == 429 ->
                    throw IOException("更新源访问受限（HTTP $code），请稍后再试")
                code !in 200..299 ->
                    throw IOException("更新源返回 HTTP $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseReleases(body)
        } finally {
            connection.disconnect()
        }
    }

    /** 解析 GitHub Releases 列表，返回版本号最高且带 APK 资产的一条。 */
    fun parseReleases(json: String): PgyerRelease? {
        val array = kotlin.runCatching { org.json.JSONArray(json) }.getOrNull() ?: return null
        val candidates = ArrayList<PgyerRelease>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            if (obj.optBoolean("draft")) continue
            val version = normalizeVersion(obj.optString("tag_name"))
            if (!version.matches(Regex("\\d+(?:\\.\\d+){0,2}"))) continue
            var apkUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        if (apkUrl != null) break
                    }
                }
            }
            candidates += PgyerRelease(
                versionName = version,
                buildNumber = null,
                updateDescription = obj.optString("body").takeIf { it.isNotBlank() },
                buildKey = null,
                downloadUrl = apkUrl,
                pageUrl = obj.optString("html_url").takeIf { it.isNotBlank() }
            )
        }
        return candidates.maxWithOrNull { a, b -> VersionComparator.compare(a.versionName, b.versionName) }
    }

    fun normalizeVersion(tag: String): String = tag.trim().removePrefix("v").removePrefix("V").trim()
}
