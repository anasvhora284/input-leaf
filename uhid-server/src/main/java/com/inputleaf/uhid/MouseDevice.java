package com.inputleaf.uhid;

import java.io.*;

public class MouseDevice implements Closeable {
    private static final int UHID_CREATE2 = 11;
    private static final int UHID_INPUT2  = 12;

    // Relative mouse HID descriptor: buttons + X + Y + wheel
    private static final byte[] MOUSE_DESCRIPTOR = {
        0x05, 0x01, 0x09, 0x02, (byte)0xA1, 0x01, 0x09, 0x01,
        (byte)0xA1, 0x00,
        // 3 buttons
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, (byte)0x95, 0x03, (byte)0x81, 0x02,
        // 5 padding bits
        0x75, 0x05, (byte)0x95, 0x01, (byte)0x81, 0x03,
        // X, Y (relative, -127 to 127)
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x15, (byte)0x81, 0x25, 0x7F,
        0x75, 0x08, (byte)0x95, 0x02, (byte)0x81, 0x06,
        // Wheel
        0x09, 0x38, 0x15, (byte)0x81, 0x25, 0x7F, 0x75, 0x08, (byte)0x95, 0x01,
        (byte)0x81, 0x06,
        (byte)0xC0, (byte)0xC0
    };

    private FileOutputStream uhid;
    private byte buttonState = 0;

    public MouseDevice() throws IOException {
        uhid = new FileOutputStream("/dev/uhid");
        writeCreate2("InputLeaf Mouse", MOUSE_DESCRIPTOR);
    }

    public void move(int dx, int dy) throws IOException {
        sendReport(buttonState, clamp(dx), clamp(dy), (byte)0);
    }

    // button: 1-indexed (1=left, 2=right, 3=middle) per Input-Leap protocol
    public void buttonDown(int button) throws IOException {
        buttonState |= (byte)(1 << (button - 1));
        sendReport(buttonState, (byte)0, (byte)0, (byte)0);
    }

    public void buttonUp(int button) throws IOException {
        buttonState &= (byte)~(1 << (button - 1));
        sendReport(buttonState, (byte)0, (byte)0, (byte)0);
    }

    public void wheel(int delta) throws IOException {
        sendReport(buttonState, (byte)0, (byte)0, clamp(delta));
    }

    private byte clamp(int v) { return (byte) Math.max(-127, Math.min(127, v)); }

    private void sendReport(byte buttons, byte x, byte y, byte wheel) throws IOException {
        byte[] report = {buttons, x, y, wheel};
        byte[] pkt = new byte[4 + 4 + 4096];
        writeInt(pkt, 0, UHID_INPUT2);
        writeInt(pkt, 4, report.length);
        System.arraycopy(report, 0, pkt, 8, report.length);
        uhid.write(pkt);
    }

    private void writeCreate2(String name, byte[] descriptor) throws IOException {
        byte[] pkt = new byte[4 + 4 + 128 + 4 + 4096 + 16];
        writeInt(pkt, 0, UHID_CREATE2);
        byte[] nameBytes = name.getBytes("UTF-8");
        System.arraycopy(nameBytes, 0, pkt, 4, Math.min(nameBytes.length, 127));
        writeInt(pkt, 132, descriptor.length);
        System.arraycopy(descriptor, 0, pkt, 136, descriptor.length);
        uhid.write(pkt);
    }

    private void writeInt(byte[] buf, int offset, int value) {
        buf[offset]   = (byte)(value & 0xFF);
        buf[offset+1] = (byte)((value >> 8) & 0xFF);
        buf[offset+2] = (byte)((value >> 16) & 0xFF);
        buf[offset+3] = (byte)((value >> 24) & 0xFF);
    }

    @Override public void close() throws IOException { uhid.close(); }
}
