package com.inputleaf.android.update

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

enum class InstallSource {
    FDROID,
    PLAY_STORE,
    GITHUB
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val latestVersion: String,
        val changelog: String,
        val updateUrl: String,
        val isFdroid: Boolean
    ) : UpdateCheckResult

    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Which GitHub releases the in-app updater offers. Pre-release also offers stable
 * releases, so a beta user is moved onto the final build once it ships.
 */
enum class UpdateChannel(val storageValue: String) {
    STABLE("stable"),
    PRERELEASE("prerelease");

    companion object {
        fun fromStorage(value: String?): UpdateChannel? = entries.firstOrNull { it.storageValue == value }
    }
}

data class VersionChangelog(
    val versionName: String,
    val versionCode: Int,
    val highlights: List<String>,
)

object UpdateService {

    internal const val GITHUB_API_LATEST_RELEASE =
        "https://api.github.com/repos/anasvhora284/input-leaf/releases/latest"
    /** /releases/latest never returns a pre-release, so that channel reads the list. */
    internal const val GITHUB_API_RELEASES =
        "https://api.github.com/repos/anasvhora284/input-leaf/releases?per_page=20"
    internal const val FDROID_MARKET_URI = "market://details?id=com.inputleaf.android"
    internal const val GITHUB_RELEASES_WEB_URL =
        "https://github.com/anasvhora284/input-leaf/releases/latest"

    private val RELEASES = listOf(
        VersionChangelog(
            versionName = "1.4.4",
            versionCode = 11,
            highlights = listOf(
                "Fixed connecting a second time (or to another server) with no mouse or keyboard until the app restarted.",
                "Fixed a crash when rotating the phone while connected.",
                "Fixed Accessibility mouse clicks on Android TV devices without touch or fake-touch support.",
                "Left click now activates the accessibility element beneath the software cursor.",
                "Right click now performs Android Back on non-touch Accessibility devices.",
                "Settings can opt in to pre-release updates, and a pre-release install is offered the final release when it ships.",
            ),
        ),
        VersionChangelog(
            versionName = "1.4.3",
            versionCode = 9,
            highlights = listOf(
                "Rooted devices can grant su and inject through a uid-0 HID service, without Shizuku.",
                "New Material 3 Expressive design with dynamic color, edge-to-edge screens, and a refreshed settings layout.",
                "Fixed a crash when connecting on Android 10 and older (including Huawei HarmonyOS tablets).",
                "Saved servers now persist across restarts, with a redesigned servers page and trusted-servers manager.",
                "Optional stylus mode injects a HID pen for apps that want absolute pen input.",
                "Better macOS server key mapping and Android TV launcher support.",
                "Experimental Synergy 3 support, contributed by @joihn.",
            ),
        ),
        VersionChangelog(
            versionName = "1.4.2",
            versionCode = 8,
            highlights = listOf(
                "Show the mouse cursor when using Accessibility Service without draw-over-apps permission.",
                "Use a real HID keyboard (Shizuku) in Shizuku and Accessibility modes, attached only while the cursor is on this device.",
            ),
        ),
        VersionChangelog(
            versionName = "1.4.1",
            versionCode = 7,
            highlights = listOf(
                "Added About & Community section with developer portfolio, GitHub, LinkedIn, and contributor credits.",
                "Added update checker with intelligent F-Droid and GitHub release redirection.",
                "Added 'What's New' changelog display upon upgrading.",
                "Enhanced input injection performance and connection stability.",
            ),
        ),
        VersionChangelog(
            versionName = "1.4.0",
            versionCode = 6,
            highlights = listOf(
                "Added TLS-secured connections with TOFU certificate pinning.",
                "Added client certificate management and custom fingerprints.",
                "Modernized Material 3 UI with enhanced connection cards and favorites.",
            ),
        ),
    )

    fun getChangelog(versionName: String): VersionChangelog {
        val clean = versionName.removePrefix("v").removePrefix("V").substringBefore('-')
        return RELEASES.firstOrNull { it.versionName == clean } ?: RELEASES.first()
    }

    fun getInstallSource(context: Context): InstallSource {
        val installer = readInstallerPackageName(context)
        return resolveInstallSource(installer)
    }

    fun getCurrentVersion(context: Context): String {
        return versionNameFrom(readPackageInfo(context))
    }

    fun getCurrentVersionCode(context: Context): Long {
        return versionCodeFrom(readPackageInfo(context))
    }

    suspend fun checkUpdate(
        context: Context,
        openConnection: (URL) -> HttpURLConnection = defaultConnectionOpener,
        channel: UpdateChannel = UpdateChannel.STABLE,
    ): UpdateCheckResult {
        val currentVersion = getCurrentVersion(context)
        val isFdroid = getInstallSource(context) == InstallSource.FDROID
        return checkUpdate(currentVersion, isFdroid, openConnection, channel)
    }

    /** A pre-release build stays on the pre-release channel until the user opts out. */
    fun defaultChannel(currentVersion: String): UpdateChannel =
        if ('-' in currentVersion) UpdateChannel.PRERELEASE else UpdateChannel.STABLE

    internal suspend fun checkUpdate(
        currentVersion: String,
        isFdroid: Boolean,
        openConnection: (URL) -> HttpURLConnection,
        channel: UpdateChannel = UpdateChannel.STABLE,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        // F-Droid only ships stable builds, so its users are never pointed at a pre-release.
        val prerelease = channel == UpdateChannel.PRERELEASE && !isFdroid
        try {
            val connection = openConnection(URL(if (prerelease) GITHUB_API_RELEASES else GITHUB_API_LATEST_RELEASE))
            connection.useAndDisconnect {
                val responseCode = responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext httpErrorResult(responseCode)
                }

                val responseBody = inputStream.bufferedReader().use(BufferedReader::readText)
                if (prerelease) {
                    parseReleaseListResponse(responseBody, currentVersion, isFdroid)
                } else {
                    parseLatestReleaseResponse(responseBody, currentVersion, isFdroid)
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    internal val defaultConnectionOpener: (URL) -> HttpURLConnection = { url ->
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "InputLeaf-Android")
            connectTimeout = 10000
            readTimeout = 10000
        }
    }

    /** True when [candidate] has higher SemVer precedence than [current]. */
    fun isNewerVersion(candidate: String, current: String): Boolean {
        if (candidate.isBlank() || current.isBlank()) return false
        return compareVersions(candidate, current) > 0
    }

    /**
     * SemVer precedence: the numeric core first, then a pre-release sorts below its
     * release (1.4.4-beta.1 < 1.4.4-beta.2 < 1.4.4-rc.1 < 1.4.4). Comparing the suffix
     * is what lets a pre-release install be offered the final release of the same version.
     */
    fun compareVersions(a: String, b: String): Int {
        val (coreA, preA) = splitVersion(a)
        val (coreB, preB) = splitVersion(b)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val diff = (coreA.getOrNull(i) ?: 0).compareTo(coreB.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        if (preA == null) return if (preB == null) 0 else 1
        if (preB == null) return -1
        for (i in 0 until maxOf(preA.size, preB.size)) {
            val x = preA.getOrNull(i) ?: return -1
            val y = preB.getOrNull(i) ?: return 1
            val xNum = x.toIntOrNull()
            val yNum = y.toIntOrNull()
            val diff = when {
                xNum != null && yNum != null -> xNum.compareTo(yNum)
                xNum != null -> -1
                yNum != null -> 1
                else -> x.compareTo(y)
            }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun splitVersion(version: String): Pair<List<Int>, List<String>?> {
        val clean = version.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val core = clean.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
        val pre = clean.substringAfter('-', "").takeIf { it.isNotEmpty() }?.split('.')
        return core to pre
    }

    private fun readInstallerPackageName(context: Context): String? =
        installerPackageNameForSdk(
            sdkInt = Build.VERSION.SDK_INT,
            modernLookup = { readModernInstallerPackageName(context) },
            legacyLookup = { readLegacyInstallerPackageName(context) },
        )

    private fun readPackageInfo(context: Context): PackageInfo? =
        packageInfoForSdk(
            sdkInt = Build.VERSION.SDK_INT,
            modernLookup = { readModernPackageInfo(context) },
            legacyLookup = { readLegacyPackageInfo(context) },
        )
}

internal fun readModernInstallerPackageName(context: Context): String? =
    installSourcePackageName(context.packageManager.getInstallSourceInfo(context.packageName))

internal fun installSourcePackageName(installSourceInfo: InstallSourceInfo): String? =
    installSourceInfo.installingPackageName

@Suppress("DEPRECATION")
internal fun readLegacyInstallerPackageName(context: Context): String? =
    context.packageManager.getInstallerPackageName(context.packageName)

internal fun readModernPackageInfo(context: Context): PackageInfo =
    context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(0)
    )

@Suppress("DEPRECATION")
internal fun readLegacyPackageInfo(context: Context): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)

internal fun installerPackageNameForSdk(
    sdkInt: Int,
    modernLookup: () -> String?,
    legacyLookup: () -> String?,
): String? {
    return try {
        if (sdkInt >= Build.VERSION_CODES.R) {
            modernLookup()
        } else {
            legacyLookup()
        }
    } catch (_: Exception) {
        null
    }
}

internal fun packageInfoForSdk(
    sdkInt: Int,
    modernLookup: () -> PackageInfo,
    legacyLookup: () -> PackageInfo,
): PackageInfo? {
    return try {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            modernLookup()
        } else {
            legacyLookup()
        }
    } catch (_: Exception) {
        null
    }
}

internal fun resolveInstallSource(installerPackage: String?): InstallSource {
    return when {
        installerPackage != null &&
            (installerPackage.contains("fdroid", ignoreCase = true) ||
                installerPackage.contains("droidify", ignoreCase = true)) ->
            InstallSource.FDROID
        installerPackage != null && installerPackage.contains("vending", ignoreCase = true) ->
            InstallSource.PLAY_STORE
        else ->
            InstallSource.GITHUB
    }
}

internal fun versionNameFrom(packageInfo: PackageInfo?): String =
    packageInfo?.versionName ?: "1.4.4"

internal fun versionCodeFrom(packageInfo: PackageInfo?, sdkInt: Int = Build.VERSION.SDK_INT): Long {
    if (packageInfo == null) return 11L
    return if (sdkInt >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
}

internal fun httpErrorResult(responseCode: Int): UpdateCheckResult.Error =
    UpdateCheckResult.Error("HTTP error $responseCode from GitHub")

internal fun parseLatestReleaseResponse(
    responseBody: String,
    currentVersion: String,
    isFdroid: Boolean,
): UpdateCheckResult {
    return releaseResult(JSONObject(responseBody), currentVersion, isFdroid)
}

/** Picks the highest-precedence published release, pre-releases included. */
internal fun parseReleaseListResponse(
    responseBody: String,
    currentVersion: String,
    isFdroid: Boolean,
): UpdateCheckResult {
    val releases = JSONArray(responseBody)
    val newest = (0 until releases.length())
        .map(releases::getJSONObject)
        .filter { !it.optBoolean("draft") && it.optString("tag_name").isNotBlank() }
        .maxWithOrNull { a, b -> UpdateService.compareVersions(a.optString("tag_name"), b.optString("tag_name")) }
        ?: return UpdateCheckResult.UpToDate(currentVersion)
    return releaseResult(newest, currentVersion, isFdroid)
}

private fun releaseResult(
    json: JSONObject,
    currentVersion: String,
    isFdroid: Boolean,
): UpdateCheckResult {
    val rawTagName = json.optString("tag_name", "").trim()
    val latestVersion = rawTagName.removePrefix("v").removePrefix("V")
    val releaseNotes = json.optString("body", "").trim()
    val githubHtmlUrl = json.optString("html_url", UpdateService.GITHUB_RELEASES_WEB_URL)

    val targetUrl = if (isFdroid) {
        UpdateService.FDROID_MARKET_URI
    } else {
        githubHtmlUrl.ifEmpty { UpdateService.GITHUB_RELEASES_WEB_URL }
    }

    return if (UpdateService.isNewerVersion(latestVersion, currentVersion)) {
        UpdateCheckResult.UpdateAvailable(
            latestVersion = latestVersion,
            changelog = releaseNotes,
            updateUrl = targetUrl,
            isFdroid = isFdroid
        )
    } else {
        UpdateCheckResult.UpToDate(currentVersion)
    }
}

internal inline fun <T> HttpURLConnection.useAndDisconnect(block: HttpURLConnection.() -> T): T {
    try {
        return block()
    } finally {
        disconnect()
    }
}
