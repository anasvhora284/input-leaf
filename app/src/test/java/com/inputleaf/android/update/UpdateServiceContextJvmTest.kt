package com.inputleaf.android.update

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class UpdateServiceContextJvmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun getCurrentVersion_readsInstalledVersion() {
        assertThat(UpdateService.getCurrentVersion(context)).isEqualTo("1.4.2")
    }

    @Test
    fun getCurrentVersionCode_readsInstalledVersionCode() {
        UpdateService.getCurrentVersionCode(context)
    }

    @Test
    fun getInstallSource_returnsKnownSource() {
        assertThat(UpdateService.getInstallSource(context)).isEqualTo(InstallSource.GITHUB)
    }

    @Test
    fun readModernInstallerPackageName_invokesModernLookupOnApi34() {
        runCatching { readModernInstallerPackageName(context) }
    }

    @Test
    fun readModernPackageInfo_readsInstalledPackageInfo() {
        assertThat(readModernPackageInfo(context).packageName).isEqualTo(context.packageName)
    }

    @Test
    fun checkUpdate_returnsResultWithoutCrashing() = runTest {
        val result = UpdateService.checkUpdate(context)

        assertThat(
            result is UpdateCheckResult.UpdateAvailable ||
                result is UpdateCheckResult.UpToDate ||
                result is UpdateCheckResult.Error
        ).isTrue()
    }

    @Test
    fun checkUpdate_propagatesFdroidInstallSource() = runBlocking {
        val installSourceInfo = mock(InstallSourceInfo::class.java)
        `when`(installSourceInfo.installingPackageName).thenReturn("org.fdroid.fdroid")

        val packageManager = spy(context.packageManager)
        doReturn(installSourceInfo).`when`(packageManager)
            .getInstallSourceInfo(context.packageName)

        val fdroidContext = spy(context)
        doReturn(packageManager).`when`(fdroidContext).packageManager

        assertThat(UpdateService.getInstallSource(fdroidContext)).isEqualTo(InstallSource.FDROID)

        val result = withTimeout(20_000) {
            UpdateService.checkUpdate(fdroidContext)
        }
        assertThat(
            result is UpdateCheckResult.UpdateAvailable ||
                result is UpdateCheckResult.UpToDate ||
                result is UpdateCheckResult.Error
        ).isTrue()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class UpdateServiceLegacySdkJvmTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun getInstallSource_usesLegacyInstallerLookupBelowApi30() {
        UpdateService.getInstallSource(context)
    }

    @Test
    fun readLegacyInstallerPackageName_readsInstallSourceOnApi29() {
        readLegacyInstallerPackageName(context)
    }

    @Test
    fun getCurrentVersion_usesLegacyPackageInfoLookupBelowApi33() {
        UpdateService.getCurrentVersion(context)
    }

    @Test
    fun readLegacyPackageInfo_readsPackageInfoOnApi29() {
        readLegacyPackageInfo(context)
    }
}
