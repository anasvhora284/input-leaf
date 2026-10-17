package com.inputleaf.android.shizuku

import android.os.Looper
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/**
 * Hosts [InputInjectorService] in a bare `app_process` when Shizuku's UserService
 * starter cannot.
 *
 * The starter loads the app through `LoadedApk.makeApplication()`, which calls
 * `IActivityManager.addPackageDependency()`. Onyx Boox firmware (Android 12) throws an
 * NPE there for any caller without a ProcessRecord, so the UserService dies before any
 * of our code runs and every bind just times out. Started as `app_process` with the APK
 * on CLASSPATH, this needs no Context: it builds the stub and hands its binder to the
 * app through [InjectorHandoffProvider].
 */
object StandaloneInjectorMain {
    private const val TAG = "StandaloneInjector"

    @JvmStatic
    fun main(args: Array<String>) {
        val authority = args.getOrNull(0)
        if (authority == null) {
            Log.e(TAG, "Usage: StandaloneInjectorMain <handoff authority> [userId]")
            exitProcess(2)
        }
        val userId = args.getOrNull(1)?.toIntOrNull() ?: 0

        @Suppress("DEPRECATION")
        Looper.prepareMainLooper()
        val stub = InputInjectorService()

        val client = try {
            InjectorHandoff.deliver(authority, userId, stub)
        } catch (e: Throwable) {
            Log.e(TAG, "Could not hand the injector to $authority", e)
            exitProcess(1)
        }

        // Same lifetime as a UserService: nothing is left to serve once the app is gone,
        // and the UHID devices must not outlive it.
        try {
            client.linkToDeath({
                Log.i(TAG, "Client died; exiting")
                stub.destroy()
                exitProcess(0)
            }, 0)
        } catch (e: Throwable) {
            Log.w(TAG, "Client already dead at handoff", e)
            stub.destroy()
            exitProcess(0)
        }

        Log.i(TAG, "Injector ready pid=${Process.myPid()} uid=${Process.myUid()}")
        Looper.loop()
    }
}
