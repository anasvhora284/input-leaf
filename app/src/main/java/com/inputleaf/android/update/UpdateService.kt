package com.inputleaf.android.update

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class VersionChangelog(
    val versionName: String,
    val versionCode: Int,
    val highlights: List<String>,
)

object UpdateService {

    internal const val GITHUB_API_LATEST_RELEASE =
        "https://api.github.com/repos/anasvhora284/input-leaf/releases/latest"
    internal const val FDROID_MARKET_URI = "market://details?id=com.inputleaf.android"
    internal const val GITHUB_RELEASES_WEB_URL =
        "https://github.com/anasvhora284/input-leaf/releases/latest"

    private val RELEASES = listOf(
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
        val clean = versionName.removePrefix("v").removePrefix("V")
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
    ): UpdateCheckResult {
        val currentVersion = getCurrentVersion(context)
        val isFdroid = getInstallSource(context) == InstallSource.FDROID
        return checkUpdate(currentVersion, isFdroid, openConnection)
    }

    internal suspend fun checkUpdate(
        currentVersion: String,
        isFdroid: Boolean,
        openConnection: (URL) -> HttpURLConnection,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = openConnection(URL(GITHUB_API_LATEST_RELEASE))
            connection.useAndDisconnect {
                val responseCode = responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext httpErrorResult(responseCode)
                }

                val responseBody = inputStream.bufferedReader().use(BufferedReader::readText)
                parseLatestReleaseResponse(responseBody, currentVersion, isFdroid)
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

    /**
     * Compare two semantic version strings (e.g. "1.4.2" vs "1.4.1").
     * Returns true if candidate > current.
     */
    fun isNewerVersion(candidate: String, current: String): Boolean {
        if (candidate.isBlank() || current.isBlank()) return false
        val cleanCandidate = candidate.removePrefix("v").removePrefix("V").split("-")[0]
        val cleanCurrent = current.removePrefix("v").removePrefix("V").split("-")[0]

        val candidateParts = cleanCandidate.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(candidateParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val candPart = candidateParts.getOrNull(i) ?: 0
            val currPart = currentParts.getOrNull(i) ?: 0
            if (candPart > currPart) return true
            if (candPart < currPart) return false
        }
        return false
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
    packageInfo?.versionName ?: "1.4.1"

internal fun versionCodeFrom(packageInfo: PackageInfo?, sdkInt: Int = Build.VERSION.SDK_INT): Long {
    if (packageInfo == null) return 7L
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
    val json = JSONObject(responseBody)

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
