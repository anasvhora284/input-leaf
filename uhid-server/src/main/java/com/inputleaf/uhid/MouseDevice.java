package com.inputleaf.uhid;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MouseDevice implements Closeable {
    static final int MIN_BUTTON = 1;
    static final int MAX_BUTTON = 3;

    private static final int UHID_CREATE2 = 11;
    private static final int UHID_INPUT2 = 12;

    private static final byte[] MOUSE_DESCRIPTOR = {
        0x05, 0x01, 0x09, 0x02, (byte) 0xA1, 0x01, 0x09, 0x01,
        (byte) 0xA1, 0x00,
        0x05, 0x09, 0x19, MIN_BUTTON, 0x29, MAX_BUTTON, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, (byte) 0x95, MAX_BUTTON, (byte) 0x81, 0x02,
        0x75, 0x05, (byte) 0x95, 0x01, (byte) 0x81, 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, (byte) 0x81, 0x25, 0x7F,
        0x75, 0x08, (byte) 0x95, 0x02, (byte) 0x81, 0x06,
        0x09, 0x38, 0x15, (byte) 0x81, 0x25, 0x7F, 0x75, 0x08, (byte) 0x95, 0x01,
        (byte) 0x81, 0x06,
        // Consumer-page AC Pan provides horizontal wheel scrolling.
        0x05, 0x0C, 0x0A, 0x38, 0x02, 0x15, (byte) 0x81, 0x25, 0x7F,
        0x75, 0x08, (byte) 0x95, 0x01, (byte) 0x81, 0x06,
        (byte) 0xC0, (byte) 0xC0
    };

    private final OutputStream uhid;
    private byte buttonState = 0;

    public MouseDevice() throws IOException {
        this(initializeOutput(new FileOutputStream("/dev/uhid")));
    }

    MouseDevice(OutputStream uhid) {
        this.uhid = uhid;
    }

    static OutputStream initializeOutput(OutputStream uhid) throws IOException {
        try {
            writeCreate2(uhid, "InputLeaf Mouse", MOUSE_DESCRIPTOR);
            return uhid;
        } catch (IOException createFailure) {
            try {
                uhid.close();
            } catch (IOException closeFailure) {
                createFailure.addSuppressed(closeFailure);
            }
            throw createFailure;
        }
    }

    public void move(int dx, int dy) throws IOException {
        sendReport(buttonState, clamp(dx), clamp(dy), (byte) 0, (byte) 0);
    }

    // button: 1-indexed (1=left, 2=right, 3=middle) per Input-Leap protocol
    public void buttonDown(int button) throws IOException {
        validateButton(button);
        buttonState |= (byte) (1 << (button - 1));
        sendReport(buttonState, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
    }

    public void buttonUp(int button) throws IOException {
        validateButton(button);
        buttonState &= (byte) ~(1 << (button - 1));
        sendReport(buttonState, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
    }

    private void validateButton(int button) {
        if (button < MIN_BUTTON || button > MAX_BUTTON) {
            throw new IllegalArgumentException("Unsupported mouse button: " + button);
        }
    }

    public void wheel(int deltaX, int deltaY) throws IOException {
        sendReport(buttonState, (byte) 0, (byte) 0, clamp(deltaY), clamp(deltaX));
    }

    private byte clamp(int value) {
        return (byte) Math.max(-127, Math.min(127, value));
    }

    private void sendReport(byte buttons, byte x, byte y, byte wheel, byte horizontalPan) throws IOException {
        byte[] report = {buttons, x, y, wheel, horizontalPan};
        byte[] pkt = new byte[4 + 4 + 4096];
        writeInt(pkt, 0, UHID_INPUT2);
        writeInt(pkt, 4, report.length);
        System.arraycopy(report, 0, pkt, 8, report.length);
        uhid.write(pkt);
    }

    private static void writeCreate2(OutputStream uhid, String name, byte[] descriptor) throws IOException {
        byte[] pkt = new byte[4 + 4 + 128 + 4 + 4096 + 16];
        writeInt(pkt, 0, UHID_CREATE2);
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, pkt, 4, Math.min(nameBytes.length, 127));
        writeInt(pkt, 132, descriptor.length);
        System.arraycopy(descriptor, 0, pkt, 136, descriptor.length);
        uhid.write(pkt);
    }

    private static void writeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    @Override public void close() throws IOException { uhid.close(); }
}
