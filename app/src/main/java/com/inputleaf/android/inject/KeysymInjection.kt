package com.inputleaf.android.inject

import android.view.KeyEvent

internal object KeysymInjection {
    fun applyKeyEventAction(
        action: KeysymAction.KeyEventAction,
        isDown: Boolean,
        metaState: Int,
        onMetaStateChanged: (Int) -> Unit,
        injectKeyEvent: (Int, Int, Int) -> Unit,
    ) {
        val keyEventAction = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val updatedMetaState = if (isDown) {
            KeyMapUtils.updateMetaState(action.keyCode, true, metaState)
        } else {
            metaState
        }
        onMetaStateChanged(updatedMetaState)
        injectKeyEvent(keyEventAction, action.keyCode, updatedMetaState)
        if (!isDown) {
            onMetaStateChanged(KeyMapUtils.updateMetaState(action.keyCode, false, updatedMetaState))
        }
    }
}
