package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class KeyboardDeviceTest {
    private static final int PACKET_SIZE = 4 + 4 + 4096;
    private static final int CREATE_PACKET_SIZE = 4 + 4 + 128 + 4 + 4096 + 16;

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

    @Test public void writesCreatePacketWithKeyboardNameAndDescriptor() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThat(KeyboardDevice.initializeOutput(output)).isSameInstanceAs(output);

        byte[] packet = output.toByteArray();
        assertThat(packet).hasLength(CREATE_PACKET_SIZE);
        assertThat(littleEndianInt(packet, 0)).isEqualTo(11);
        assertThat(new String(packet, 4, "InputLeaf Keyboard".length(), StandardCharsets.UTF_8))
            .isEqualTo("InputLeaf Keyboard");
        assertThat(littleEndianInt(packet, 132)).isGreaterThan(0);
    }

    @Test public void closesOutputWhenCreatePacketCannotBeWritten() {
        FailingOutputStream output = new FailingOutputStream();

        assertThrows(IOException.class, () -> KeyboardDevice.initializeOutput(output));

        assertThat(output.closed).isTrue();
    }

    @Test public void emitsKeyDownUpAndCurrentModifierState() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        keyboard.keyDown(0x04, (byte) 0x02);
        keyboard.keyUp(0x04, (byte) 0);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {0x02, 0, 0x04, 0, 0, 0, 0, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
    }

    @Test public void representsModifiersOnlyInTheModifierByte() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        keyboard.keyDown(0xE1, (byte) 0x02);
        keyboard.keyDown(0x04, (byte) 0x02);
        keyboard.keyUp(0xE1, (byte) 0);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {0x02, 0, 0, 0, 0, 0, 0, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0x02, 0, 0x04, 0, 0, 0, 0, 0});
        assertThat(reportAt(output, 2)).isEqualTo(new byte[] {0, 0, 0x04, 0, 0, 0, 0, 0});
    }

    @Test public void keepsAKeyInOneSlotAndDoesNotDuplicateIt() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        keyboard.keyDown(0x04, (byte) 0);
        keyboard.keyDown(0x04, (byte) 0);

        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0, 0, 0x04, 0, 0, 0, 0, 0});
    }

    @Test public void intentionallyIgnoresASeventhConcurrentKey() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        for (int key = 0x04; key <= 0x0A; key++) keyboard.keyDown(key, (byte) 0);

        assertThat(reportAt(output, 6)).isEqualTo(new byte[] {0, 0, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09});
    }

    @Test public void retainsHeldBackspaceUntilItIsReleased() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        KeyboardDevice keyboard = new KeyboardDevice(output);

        keyboard.keyDown(0x2A, (byte) 0);
        keyboard.keyDown(0x04, (byte) 0);
        keyboard.keyUp(0x2A, (byte) 0);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {0, 0, 0x2A, 0, 0, 0, 0, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0, 0, 0x2A, 0x04, 0, 0, 0, 0});
        assertThat(reportAt(output, 2)).isEqualTo(new byte[] {0, 0, 0, 0x04, 0, 0, 0, 0});
    }

    private static class FailingOutputStream extends OutputStream {
        boolean closed;

        @Override public void write(int value) throws IOException {
            throw new IOException("write failed");
        }

        @Override public void close() {
            closed = true;
        }
    }
}
