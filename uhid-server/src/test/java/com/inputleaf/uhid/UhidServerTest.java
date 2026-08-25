package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class UhidServerTest {
    @Test public void acceptsOnlyTheAppAndItsNamedProcesses() {
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android")).isTrue();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android:uhid")).isTrue();

        assertThat(UhidServer.isAllowedProcessName("")).isFalse();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android.evil")).isFalse();
        assertThat(UhidServer.isAllowedProcessName("evil.com.inputleaf.android")).isFalse();
    }

    @Test public void readsOnlyTheProcessNameFromProcCmdline() {
        byte[] cmdline = "com.inputleaf.android:worker\u0000--argument\u0000"
            .getBytes(StandardCharsets.UTF_8);

        assertThat(UhidServer.firstArgument(cmdline)).isEqualTo("com.inputleaf.android:worker");
    }

    @Test public void treatsClientEofAsACleanSessionEnd() throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(new byte[0]));

        UhidServer.runSession(input, dispatcher());

        assertThat(input.available()).isEqualTo(0);
    }

    @Test public void stopsAtShutdownWithoutConsumingTheNextByte() throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(new byte[] {
            EventProtocol.TYPE_SHUTDOWN, 42
        }));

        UhidServer.runSession(input, dispatcher());

        assertThat(input.readUnsignedByte()).isEqualTo(42);
    }

    @Test public void attemptsToCloseBothDevicesAndPreservesBothFailures() {
        FailingCloseOutputStream keyboardOutput = new FailingCloseOutputStream("keyboard");
        FailingCloseOutputStream mouseOutput = new FailingCloseOutputStream("mouse");
        UhidServer server = new UhidServer(
            new KeyboardDevice(keyboardOutput),
            new MouseDevice(mouseOutput)
        );

        IOException failure = assertThrows(IOException.class, server::close);

        assertThat(keyboardOutput.closed).isTrue();
        assertThat(mouseOutput.closed).isTrue();
        assertThat(failure).hasMessageThat().isEqualTo("keyboard close failed");
        assertThat(failure.getSuppressed()).asList().hasSize(1);
        assertThat(failure.getSuppressed()[0]).hasMessageThat().isEqualTo("mouse close failed");
    }

    private UhidEventDispatcher dispatcher() {
        return new UhidEventDispatcher(new UhidEventDispatcher.EventSink() {
            @Override public void keyDown(int hidUsage, byte modifiers) {}
            @Override public void keyUp(int hidUsage, byte modifiers) {}
            @Override public void mouseMove(int dx, int dy) {}
            @Override public void mouseButtonDown(byte button) {}
            @Override public void mouseButtonUp(byte button) {}
            @Override public void mouseWheel(short deltaX, short deltaY) {}
        });
    }

    private static class FailingCloseOutputStream extends OutputStream {
        private final String device;
        boolean closed;

        FailingCloseOutputStream(String device) {
            this.device = device;
        }

        @Override public void write(int value) {}

        @Override public void close() throws IOException {
            closed = true;
            throw new IOException(device + " close failed");
        }
    }
}
