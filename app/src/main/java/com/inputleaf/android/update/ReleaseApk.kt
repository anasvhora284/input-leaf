package com.inputleaf.android.update

import org.json.JSONObject

/** One APK attached to a GitHub release. */
data class ReleaseApk(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    /** Lower-case hex SHA-256 from GitHub's asset digest, or null when GitHub gave none. */
    val sha256: String?,
)

/**
 * Picks the release APK to download in the app instead of sending the user to GitHub.
 *
 * Release files are named `input-leaf_<version>_<abi>.apk` (see docs/RELEASING.md). The
 * device's preferred ABI wins, then any other ABI it supports, then `universal`. Only
 * HTTPS downloads from this repository's releases are accepted, since the release JSON
 * decides what gets installed.
 */
object ReleaseApkPicker {
    internal const val DOWNLOAD_PREFIX = "https://github.com/anasvhora284/input-leaf/releases/download/"

    fun parseAssets(release: JSONObject): List<ReleaseApk> {
        val assets = release.optJSONArray("assets") ?: return emptyList()
        return (0 until assets.length()).mapNotNull { i ->
            val asset = assets.optJSONObject(i) ?: return@mapNotNull null
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (!name.endsWith(".apk") || !url.startsWith(DOWNLOAD_PREFIX)) return@mapNotNull null
            ReleaseApk(
                name = name,
                url = url,
                sizeBytes = asset.optLong("size", -1L),
                sha256 = asset.optString("digest").removePrefix("sha256:")
                    .lowercase().takeIf { it.matches(SHA256_HEX) },
            )
        }
    }

    /** @param supportedAbis the device's ABIs, most preferred first (Build.SUPPORTED_ABIS). */
    fun pick(apks: List<ReleaseApk>, version: String, supportedAbis: List<String>): ReleaseApk? {
        val byName = apks.associateBy { it.name }
        return (supportedAbis + "universal").firstNotNullOfOrNull { abi -> byName["input-leaf_${version}_$abi.apk"] }
    }

    private val SHA256_HEX = Regex("[0-9a-f]{64}")
}

/** Pure rules for whether a downloaded APK may replace this app. */
internal object UpdateVerification {
    fun problem(
        archivePackage: String,
        archiveVersionCode: Long,
        archiveSigners: Set<String>?,
        installedPackage: String,
        installedVersionCode: Long,
        installedSigners: Set<String>?,
    ): String? = when {
        archivePackage != installedPackage -> "The download is not Input Leaf"
        archiveVersionCode <= installedVersionCode -> "The download is not newer than this version"
        // Older Android may not report an archive's signers; the installer still enforces them.
        archiveSigners != null && installedSigners != null && archiveSigners != installedSigners ->
            "The download is not signed by Input Leaf's release key"
        else -> null
    }
}
