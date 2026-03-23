// IInputInjector.aidl
package com.inputleaf.android.shizuku;

/**
 * AIDL interface for injecting input events via Shizuku.
 * This service runs with shell (ADB) privileges and can call InputManager.injectInputEvent().
 */
interface IInputInjector {
    /**
     * Inject a mouse/touch motion event.
     * @param action MotionEvent action (ACTION_DOWN, ACTION_MOVE, ACTION_UP, etc.)
     * @param x X coordinate
     * @param y Y coordinate  
     * @param buttonState Mouse button state (BUTTON_PRIMARY, BUTTON_SECONDARY, etc.)
     * @return true if injection succeeded
     */
    boolean injectMotionEvent(int action, float x, float y, int buttonState);
    
    /**
     * Inject a scroll event (mouse wheel).
     * @param x X coordinate
     * @param y Y coordinate
     * @param hScroll Horizontal scroll amount (-1 to 1)
     * @param vScroll Vertical scroll amount (-1 to 1)
     * @return true if injection succeeded
     */
    boolean injectScrollEvent(float x, float y, float hScroll, float vScroll);
    
    /**
     * Inject a key event (keyboard).
     * @param action KeyEvent action (ACTION_DOWN or ACTION_UP)
     * @param keyCode Android KeyEvent keycode
     * @param scanCode Hardware scan code (Linux input event code, e.g. 125 for KEY_LEFTMETA)
     * @param metaState Modifier state (CTRL, ALT, SHIFT, etc.)
     * @return true if injection succeeded
     */
    boolean injectKeyEvent(int action, int keyCode, int scanCode, int metaState);
    
    /**
     * Destroy the service and release resources.
     */
    void destroy();
}
