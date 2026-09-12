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
     * Register a real HID pointer on /dev/uhid so Android draws its own cursor — the
     * only cursor that renders above the notification shade and Quick Settings.
     * Idempotent: repeated calls while open are a no-op.
     * @return true if the pointer is registered and usable
     */
    boolean openVirtualPointer();

    /**
     * Tear down the HID pointer. Safe to call when nothing is open.
     */
    void closeVirtualPointer();

    /**
     * @return true while a HID pointer is registered
     */
    boolean isVirtualPointerOpen();

    /**
     * Move the HID pointer to an absolute screen position.
     * @param x X coordinate in screen pixels
     * @param y Y coordinate in screen pixels
     * @param screenWidth display width used to scale into the HID logical range
     * @param screenHeight display height used to scale into the HID logical range
     */
    void movePointerAbsolute(int x, int y, int screenWidth, int screenHeight);

    /**
     * Forget the tracked pointer position. The next move re-establishes it by slamming to
     * the screen origin, where Android's edge clamping gives a known reference point.
     * Call when the server hands control over or takes it away.
     */
    void resyncPointer();

    /**
     * Press or release a HID pointer button.
     * @param buttonId InputLeap button id (1 = left, 2 = middle, 3 = right)
     * @param pressed true to press, false to release
     */
    void pointerButton(int buttonId, boolean pressed);

    /**
     * Scroll the HID pointer.
     * @param horizontal InputLeap x delta, 120 units per notch
     * @param vertical InputLeap y delta, 120 units per notch
     */
    void pointerWheel(int horizontal, int vertical);

    /**
     * Send a key through the HID keyboard, so Android sees real hardware input.
     * @param evdevCode Linux evdev key code
     * @param isDown true for press, false for release
     * @return true if the key was mapped and sent; false means the caller should fall
     *         back to injectKeyEvent or text injection
     */
    boolean injectHidKey(int evdevCode, boolean isDown);

    /**
     * Release every held HID key, so a disconnect mid-keypress leaves nothing stuck down.
     */
    void releaseHidKeys();

    /**
     * Show or hide the on-screen keyboard while a hardware keyboard is attached, which is
     * how the user reaches their IME's emoji and GIF pickers during a session.
     * @return the new visibility state
     */
    boolean toggleSoftKeyboard();

    /**
     * @return true if the on-screen keyboard is currently forced visible
     */
    boolean isSoftKeyboardShown();

    /**
     * Destroy the service and release resources.
     */
    void destroy();
}
