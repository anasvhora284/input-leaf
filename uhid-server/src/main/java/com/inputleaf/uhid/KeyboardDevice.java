package com.inputleaf.uhid;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class KeyboardDevice implements Closeable {
    private static final int UHID_CREATE2 = 11;
    private static final int UHID_INPUT2 = 12;
    private static final int UHID_DATA_SIZE = 4096;
    private static final int REPORT_SIZE = 8;
    private static final int KEY_SLOT_START = 2;
    private static final int MAX_CONCURRENT_KEYS = 6;
    private static final int FIRST_MODIFIER_USAGE = 0xE0;
    private static final int LAST_MODIFIER_USAGE = 0xE7;

    private static final byte[] KEYBOARD_DESCRIPTOR = {
        0x05, 0x01, 0x09, 0x06, (byte) 0xA1, 0x01,
        0x05, 0x07, 0x19, (byte) FIRST_MODIFIER_USAGE, 0x29, (byte) LAST_MODIFIER_USAGE,
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, (byte) 0x95, 0x08, (byte) 0x81, 0x02,
        (byte) 0x95, 0x01, 0x75, 0x08, (byte) 0x81, 0x01,
        0x05, 0x07, 0x19, 0x00, 0x29, (byte) 0xDD, 0x15, 0x00, 0x25, (byte) 0xDD,
        0x75, 0x08, (byte) 0x95, MAX_CONCURRENT_KEYS, (byte) 0x81, 0x00,
        (byte) 0xC0
    };

    private final OutputStream uhid;
    // Standard boot-keyboard report: [modifiers, reserved, key0..key5].
    private final byte[] report = new byte[REPORT_SIZE];

    public KeyboardDevice() throws IOException {
        this(initializeOutput(new FileOutputStream("/dev/uhid")));
    }

    KeyboardDevice(OutputStream uhid) {
        this.uhid = uhid;
    }

    static OutputStream initializeOutput(OutputStream uhid) throws IOException {
        try {
            writeCreate2(uhid, "InputLeaf Keyboard", KEYBOARD_DESCRIPTOR);
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

    public void keyDown(int hidUsage, byte modifiers) throws IOException {
        report[0] = modifiers;
        if (isModifierUsage(hidUsage)) {
            sendReport();
            return;
        }

        byte key = (byte) (hidUsage & 0xFF);
        for (int index = KEY_SLOT_START; index < REPORT_SIZE; index++) {
            if (report[index] == key) {
                sendReport();
                return;
            }
        }
        for (int index = KEY_SLOT_START; index < REPORT_SIZE; index++) {
            if (report[index] == 0) {
                report[index] = key;
                break;
            }
        }
        sendReport();
    }

    public void keyUp(int hidUsage, byte modifiers) throws IOException {
        report[0] = modifiers;
        if (isModifierUsage(hidUsage)) {
            sendReport();
            return;
        }

        byte key = (byte) (hidUsage & 0xFF);
        for (int index = KEY_SLOT_START; index < REPORT_SIZE; index++) {
            if (report[index] == key) {
                report[index] = 0;
                break;
            }
        }
        sendReport();
    }

    private boolean isModifierUsage(int hidUsage) {
        return hidUsage >= FIRST_MODIFIER_USAGE && hidUsage <= LAST_MODIFIER_USAGE;
    }

    private void sendReport() throws IOException {
        byte[] packet = new byte[4 + 4 + UHID_DATA_SIZE];
        writeInt(packet, 0, UHID_INPUT2);
        writeInt(packet, 4, report.length);
        System.arraycopy(report, 0, packet, 8, report.length);
        uhid.write(packet);
    }

    private static void writeCreate2(OutputStream uhid, String name, byte[] descriptor) throws IOException {
        byte[] packet = new byte[4 + 4 + 128 + 4 + UHID_DATA_SIZE + 16];
        writeInt(packet, 0, UHID_CREATE2);
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, packet, 4, Math.min(nameBytes.length, 127));
        writeInt(packet, 132, descriptor.length);
        System.arraycopy(descriptor, 0, packet, 136, descriptor.length);
        uhid.write(packet);
    }

    private static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    @Override public void close() throws IOException { uhid.close(); }
}
