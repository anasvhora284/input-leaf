package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class MouseDeviceTest {
    private static final int PACKET_SIZE = 4 + 4 + 4096;
    private static final int CREATE_PACKET_SIZE = 4 + 4 + 128 + 4 + 4096 + 16;

    private byte[] reportAt(ByteArrayOutputStream output, int index) {
        byte[] bytes = output.toByteArray();
        int packetOffset = index * PACKET_SIZE;
        assertThat(littleEndianInt(bytes, packetOffset)).isEqualTo(12);
        assertThat(littleEndianInt(bytes, packetOffset + 4)).isEqualTo(5);
        byte[] report = new byte[5];
        System.arraycopy(bytes, packetOffset + 8, report, 0, report.length);
        return report;
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
    }

    @Test public void writesCreatePacketWithMouseNameAndDescriptor() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThat(MouseDevice.initializeOutput(output)).isSameInstanceAs(output);

        byte[] packet = output.toByteArray();
        assertThat(packet).hasLength(CREATE_PACKET_SIZE);
        assertThat(littleEndianInt(packet, 0)).isEqualTo(11);
        assertThat(new String(packet, 4, "InputLeaf Mouse".length(), StandardCharsets.UTF_8))
            .isEqualTo("InputLeaf Mouse");
        int descriptorLength = littleEndianInt(packet, 132);
        assertThat(descriptorLength).isGreaterThan(0);
        byte[] descriptor = new byte[descriptorLength];
        System.arraycopy(packet, 136, descriptor, 0, descriptorLength);
        assertThat(containsSequence(descriptor, new byte[] {0x05, 0x0C, 0x0A, 0x38, 0x02})).isTrue();
    }

    @Test public void closesOutputWhenCreatePacketCannotBeWritten() {
        FailingOutputStream output = new FailingOutputStream();

        assertThrows(IOException.class, () -> MouseDevice.initializeOutput(output));

        assertThat(output.closed).isTrue();
    }

    @Test public void retainsAllSupportedButtonStatesAcrossMovementAndRelease() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MouseDevice mouse = new MouseDevice(output);

        mouse.buttonDown(1);
        mouse.buttonDown(2);
        mouse.buttonDown(3);
        mouse.move(5, -3);
        mouse.buttonUp(2);
        mouse.buttonUp(1);
        mouse.buttonUp(3);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {1, 0, 0, 0, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {3, 0, 0, 0, 0});
        assertThat(reportAt(output, 2)).isEqualTo(new byte[] {7, 0, 0, 0, 0});
        assertThat(reportAt(output, 3)).isEqualTo(new byte[] {7, 5, -3, 0, 0});
        assertThat(reportAt(output, 4)).isEqualTo(new byte[] {5, 0, 0, 0, 0});
        assertThat(reportAt(output, 5)).isEqualTo(new byte[] {4, 0, 0, 0, 0});
        assertThat(reportAt(output, 6)).isEqualTo(new byte[] {0, 0, 0, 0, 0});
    }

    @Test public void rejectsUnsupportedMouseButtons() throws Exception {
        MouseDevice mouse = new MouseDevice(new ByteArrayOutputStream());

        assertThrows(IllegalArgumentException.class, () -> mouse.buttonDown(0));
        assertThrows(IllegalArgumentException.class, () -> mouse.buttonUp(0));
        assertThrows(IllegalArgumentException.class, () -> mouse.buttonDown(4));
        assertThrows(IllegalArgumentException.class, () -> mouse.buttonUp(4));
    }

    @Test public void clampsMovementAndBothWheelAxesToHidReportRange() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MouseDevice mouse = new MouseDevice(output);

        mouse.move(1000, -1000);
        mouse.wheel(1000, -1000);
        mouse.wheel(-1000, 1000);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {0, 127, -127, 0, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0, 0, 0, -127, 127});
        assertThat(reportAt(output, 2)).isEqualTo(new byte[] {0, 0, 0, 127, -127});
    }

    private boolean containsSequence(byte[] bytes, byte[] sequence) {
        for (int offset = 0; offset <= bytes.length - sequence.length; offset++) {
            boolean matches = true;
            for (int index = 0; index < sequence.length; index++) {
                if (bytes[offset + index] != sequence[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
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
