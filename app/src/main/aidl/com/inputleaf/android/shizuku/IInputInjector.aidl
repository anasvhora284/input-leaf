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
     * Inject printable text (for non-Latin keyboard layouts).
     * @param text UTF-8 text to inject
     * @return true if injection succeeded
     */
    boolean injectText(String text);

    /**
     * Register a real HID keyboard on /dev/uhid so Android treats keys as hardware input.
     * Idempotent while already open.
     */
    boolean openVirtualKeyboard();

    /**
     * Destroy the HID keyboard. Android then sees the physical keyboard disconnect.
     */
    void closeVirtualKeyboard();

    /**
     * Send a key through the HID keyboard.
     * @return false when unmapped or the keyboard is not open, so the caller can fall back
     */
    boolean injectHidKey(int evdevCode, boolean isDown);

    /**
     * Release every held HID key so a leave/disconnect cannot stick a key down.
     */
    void releaseHidKeys();

    /**
     * Register a real HID mouse on /dev/uhid so Android treats motion as hardware input.
     * Idempotent while already open.
     */
    boolean openVirtualMouse();

    /**
     * Destroy the HID mouse. Android then sees the physical mouse disconnect.
     */
    void closeVirtualMouse();

    /**
     * Send a relative motion report through the HID mouse.
     * @return false when the mouse is not open, so the caller can fall back
     */
    boolean injectHidMouse(int dx, int dy, int buttons, int wheel);
    
    /**
     * Hand the injector a binder owned by the client process so it can watch for that
     * process dying. Without it, an app that is force-stopped or crashes while HID
     * devices are attached leaves them registered on /dev/uhid: the teardown calls
     * never arrive, and Android keeps believing a physical keyboard is connected.
     */
    void attachClient(IBinder token);

    /**
     * Destroy the service and release resources.
     */
    void destroy();
}
