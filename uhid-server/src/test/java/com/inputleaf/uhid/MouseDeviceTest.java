package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import org.junit.Test;

public class MouseDeviceTest {
    private static final int PACKET_SIZE = 4 + 4 + 4096;

    private byte[] reportAt(ByteArrayOutputStream output, int index) {
        byte[] bytes = output.toByteArray();
        int packetOffset = index * PACKET_SIZE;
        assertThat(littleEndianInt(bytes, packetOffset)).isEqualTo(12);
        assertThat(littleEndianInt(bytes, packetOffset + 4)).isEqualTo(4);
        byte[] report = new byte[4];
        System.arraycopy(bytes, packetOffset + 8, report, 0, report.length);
        return report;
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
    }

    @Test public void retainsButtonStateAcrossMovementAndRelease() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MouseDevice mouse = new MouseDevice(output);

        mouse.buttonDown(1);
        mouse.move(5, -3);
        mouse.buttonUp(1);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {1, 0, 0, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {1, 5, -3, 0});
        assertThat(reportAt(output, 2)).isEqualTo(new byte[] {0, 0, 0, 0});
    }

    @Test public void clampsMovementAndWheelToHidReportRange() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MouseDevice mouse = new MouseDevice(output);

        mouse.move(1000, -1000);
        mouse.wheel(1000);
        mouse.wheel(-1000);

        assertThat(reportAt(output, 0)).isEqualTo(new byte[] {0, 127, -127, 0});
        assertThat(reportAt(output, 1)).isEqualTo(new byte[] {0, 0, 0, 127});
        assertThat(reportAt(output, 2)).isEqualTo(new byte[] {0, 0, 0, -127});
    }
}
