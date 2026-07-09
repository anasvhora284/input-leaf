package com.inputleaf.android.inject

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

class InputLeafIME : InputMethodService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "InputLeafIME onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "InputLeafIME onDestroy")
    }

    fun injectKeyEvent(action: Int, keyCode: Int, metaState: Int) {
        val ic = currentInputConnection
        if (ic == null) {
            Log.w(TAG, "injectKeyEvent: No InputConnection available")
            return
        }

        val eventTime = SystemClock.uptimeMillis()
        val event = KeyEvent(
            eventTime, eventTime,
            action, keyCode, 0, metaState,
            -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        
        ic.sendKeyEvent(event)
    }

    companion object {
        private const val TAG = "InputLeafIME"
        private var instance: InputLeafIME? = null

        fun getInstance(): InputLeafIME? {
            return instance
        }
        
        fun isRunning(): Boolean {
            return instance != null
        }
    }
}
