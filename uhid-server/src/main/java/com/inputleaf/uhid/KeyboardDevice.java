package com.inputleaf.uhid;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class KeyboardDevice implements Closeable {
    private static final int UHID_CREATE2 = 11;
    private static final int UHID_INPUT2 = 12;

    private static final byte[] KEYBOARD_DESCRIPTOR = {
        0x05, 0x01, 0x09, 0x06, (byte) 0xA1, 0x01,
        0x05, 0x07, 0x19, (byte) 0xE0, 0x29, (byte) 0xE7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, (byte) 0x95, 0x08, (byte) 0x81, 0x02,
        (byte) 0x95, 0x01, 0x75, 0x08, (byte) 0x81, 0x01,
        0x05, 0x07, 0x19, 0x00, 0x29, (byte) 0xDD, 0x15, 0x00, 0x25, (byte) 0xDD,
        0x75, 0x08, (byte) 0x95, 0x06, (byte) 0x81, 0x00,
        (byte) 0xC0
    };

    private final OutputStream uhid;
    // 8-byte HID report: [modifier, reserved, key0..key5]
    private final byte[] report = new byte[8];

    public KeyboardDevice() throws IOException {
        this(new FileOutputStream("/dev/uhid"));
        writeCreate2("InputLeaf Keyboard", KEYBOARD_DESCRIPTOR);
    }

    KeyboardDevice(OutputStream uhid) {
        this.uhid = uhid;
    }

    public void keyDown(int hidUsage, byte modifiers) throws IOException {
        report[0] = modifiers;
        byte key = (byte) (hidUsage & 0xFF);
        for (int i = 2; i < 8; i++) {
            if (report[i] == key) {
                sendReport();
                return;
            }
        }
        for (int i = 2; i < 8; i++) {
            if (report[i] == 0) {
                report[i] = key;
                break;
            }
        }
        sendReport();
    }

    public void keyUp(int hidUsage) throws IOException {
        byte key = (byte) (hidUsage & 0xFF);
        for (int i = 2; i < 8; i++) {
            if (report[i] == key) {
                report[i] = 0;
                break;
            }
        }
        sendReport();
    }

    private void sendReport() throws IOException {
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
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    @Override public void close() throws IOException { uhid.close(); }
}
