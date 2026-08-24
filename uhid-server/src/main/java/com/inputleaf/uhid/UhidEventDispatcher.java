package com.inputleaf.uhid;

import java.io.DataInputStream;
import java.io.IOException;

/** Decodes socket event payloads and forwards supported events to an input sink. */
public class UhidEventDispatcher {
    public interface EventSink {
        void keyDown(int hidUsage, byte modifiers) throws IOException;
        void keyUp(int hidUsage) throws IOException;
        void mouseMove(int dx, int dy) throws IOException;
        void mouseButtonDown(byte button) throws IOException;
        void mouseButtonUp(byte button) throws IOException;
        void mouseWheel(short delta) throws IOException;
    }

    private final EventSink sink;

    public UhidEventDispatcher(EventSink sink) {
        this.sink = sink;
    }

    public void dispatch(byte type, DataInputStream input) throws IOException {
        switch (type) {
            case EventProtocol.TYPE_KEY_EVENT:
                dispatchKey(input.readInt(), input.readByte(), input.readByte());
                break;
            case EventProtocol.TYPE_MOUSE_MOVE:
                sink.mouseMove(input.readInt(), input.readInt());
                break;
            case EventProtocol.TYPE_MOUSE_BTN:
                dispatchButton(input.readByte(), input.readByte());
                break;
            case EventProtocol.TYPE_MOUSE_WHEEL:
                input.readShort(); // Horizontal scrolling is not supported by the HID descriptor.
                sink.mouseWheel(input.readShort());
                break;
            default:
                throw new IOException("Unsupported UHID event type: " + (type & 0xFF));
        }
    }

    private void dispatchKey(int keysym, byte action, byte modifiers) throws IOException {
        if (action != EventProtocol.ACTION_DOWN && action != EventProtocol.ACTION_UP) {
            throw new IOException("Unsupported key action: " + (action & 0xFF));
        }
        Integer hid = KeysymToHid.lookup(keysym);
        if (hid == null) return;
        if (action == EventProtocol.ACTION_DOWN) {
            sink.keyDown(hid, modifiers);
        } else {
            sink.keyUp(hid);
        }
    }

    private void dispatchButton(byte button, byte action) throws IOException {
        if (button < MouseDevice.MIN_BUTTON || button > MouseDevice.MAX_BUTTON) {
            throw new IOException("Unsupported mouse button: " + (button & 0xFF));
        }
        if (action == EventProtocol.ACTION_DOWN) {
            sink.mouseButtonDown(button);
        } else if (action == EventProtocol.ACTION_UP) {
            sink.mouseButtonUp(button);
        } else {
            throw new IOException("Unsupported mouse action: " + (action & 0xFF));
        }
    }
}
