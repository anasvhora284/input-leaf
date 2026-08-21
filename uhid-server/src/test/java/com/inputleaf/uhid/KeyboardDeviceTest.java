package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import org.junit.Test;

public class KeyboardDeviceTest {
    private static final int PACKET_SIZE = 4 + 4 + 4096;

    private byte[] reportAt(ByteArrayOutputStream output, int index) {
        byte[] bytes = output.toByteArray();
        int packetOffset = index * PACKET_SIZE;
        assertThat(littleEndianInt(bytes, packetOffset)).isEqualTo(12);
        assertThat(littleEndianInt(bytes, packetOffset + 4)).isEqualTo(8);
        byte[] report = new byte[8];
        System.arraycopy(bytes, packetOffset + 8, report, 0, report.length);
        return report;
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
    }

    @Test public void emitsKeyDownUpAndModifierReports() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        keyboard.keyDown(0x04, (byte) 0x02);
        keyboard.keyUp(0x04);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {0x02, 0, 0x04, 0, 0, 0, 0, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0x02, 0, 0, 0, 0, 0, 0, 0});
    }

    @Test public void keepsAKeyInOneSlotAndDoesNotDuplicateIt() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        keyboard.keyDown(0x04, (byte) 0);
        keyboard.keyDown(0x04, (byte) 0);

        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0, 0, 0x04, 0, 0, 0, 0, 0});
    }

    @Test public void retainsOnlySixConcurrentKeys() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        for (int key = 0x04; key <= 0x0A; key++) keyboard.keyDown(key, (byte) 0);

        assertThat(reportAt(output, 6)).isEqualTo(new byte[] {0, 0, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09});
    }
}
