package com.inputleaf.android.privilege

import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.inputleaf.android.shizuku.InputInjectorService
import com.topjohnwu.superuser.ipc.RootService

/**
 * Long-lived uid-0 host for the same [InputInjectorService] AIDL stub Shizuku uses.
 * Root's only job is opening `/dev/uhid`; enter/leave stay CREATE2/DESTROY on that stub.
 *
 * Daemon mode keeps the process warm while the app is bound so Enter/Leave never
 * re-fork `su`. Bump [SERVICE_VERSION] if the stub contract changes so a leaked
 * root process is not reused.
 */
class RootInputInjectorService : RootService() {

    private val stub = InputInjectorService()

    override fun onBind(intent: Intent): IBinder {
        android.util.Log.i(
            TAG,
            "onBind version=$SERVICE_VERSION pid=${android.os.Process.myPid()} uid=${android.os.Process.myUid()}",
        )
        return stub
    }

    override fun onUnbind(intent: Intent): Boolean {
        // Daemon stays warm. Leave DESTROY is the client's closeVirtual* AIDL, not unbind.
        android.util.Log.i(TAG, "onUnbind pid=${android.os.Process.myPid()}")
        return true
    }

    override fun onDestroy() {
        stub.destroy()
    }

    companion object {
        private const val TAG = "RootInputInjector"
        // 5: stub now includes attachClient and the 1.4.2 HID lifecycle. A daemon
        // left over from the parked branch (v4) must not be reused.
        const val SERVICE_VERSION = 5
        const val CATEGORY_VERSION = "com.inputleaf.android.root.injector.v$SERVICE_VERSION"

        fun bindIntent(context: Context): Intent {
            return Intent(context, RootInputInjectorService::class.java)
                .addCategory(CATEGORY_VERSION)
                .addCategory(RootService.CATEGORY_DAEMON_MODE)
        }
    }
}
