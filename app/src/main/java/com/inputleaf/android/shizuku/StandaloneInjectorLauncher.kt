package com.inputleaf.android.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Starts [StandaloneInjectorMain] through Shizuku and reports its binder through a
 * [ServiceConnection], so [ShizukuInputInjector] cannot tell it from a UserService.
 *
 * One injector process per app process, like the UserService it replaces. A warm
 * injector is handed straight to the next connection instead of re-forking.
 */
internal object StandaloneInjectorLauncher {
    private const val TAG = "StandaloneInjector"

    /** Owned by this process; the injector exits when it dies. */
    val clientToken: IBinder = Binder()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var process: Process? = null
    private var injector: IBinder? = null
    private var connection: ServiceConnection? = null

    fun bind(conn: ServiceConnection): Boolean {
        val context = InjectorHandoffProvider.appContext ?: run {
            Log.e(TAG, "Handoff provider not created; cannot start injector")
            return false
        }
        val warm = synchronized(lock) {
            connection = conn
            injector?.takeIf { it.pingBinder() }
        }
        if (warm != null) {
            mainHandler.post { conn.onServiceConnected(componentName(context.packageName), warm) }
            return true
        }
        val authority = InjectorHandoffProvider.authority(context)
        val userId = android.os.Process.myUid() / PER_USER_RANGE
        val command = "CLASSPATH='${context.applicationInfo.sourceDir}' exec app_process /system/bin " +
            "--nice-name=${context.packageName}:input_injector " +
            "${StandaloneInjectorMain::class.java.name} $authority $userId"
        // Check and launch under one lock so concurrent binds share a single injector
        // instead of racing two privileged processes for the HID devices. Only the
        // fork happens here; the handoff arrives later on a binder thread.
        synchronized(lock) {
            if (process?.isAlive == true) return true
            return try {
                val started = newProcess(arrayOf("sh", "-c", command))
                drain(started)
                process = started
                Log.i(TAG, "Started standalone injector")
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Could not start standalone injector", e)
                false
            }
        }
    }

    fun unbind(conn: ServiceConnection, destroy: Boolean) {
        synchronized(lock) {
            if (connection === conn) connection = null
            if (!destroy) return
            process?.destroy()
            process = null
            injector = null
        }
    }

    fun onInjectorAttached(binder: IBinder) {
        val conn = synchronized(lock) {
            injector = binder
            connection
        }
        try {
            binder.linkToDeath({ onInjectorDied(binder) }, 0)
        } catch (e: Throwable) {
            onInjectorDied(binder)
            return
        }
        val packageName = InjectorHandoffProvider.appContext?.packageName ?: return
        Log.i(TAG, "Injector attached")
        mainHandler.post { conn?.onServiceConnected(componentName(packageName), binder) }
    }

    private fun onInjectorDied(binder: IBinder) {
        val conn = synchronized(lock) {
            if (injector !== binder) return
            injector = null
            process = null
            connection
        }
        Log.w(TAG, "Injector process died")
        val packageName = InjectorHandoffProvider.appContext?.packageName ?: return
        mainHandler.post { conn?.onServiceDisconnected(componentName(packageName)) }
    }

    private fun componentName(packageName: String) =
        ComponentName(packageName, StandaloneInjectorMain::class.java.name)

    /**
     * `Shizuku.newProcess` is private since API 13 in favour of UserService, which is
     * exactly what fails here. The server side is unchanged, and still kills the process
     * if this app dies.
     */
    private fun newProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }

    /** Keeps the pipes from filling up and surfaces app_process startup errors in logcat. */
    private fun drain(process: Process) {
        for ((stream, name) in listOf(process.inputStream to "stdout", process.errorStream to "stderr")) {
            Thread({
                runCatching {
                    stream.bufferedReader().forEachLine { Log.i(TAG, "[$name] $it") }
                }
            }, "standalone-injector-$name").apply { isDaemon = true }.start()
        }
    }

    private const val PER_USER_RANGE = 100_000
}
