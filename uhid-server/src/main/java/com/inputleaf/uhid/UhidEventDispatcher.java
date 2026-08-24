package com.inputleaf.uhid;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;

/** Decodes socket event payloads and forwards supported events to an input sink. */
public class UhidEventDispatcher {
    public interface EventSink {
        void keyDown(int hidUsage, byte modifiers) throws IOException;
        void keyUp(int hidUsage, byte modifiers) throws IOException;
        void mouseMove(int dx, int dy) throws IOException;
        void mouseButtonDown(byte button) throws IOException;
        void mouseButtonUp(byte button) throws IOException;
        void mouseWheel(short deltaX, short deltaY) throws IOException;
    }

    private final EventSink sink;

    public UhidEventDispatcher(EventSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public void dispatch(byte type, DataInputStream input) throws IOException {
        switch (type) {
            case EventProtocol.TYPE_KEY_EVENT: {
                // keysym(4), action(1), modifiers(1)
                int keysym;
                byte action;
                byte modifiers;
                try {
                    keysym = input.readInt();
                    action = input.readByte();
                    modifiers = input.readByte();
                } catch (EOFException truncated) {
                    throw truncated("keyboard", truncated);
                }
                dispatchKey(keysym, action, modifiers);
                break;
            }
            case EventProtocol.TYPE_MOUSE_MOVE: {
                // deltaX(4), deltaY(4)
                int deltaX;
                int deltaY;
                try {
                    deltaX = input.readInt();
                    deltaY = input.readInt();
                } catch (EOFException truncated) {
                    throw truncated("mouse-move", truncated);
                }
                sink.mouseMove(deltaX, deltaY);
                break;
            }
            case EventProtocol.TYPE_MOUSE_BTN: {
                // button(1), action(1)
                byte button;
                byte action;
                try {
                    button = input.readByte();
                    action = input.readByte();
                } catch (EOFException truncated) {
                    throw truncated("mouse-button", truncated);
                }
                dispatchButton(button, action);
                break;
            }
            case EventProtocol.TYPE_MOUSE_WHEEL: {
                // deltaX(2), deltaY(2)
                short deltaX;
                short deltaY;
                try {
                    deltaX = input.readShort();
                    deltaY = input.readShort();
                } catch (EOFException truncated) {
                    throw truncated("mouse-wheel", truncated);
                }
                sink.mouseWheel(deltaX, deltaY);
                break;
            }
            default:
                throw new IOException("Unsupported UHID event type: " + (type & 0xFF));
        }
    }

    private IOException truncated(String event, EOFException cause) {
        return new IOException("Truncated UHID " + event + " event", cause);
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
            sink.keyUp(hid, modifiers);
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
