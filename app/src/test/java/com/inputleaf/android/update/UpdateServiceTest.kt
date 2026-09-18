package com.inputleaf.android.update

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UpdateServiceTest {

    @Test
    fun isNewerVersion_detectsHigherPatch() {
        assertThat(UpdateService.isNewerVersion("1.4.2", "1.4.1")).isTrue()
        assertThat(UpdateService.isNewerVersion("v1.4.2", "1.4.1")).isTrue()
        assertThat(UpdateService.isNewerVersion("1.4.2", "v1.4.1")).isTrue()
    }

    @Test
    fun isNewerVersion_detectsHigherMinorAndMajor() {
        assertThat(UpdateService.isNewerVersion("1.5.0", "1.4.9")).isTrue()
        assertThat(UpdateService.isNewerVersion("2.0.0", "1.9.9")).isTrue()
    }

    @Test
    fun isNewerVersion_returnsFalseForBlankVersions() {
        assertThat(UpdateService.isNewerVersion("", "1.4.1")).isFalse()
        assertThat(UpdateService.isNewerVersion("1.4.2", "")).isFalse()
        assertThat(UpdateService.isNewerVersion(" ", "1.4.1")).isFalse()
    }

    @Test
    fun isNewerVersion_returnsFalseWhenEqualOrLower() {
        assertThat(UpdateService.isNewerVersion("1.4.1", "1.4.1")).isFalse()
        assertThat(UpdateService.isNewerVersion("v1.4.1", "1.4.1")).isFalse()
        assertThat(UpdateService.isNewerVersion("1.4.0", "1.4.1")).isFalse()
        assertThat(UpdateService.isNewerVersion("1.3.9", "1.4.1")).isFalse()
        assertThat(UpdateService.isNewerVersion("0.9.9", "1.0.0")).isFalse()
    }

    @Test
    fun isNewerVersion_handlesDifferentSegmentCounts() {
        assertThat(UpdateService.isNewerVersion("1.0.1", "1.0")).isTrue()
        assertThat(UpdateService.isNewerVersion("1.0", "1.0.1")).isFalse()
        assertThat(UpdateService.isNewerVersion("2", "1.9.9")).isTrue()
        assertThat(UpdateService.isNewerVersion("1.9", "1.10")).isFalse()
        assertThat(UpdateService.isNewerVersion("1.0.0.5", "1.0.0")).isTrue()
        assertThat(UpdateService.isNewerVersion("1.0.0", "1.0.0.5")).isFalse()
        assertThat(UpdateService.isNewerVersion("1.0", "1.0.0.0")).isFalse()
    }

    @Test
    fun installSourcePackageName_returnsInstallerPackage() {
        val installSourceInfo = mock(InstallSourceInfo::class.java)
        `when`(installSourceInfo.installingPackageName).thenReturn("org.fdroid.fdroid")

        assertThat(installSourcePackageName(installSourceInfo)).isEqualTo("org.fdroid.fdroid")
    }

    @Test
    fun resolveInstallSource_detectsFdroidAndDroidify() {
        assertThat(resolveInstallSource("org.fdroid.fdroid")).isEqualTo(InstallSource.FDROID)
        assertThat(resolveInstallSource("com.aurora.store.droidify")).isEqualTo(InstallSource.FDROID)
    }

    @Test
    fun resolveInstallSource_detectsPlayStore() {
        assertThat(resolveInstallSource("com.android.vending")).isEqualTo(InstallSource.PLAY_STORE)
    }

    @Test
    fun resolveInstallSource_defaultsToGithub() {
        assertThat(resolveInstallSource(null)).isEqualTo(InstallSource.GITHUB)
        assertThat(resolveInstallSource("com.github.android")).isEqualTo(InstallSource.GITHUB)
    }

    @Test
    fun versionNameFrom_usesPackageInfoOrFallback() {
        assertThat(versionNameFrom(PackageInfo().apply { versionName = "2.0.0" })).isEqualTo("2.0.0")
        assertThat(versionNameFrom(null)).isEqualTo("1.4.2")
    }

    @Test
    fun versionCodeFrom_usesPackageInfoOrFallback() {
        val packageInfo = PackageInfo().apply {
            @Suppress("DEPRECATION")
            versionCode = 42
        }
        assertThat(versionCodeFrom(packageInfo)).isEqualTo(42L)
        assertThat(versionCodeFrom(null)).isEqualTo(8L)
    }

    @Test
    fun versionCodeFrom_usesLegacyFieldBelowApi28() {
        val packageInfo = PackageInfo().apply {
            @Suppress("DEPRECATION")
            versionCode = 42
        }
        assertThat(versionCodeFrom(packageInfo, sdkInt = Build.VERSION_CODES.N)).isEqualTo(42L)
    }

    @Test
    fun installerPackageNameForSdk_usesModernLookupOnApi30Plus() {
        val result = installerPackageNameForSdk(
            sdkInt = Build.VERSION_CODES.R,
            modernLookup = { "org.fdroid.fdroid" },
            legacyLookup = { error("legacy should not run") },
        )

        assertThat(result).isEqualTo("org.fdroid.fdroid")
    }

    @Test
    fun installerPackageNameForSdk_usesLegacyLookupBelowApi30() {
        val result = installerPackageNameForSdk(
            sdkInt = Build.VERSION_CODES.Q,
            modernLookup = { error("modern should not run") },
            legacyLookup = { "com.android.vending" },
        )

        assertThat(result).isEqualTo("com.android.vending")
    }

    @Test
    fun installerPackageNameForSdk_returnsNullWhenLookupThrows() {
        val result = installerPackageNameForSdk(
            sdkInt = Build.VERSION_CODES.R,
            modernLookup = { throw IllegalStateException("boom") },
            legacyLookup = { "ignored" },
        )

        assertThat(result).isNull()
    }

    @Test
    fun installerPackageNameForSdk_returnsNullWhenLegacyLookupThrows() {
        val result = installerPackageNameForSdk(
            sdkInt = Build.VERSION_CODES.Q,
            modernLookup = { "ignored" },
            legacyLookup = { throw IllegalStateException("boom") },
        )

        assertThat(result).isNull()
    }

    @Test
    fun packageInfoForSdk_usesModernLookupOnApi33Plus() {
        val expected = PackageInfo().apply { versionName = "modern" }
        val result = packageInfoForSdk(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            modernLookup = { expected },
            legacyLookup = { error("legacy should not run") },
        )

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun packageInfoForSdk_usesLegacyLookupBelowApi33() {
        val expected = PackageInfo().apply { versionName = "legacy" }
        val result = packageInfoForSdk(
            sdkInt = Build.VERSION_CODES.S,
            modernLookup = { error("modern should not run") },
            legacyLookup = { expected },
        )

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun packageInfoForSdk_returnsNullWhenLookupThrows() {
        val result = packageInfoForSdk(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            modernLookup = { throw IllegalStateException("boom") },
            legacyLookup = { PackageInfo() },
        )

        assertThat(result).isNull()
    }

    @Test
    fun packageInfoForSdk_returnsNullWhenLegacyLookupThrows() {
        val result = packageInfoForSdk(
            sdkInt = Build.VERSION_CODES.S,
            modernLookup = { PackageInfo() },
            legacyLookup = { throw IllegalStateException("boom") },
        )

        assertThat(result).isNull()
    }

    @Test
    fun httpErrorResult_formatsMessage() {
        assertThat(httpErrorResult(404).message).isEqualTo("HTTP error 404 from GitHub")
    }

    @Test
    fun parseLatestReleaseResponse_returnsUpdateAvailableForGithub() {
        val body = """
            {
              "tag_name": "v1.5.0",
              "body": "New features",
              "html_url": "https://github.com/anasvhora284/input-leaf/releases/tag/v1.5.0"
            }
        """.trimIndent()

        val result = parseLatestReleaseResponse(body, currentVersion = "1.4.1", isFdroid = false)

        assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        val update = result as UpdateCheckResult.UpdateAvailable
        assertThat(update.latestVersion).isEqualTo("1.5.0")
        assertThat(update.changelog).isEqualTo("New features")
        assertThat(update.updateUrl).contains("github.com")
        assertThat(update.isFdroid).isFalse()
    }

    @Test
    fun parseLatestReleaseResponse_usesFdroidMarketUrl() {
        val body = """{"tag_name":"v2.0.0","body":"","html_url":""}"""

        val result = parseLatestReleaseResponse(body, currentVersion = "1.0.0", isFdroid = true)

        assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        val update = result as UpdateCheckResult.UpdateAvailable
        assertThat(update.updateUrl).isEqualTo(UpdateService.FDROID_MARKET_URI)
        assertThat(update.isFdroid).isTrue()
    }

    @Test
    fun parseLatestReleaseResponse_returnsUpToDateWhenNotNewer() {
        val body = """{"tag_name":"v1.4.1","body":"Same version","html_url":""}"""

        val result = parseLatestReleaseResponse(body, currentVersion = "1.4.1", isFdroid = false)

        assertThat(result).isEqualTo(UpdateCheckResult.UpToDate("1.4.1"))
    }

    @Test
    fun checkUpdate_returnsHttpError() = runTest {
        val connection = TestHttpURLConnection(URL("http://example.com"), responseCode = 500)

        val result = UpdateService.checkUpdate(
            currentVersion = "1.4.1",
            isFdroid = false,
            openConnection = { connection },
        )

        assertThat(result).isEqualTo(UpdateCheckResult.Error("HTTP error 500 from GitHub"))
        assertThat(connection.disconnectCalled).isTrue()
    }

    @Test
    fun checkUpdate_returnsParsedRelease() = runTest {
        val body = """{"tag_name":"v9.9.9","body":"Big release","html_url":"https://example.com"}"""
        val connection = TestHttpURLConnection(
            url = URL("http://example.com"),
            responseBody = body,
        )

        val result = UpdateService.checkUpdate(
            currentVersion = "1.0.0",
            isFdroid = false,
            openConnection = { connection },
        )

        assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        assertThat((result as UpdateCheckResult.UpdateAvailable).latestVersion).isEqualTo("9.9.9")
        assertThat(connection.disconnectCalled).isTrue()
    }

    @Test
    fun checkUpdate_returnsErrorWhenConnectionThrows() = runTest {
        val result = UpdateService.checkUpdate(
            currentVersion = "1.0.0",
            isFdroid = false,
            openConnection = { throw IllegalStateException("network down") },
        )

        assertThat(result).isEqualTo(UpdateCheckResult.Error("network down"))
    }

    @Test
    fun checkUpdate_returnsFallbackMessageWhenExceptionHasNoMessage() = runTest {
        val result = UpdateService.checkUpdate(
            currentVersion = "1.0.0",
            isFdroid = false,
            openConnection = { throw RuntimeException() },
        )

        assertThat(result).isEqualTo(UpdateCheckResult.Error("Failed to check for updates"))
    }

    @Test
    fun checkUpdate_returnsErrorWhenReadingBodyThrows() = runTest {
        val connection = TestHttpURLConnection(
            url = URL("http://example.com"),
            throwOnInputStream = true,
        )

        val result = UpdateService.checkUpdate(
            currentVersion = "1.0.0",
            isFdroid = false,
            openConnection = { connection },
        )

        assertThat(result).isEqualTo(UpdateCheckResult.Error("read failed"))
        assertThat(connection.disconnectCalled).isTrue()
    }

    @Test
    fun checkUpdate_returnsFdroidParsedRelease() = runTest {
        val connection = TestHttpURLConnection(
            url = URL("http://example.com"),
            responseBody = """{"tag_name":"v2.0.0","body":"F-Droid release","html_url":""}""",
        )

        val result = UpdateService.checkUpdate(
            currentVersion = "1.0.0",
            isFdroid = true,
            openConnection = { connection },
        )

        assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        val update = result as UpdateCheckResult.UpdateAvailable
        assertThat(update.updateUrl).isEqualTo(UpdateService.FDROID_MARKET_URI)
        assertThat(update.isFdroid).isTrue()
    }

    @Test
    fun checkUpdate_returnsFdroidUpdateThroughContext() = runTest {
        val context = mockFdroidContext(versionName = "1.0.0")
        val connection = TestHttpURLConnection(
            url = URL("http://example.com"),
            responseBody = """{"tag_name":"v2.0.0","body":"F-Droid release","html_url":""}""",
        )

        val result = UpdateService.checkUpdate(context, openConnection = { connection })

        assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        val update = result as UpdateCheckResult.UpdateAvailable
        assertThat(update.updateUrl).isEqualTo(UpdateService.FDROID_MARKET_URI)
        assertThat(update.isFdroid).isTrue()
        assertThat(connection.disconnectCalled).isTrue()
    }

    @Test
    fun readLegacyInstallerPackageName_readsInstallerFromPackageManager() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(context.packageName).thenReturn("com.inputleaf.android")
        doReturn("org.fdroid.fdroid").`when`(packageManager)
            .getInstallerPackageName("com.inputleaf.android")

        assertThat(readLegacyInstallerPackageName(context)).isEqualTo("org.fdroid.fdroid")
    }

    @Test
    fun readLegacyPackageInfo_readsInstalledPackageInfo() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        val packageInfo = PackageInfo().apply { versionName = "legacy" }
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(context.packageName).thenReturn("com.inputleaf.android")
        doReturn(packageInfo).`when`(packageManager)
            .getPackageInfo("com.inputleaf.android", 0)

        assertThat(readLegacyPackageInfo(context).versionName).isEqualTo("legacy")
    }

    @Test
    fun readModernInstallerPackageName_readsInstallSourceInfo() {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        val installSourceInfo = mock(InstallSourceInfo::class.java)
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(context.packageName).thenReturn("com.inputleaf.android")
        `when`(packageManager.getInstallSourceInfo("com.inputleaf.android")).thenReturn(installSourceInfo)
        `when`(installSourceInfo.installingPackageName).thenReturn("com.android.vending")

        assertThat(readModernInstallerPackageName(context)).isEqualTo("com.android.vending")
    }

    private fun mockFdroidContext(versionName: String): Context {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)
        val installSourceInfo = mock(InstallSourceInfo::class.java)
        val packageInfo = PackageInfo().apply { this.versionName = versionName }

        `when`(context.packageManager).thenReturn(packageManager)
        `when`(context.packageName).thenReturn("com.inputleaf.android")
        `when`(installSourceInfo.installingPackageName).thenReturn("org.fdroid.fdroid")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            `when`(packageManager.getInstallSourceInfo("com.inputleaf.android")).thenReturn(installSourceInfo)
            `when`(
                packageManager.getPackageInfo(
                    eq("com.inputleaf.android"),
                    any(PackageManager.PackageInfoFlags::class.java),
                )
            ).thenReturn(packageInfo)
        } else {
            doReturn("org.fdroid.fdroid").`when`(packageManager)
                .getInstallerPackageName("com.inputleaf.android")
            doReturn(packageInfo).`when`(packageManager)
                .getPackageInfo("com.inputleaf.android", 0)
        }

        return context
    }

    @Test
    fun changelogProvider_returnsValidHighlights() {
        val changelog = UpdateService.getChangelog("1.4.2")
        assertThat(changelog.versionName).isEqualTo("1.4.2")
        assertThat(changelog.highlights).isNotEmpty()
    }

    @Test
    fun changelogProvider_fallsBackToLatestKnownRelease() {
        val changelog = UpdateService.getChangelog("9.9.9")
        assertThat(changelog.versionName).isEqualTo("1.4.2")
    }

    @Test
    fun changelogProvider_stripsVersionPrefix() {
        val changelog = UpdateService.getChangelog("v1.4.0")
        assertThat(changelog.versionName).isEqualTo("1.4.0")
    }

    @Test
    fun defaultConnectionOpener_configuresGitHubRequest() {
        val connection = UpdateService.defaultConnectionOpener(URL("http://example.com"))

        assertThat(connection.requestMethod).isEqualTo("GET")
        assertThat(connection.getRequestProperty("Accept")).isEqualTo("application/vnd.github.v3+json")
        assertThat(connection.getRequestProperty("User-Agent")).isEqualTo("InputLeaf-Android")
    }

    private class TestHttpURLConnection(
        url: URL,
        private val responseCode: Int = HTTP_OK,
        private val responseBody: String = "",
        private val throwOnInputStream: Boolean = false,
    ) : HttpURLConnection(url) {
        var disconnectCalled = false

        override fun disconnect() {
            disconnectCalled = true
        }

        override fun connect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getRequestMethod(): String = "GET"

        override fun getResponseCode(): Int = responseCode

        override fun getResponseMessage(): String = "OK"

        override fun getInputStream() =
            if (throwOnInputStream) {
                throw IOException("read failed")
            } else {
                ByteArrayInputStream(responseBody.toByteArray())
            }

        override fun getErrorStream() = null

        override fun setRequestMethod(method: String) = Unit

        override fun getInstanceFollowRedirects(): Boolean = false

        override fun setInstanceFollowRedirects(followRedirects: Boolean) = Unit

        override fun getConnectTimeout(): Int = 0

        override fun setConnectTimeout(timeout: Int) = Unit

        override fun getReadTimeout(): Int = 0

        override fun setReadTimeout(timeout: Int) = Unit
    }
}
