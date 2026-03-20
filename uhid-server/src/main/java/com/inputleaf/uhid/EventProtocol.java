package com.inputleaf.uhid;

public class EventProtocol {
    public static final byte TYPE_KEY_EVENT   = 0x01;
    public static final byte TYPE_MOUSE_MOVE  = 0x02;
    public static final byte TYPE_MOUSE_BTN   = 0x03;
    public static final byte TYPE_MOUSE_WHEEL = 0x04;
    public static final byte TYPE_SHUTDOWN    = (byte) 0xFF;

    public static final byte ACTION_DOWN = 0x00;
    public static final byte ACTION_UP   = 0x01;

    public static class KeyEvent {
        public final int keysym; // X11 keysym, 4 bytes
        public final byte action;
        public final byte modifiers;
        public KeyEvent(int keysym, byte action, byte modifiers) {
            this.keysym = keysym; this.action = action; this.modifiers = modifiers;
        }
    }

    public static class MouseMove { public final int dx, dy; public MouseMove(int dx, int dy) { this.dx=dx; this.dy=dy; } }
    public static class MouseButton { public final byte button, action; public MouseButton(byte btn, byte act) { button=btn; action=act; } }
    public static class MouseWheel { public final short deltaX, deltaY; public MouseWheel(short dx, short dy) { deltaX=dx; deltaY=dy; } }
}
