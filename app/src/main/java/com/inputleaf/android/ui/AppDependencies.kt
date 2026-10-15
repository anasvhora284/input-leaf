package com.inputleaf.android.ui

import android.app.Application
import com.inputleaf.android.network.ServerScanner
import com.inputleaf.android.storage.AppPreferences
import com.inputleaf.android.storage.ClientCertificateStore

/**
 * Lightweight application dependency provider for [MainViewModel] and UI components.
 *
 * Defaults to production implementations and allows instrumentation / smoke tests
 * to substitute deterministic fakes without real network, hardware, or root/Shizuku access.
 */
interface AppDependencies {
    val preferences: AppPreferences
    val certificateStore: ClientCertificateStore
    val serverScanner: ServerScanner
    val permissionProvider: PermissionStatusProvider

    companion object {
        @Volatile
        private var testOverride: ((Application) -> AppDependencies)? = null

        fun from(app: Application): AppDependencies {
            return testOverride?.invoke(app) ?: DefaultAppDependencies(app)
        }

        fun setForTesting(factory: ((Application) -> AppDependencies)?) {
            testOverride = factory
        }

        fun reset() {
            testOverride = null
        }
    }
}

/** Production dependencies used by default. */
class DefaultAppDependencies(app: Application) : AppDependencies {
    override val preferences: AppPreferences = AppPreferences(app)
    override val certificateStore: ClientCertificateStore = ClientCertificateStore(app)
    override val serverScanner: ServerScanner = ServerScanner()
    override val permissionProvider: PermissionStatusProvider = PermissionStatusProvider(app)
}
