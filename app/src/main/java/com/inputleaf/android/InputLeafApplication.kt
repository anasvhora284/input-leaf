package com.inputleaf.android

import android.app.Application
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class InputLeafApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("InputLeafCrash", "Uncaught exception in thread ${thread.name}", throwable)
            
            // Try to show a toast, though it might not always work if the main thread is dead.
            try {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Input Leaf recovered from a crash.", Toast.LENGTH_LONG).show()
                }
                Thread.sleep(1500)
            } catch (e: Exception) {
                // Ignore
            }
            
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
