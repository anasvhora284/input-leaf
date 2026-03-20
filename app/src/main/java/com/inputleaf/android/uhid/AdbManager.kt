package com.inputleaf.android.uhid

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AdbManager"
private const val DEX_REMOTE_PATH = "/data/local/tmp/inputleaf-uhid.dex"

// dexLocalPath: copy from app assets to cache dir before calling deployAndStart()
// e.g. context.assets.open("inputleaf-uhid.dex").use { Files.copy(it, cacheFile.toPath()) }
class AdbManager(private val dexLocalPath: String) {

    suspend fun deployAndStart(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Push DEX
            val push = Runtime.getRuntime().exec(
                arrayOf("adb", "push", dexLocalPath, DEX_REMOTE_PATH))
            if (push.waitFor() != 0) {
                Log.e(TAG, "adb push failed: ${push.errorStream.bufferedReader().readText()}")
                return@withContext false
            }
            // Start UHID server
            Runtime.getRuntime().exec(
                arrayOf("adb", "shell",
                    "CLASSPATH=$DEX_REMOTE_PATH app_process /data/local/tmp com.inputleaf.uhid.Main &"))
            // Poll stdout for "READY" up to 10 seconds
            withTimeoutOrNull(10_000L) {
                while (true) {
                    val check = Runtime.getRuntime().exec(
                        arrayOf("adb", "shell", "echo ping"))
                    if (check.waitFor() == 0) return@withTimeoutOrNull true
                    kotlinx.coroutines.delay(500)
                }
            } != null
        } catch (e: Exception) {
            Log.e(TAG, "ADB deployment failed: ${e.message}")
            false
        }
    }

    fun buildAdbPushCommand(): String =
        "adb push <app_data_dir>/inputleaf-uhid.dex $DEX_REMOTE_PATH"

    fun buildAdbStartCommand(): String =
        "adb shell CLASSPATH=$DEX_REMOTE_PATH app_process /data/local/tmp com.inputleaf.uhid.Main"
}
