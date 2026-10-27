package com.inputleaf.android.inject

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class InputLeafIME : InputMethodService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "InputLeafIME onCreate")
    }

    /**
     * Selected while no Accessibility session wants it: left over from a crash, a killed
     * app or a manual pick. Give the user's keyboard back before they have to type.
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!ImeSwitcher.wanted) {
            Log.i(TAG, "Selected without a session; handing back")
            handBack(ImeSwitcher.previousId(this))
        }
    }

    /** Switch away from this keyboard, to [targetId] or else the previous one. */
    fun handBack(targetId: String?): Boolean {
        val main = Handler(Looper.getMainLooper())
        main.post {
            runCatching {
                when {
                    targetId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> switchInputMethod(targetId)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> switchToPreviousInputMethod()
                    else -> {
                        @Suppress("DEPRECATION")
                        getSystemService(InputMethodManager::class.java)
                            .switchToLastInputMethod(window?.window?.attributes?.token)
                    }
                }
            }.onFailure { Log.w(TAG, "Could not hand the keyboard back", it) }
        }
        return true
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

    fun commitText(text: String) {
        val ic = currentInputConnection
        if (ic == null) {
            Log.w(TAG, "commitText: No InputConnection available")
            return
        }
        ic.commitText(text, 1)
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
