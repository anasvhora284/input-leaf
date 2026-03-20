package com.inputleaf.uhid;

import java.io.*;

public class KeyboardDevice implements Closeable {
    // UHID ioctl constants
    private static final int UHID_CREATE2 = 11;
    private static final int UHID_INPUT2  = 12;

    // Standard 104-key HID report descriptor
    private static final byte[] KEYBOARD_DESCRIPTOR = {
        // Usage Page (Generic Desktop), Usage (Keyboard)
        0x05, 0x01, 0x09, 0x06, (byte)0xA1, 0x01,
        // Modifier byte
        0x05, 0x07, 0x19, (byte)0xE0, 0x29, (byte)0xE7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, (byte)0x95, 0x08, (byte)0x81, 0x02,
        // Reserved byte
        (byte)0x95, 0x01, 0x75, 0x08, (byte)0x81, 0x01,
        // Keycodes (6 keys)
        0x05, 0x07, 0x19, 0x00, 0x29, (byte)0xDD, 0x15, 0x00, 0x25, (byte)0xDD,
        0x75, 0x08, (byte)0x95, 0x06, (byte)0x81, 0x00,
        (byte)0xC0
    };

    private FileOutputStream uhid;
    // 8-byte HID report: [modifier, reserved, key0..key5]
    private final byte[] report = new byte[8];

    public KeyboardDevice() throws IOException {
        uhid = new FileOutputStream("/dev/uhid");
        writeCreate2("InputLeaf Keyboard", KEYBOARD_DESCRIPTOR);
    }

    public void keyDown(int hidUsage, byte modifiers) throws IOException {
        report[0] = modifiers;
        // Find empty slot (keys 2-7)
        for (int i = 2; i < 8; i++) {
            if (report[i] == 0) { report[i] = (byte)(hidUsage & 0xFF); break; }
        }
        sendReport();
    }

    public void keyUp(int hidUsage) throws IOException {
        for (int i = 2; i < 8; i++) {
            if (report[i] == (byte)(hidUsage & 0xFF)) { report[i] = 0; break; }
        }
        sendReport();
    }

    private void sendReport() throws IOException {
        // UHID_INPUT2 structure: type(4), size(4), data(MAX_BUFFER)
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
