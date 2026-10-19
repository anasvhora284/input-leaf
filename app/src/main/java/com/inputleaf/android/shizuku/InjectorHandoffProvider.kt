package com.inputleaf.android.shizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log

/**
 * Receives the injector binder from [StandaloneInjectorMain].
 *
 * Exported so the shell process can reach it, which means any app could call it too;
 * only root and shell are trusted to hand over an injector.
 */
class InjectorHandoffProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        appContext = context?.applicationContext
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != InjectorHandoff.METHOD_ATTACH) return null
        val uid = Binder.getCallingUid()
        if (uid != ROOT_UID && uid != SHELL_UID) {
            Log.w(TAG, "Rejected injector handoff from uid $uid")
            return null
        }
        val injector = extras?.getBinder(InjectorHandoff.KEY_INJECTOR) ?: return null
        StandaloneInjectorLauncher.onInjectorAttached(injector)
        return Bundle().apply { putBinder(InjectorHandoff.KEY_CLIENT, StandaloneInjectorLauncher.clientToken) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "InjectorHandoff"
        private const val ROOT_UID = 0
        private const val SHELL_UID = 2000

        /** Set as the app process starts; providers are created before Application.onCreate. */
        @Volatile
        var appContext: Context? = null
            private set

        fun authority(context: Context): String = "${context.packageName}.injector"

        internal fun clearAppContextForTest() {
            appContext = null
        }
    }
}
