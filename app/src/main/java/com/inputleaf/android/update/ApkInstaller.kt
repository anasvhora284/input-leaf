package com.inputleaf.android.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "ApkInstaller"

/**
 * Installs a downloaded update through Android's PackageInstaller. Android always shows
 * its own confirmation, and on first use its own "allow this source" prompt, so the app
 * only checks the file first and hands it over.
 */
object ApkInstaller {
    /** What the installer last reported, for the update dialog. */
    sealed interface Status {
        data object Idle : Status
        data object Installing : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    /**
     * @return null when [apk] is a newer Input Leaf signed with the same key as this one,
     * else why it must not be installed. Android rejects a wrong signer anyway; checking
     * first gives a clear message instead of a generic install failure.
     */
    fun verify(context: Context, apk: File): String? {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return "The downloaded file is not a valid app"
        val installed = runCatching { installedInfo(pm, context.packageName) }.getOrNull()
            ?: return "Could not read the installed version"
        return UpdateVerification.problem(
            archivePackage = archive.packageName,
            archiveVersionCode = versionCodeFrom(archive),
            archiveSigners = signers(archive),
            installedPackage = context.packageName,
            installedVersionCode = versionCodeFrom(installed),
            installedSigners = signers(installed),
        )
    }

    fun install(context: Context, apk: File) {
        _status.value = Status.Installing
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Install could not start", e)
            _status.value = Status.Failed(e.message ?: "Install could not start")
        }
    }

    internal fun onResult(context: Context, intent: Intent) {
        when (val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            // On success Android replaces this app and restarts it, so there is nothing to show.
            PackageInstaller.STATUS_SUCCESS -> _status.value = Status.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _status.value = Status.Idle // user tapped Cancel
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "Install failed: $code $message")
                _status.value = Status.Failed(message ?: "Install failed")
            }
        }
    }

    fun reset() {
        _status.value = Status.Idle
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)
        }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }

    /** Signing certificates as hex, or null when the platform did not report them. */
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String>? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures?.takeIf { it.isNotEmpty() }?.map { it.toCharsString() }?.toSet()
    }
}

/** Receives PackageInstaller's progress for a session started by [ApkInstaller.install]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ApkInstaller.onResult(context, intent)
}
