package com.inputleaf.android.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.inputleaf.android.shizuku.InputInjectorService
import com.inputleaf.android.shizuku.ShizukuInputInjector
import com.topjohnwu.superuser.ipc.RootService
import rikka.shizuku.Shizuku

/**
 * Starts the privileged [com.inputleaf.android.shizuku.IInputInjector] process.
 * After bind, enter/leave/HID reports are the same AIDL calls regardless of [kind].
 */
internal interface PrivilegedUserServiceHost {
    val kind: PrivilegeKind
    val displayName: String
    val bindTimeoutMs: Long

    /** How many times [bind] is worth retrying before the caller gives up. */
    val bindAttempts: Int get() = 3
    fun isAvailable(): Boolean

    /** @return false when the bind could not even be started, so the caller fails fast. */
    fun bind(connection: ServiceConnection): Boolean
    fun unbind(connection: ServiceConnection, destroy: Boolean)
}

internal class ShizukuUserServiceHost : PrivilegedUserServiceHost {
    override val kind: PrivilegeKind = PrivilegeKind.SHIZUKU
    override val displayName: String = "Shizuku (ADB-level injection)"
    override val bindTimeoutMs: Long = 10_000L

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "com.inputleaf.android",
            InputInjectorService::class.java.name,
        ),
    ).daemon(false).processNameSuffix("input_injector").version(SHIZUKU_SERVICE_VERSION)

    override fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    override fun bind(connection: ServiceConnection): Boolean {
        return runCatching { Shizuku.bindUserService(serviceArgs, connection) }.isSuccess
    }

    override fun unbind(connection: ServiceConnection, destroy: Boolean) {
        Shizuku.unbindUserService(serviceArgs, connection, destroy)
    }

    companion object {
        // Bumped for the attachClient and Enter-warp AIDL additions: a cached older
        // UserService does not implement them and would throw on every bind.
        private const val SHIZUKU_SERVICE_VERSION = 6
    }
}

internal class RootUserServiceHost(
    context: Context,
    private val rootAccess: RootAccess = LibSuRootAccess,
) : PrivilegedUserServiceHost {
    override val kind: PrivilegeKind = PrivilegeKind.ROOT
    override val displayName: String = "Root (physical HID devices)"
    // One su fork plus a root process start; three 60s attempts would freeze connect()
    // for three minutes on a device where root is simply never granted.
    override val bindTimeoutMs: Long = 30_000L
    override val bindAttempts: Int = 2

    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bindIntent = RootInputInjectorService.bindIntent(appContext)

    override fun isAvailable(): Boolean = rootAccess.availability().canUse()

    override fun bind(connection: ServiceConnection): Boolean {
        // RootService.bind must run on the main thread and throws there on a bad intent,
        // which would crash the app instead of failing this attempt.
        runOnMain {
            runCatching { RootService.bind(bindIntent, connection) }
                .onFailure { android.util.Log.w("RootUserServiceHost", "RootService.bind failed", it) }
        }
        return true
    }

    override fun unbind(connection: ServiceConnection, destroy: Boolean) {
        runOnMain {
            runCatching { RootService.unbind(connection) }
            if (destroy) {
                runCatching { RootService.stop(bindIntent) }
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

internal object PrivilegedInjectorFactory {
    fun create(
        context: Context,
        screenWidth: Int,
        screenHeight: Int,
        snapshot: PrivilegeSnapshot = currentSnapshot(),
        method: String = "auto",
        rootAccess: RootAccess = LibSuRootAccess,
    ): ShizukuInputInjector {
        val host = hostFor(context, kindFor(method, snapshot), rootAccess)
        return ShizukuInputInjector(screenWidth, screenHeight, host)
    }

    fun kindFor(method: String, snapshot: PrivilegeSnapshot): PrivilegeKind {
        return when (InjectorMethodResolver.resolve(method, snapshot, accessibilityAvailable = false)) {
            ResolvedInjector.PRIVILEGED_ROOT -> PrivilegeKind.ROOT
            ResolvedInjector.PRIVILEGED_SHIZUKU -> PrivilegeKind.SHIZUKU
            // Falling back to PrivilegeSelector here would turn an explicit "shizuku"
            // preference into ROOT and pop an su prompt the user never asked for.
            ResolvedInjector.ACCESSIBILITY, ResolvedInjector.NONE -> PrivilegeKind.NONE
        }
    }

    fun hostFor(
        context: Context,
        kind: PrivilegeKind,
        rootAccess: RootAccess = LibSuRootAccess,
    ): PrivilegedUserServiceHost {
        return when (kind) {
            PrivilegeKind.SHIZUKU -> ShizukuUserServiceHost()
            PrivilegeKind.ROOT -> RootUserServiceHost(context, rootAccess)
            PrivilegeKind.NONE -> ShizukuUserServiceHost()
        }
    }

    fun currentSnapshot(rootAccess: RootAccess = LibSuRootAccess): PrivilegeSnapshot {
        return PrivilegeSnapshot(
            shizukuReady = ShizukuUserServiceHost().isAvailable(),
            root = rootAccess.availability(),
        )
    }
}
