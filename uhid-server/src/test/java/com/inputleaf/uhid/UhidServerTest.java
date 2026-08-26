package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class UhidServerTest {
    @Test public void acceptsOnlyTheAppAndValidNamedProcesses() {
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android")).isTrue();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android:uhid")).isTrue();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android:worker_2.remote")).isTrue();

        assertThat(UhidServer.isAllowedProcessName("")).isFalse();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android:")).isFalse();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android:worker process")).isFalse();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android:worker:extra")).isFalse();
        assertThat(UhidServer.isAllowedProcessName("com.inputleaf.android.evil")).isFalse();
        assertThat(UhidServer.isAllowedProcessName("evil.com.inputleaf.android")).isFalse();
    }

    @Test public void readsOnlyTheFirstProcCmdlineArgument() {
        byte[] cmdline = "com.inputleaf.android:worker\u0000--argument\u0000"
            .getBytes(StandardCharsets.UTF_8);

        assertThat(UhidServer.firstArgument(cmdline)).isEqualTo("com.inputleaf.android:worker");
        assertThat(UhidServer.firstArgument("without-separator".getBytes(StandardCharsets.UTF_8)))
            .isEqualTo("without-separator");
        assertThat(UhidServer.firstArgument(new byte[0])).isEmpty();
    }

    @Test public void treatsClientEofAsACleanSessionEnd() throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(new byte[0]));

        UhidServer.runSession(input, new UhidEventDispatcher(new RecordingSink()));

        assertThat(input.available()).isEqualTo(0);
    }

    @Test public void dispatchesEventsInOrderAndStopsAtShutdown() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(EventProtocol.TYPE_KEY_EVENT);
        output.writeInt('A');
        output.writeByte(EventProtocol.ACTION_DOWN);
        output.writeByte(2);
        output.writeByte(EventProtocol.TYPE_MOUSE_MOVE);
        output.writeInt(5);
        output.writeInt(-3);
        output.writeByte(EventProtocol.TYPE_SHUTDOWN);
        output.writeByte(42);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        RecordingSink sink = new RecordingSink();

        UhidServer.runSession(input, new UhidEventDispatcher(sink));

        assertThat(sink.events).containsExactly("keyDown:4:2", "move:5:-3").inOrder();
        assertThat(input.readUnsignedByte()).isEqualTo(42);
    }

    @Test public void preservesMalformedEventErrors() throws Exception {
        byte[] truncatedWheel = {
            EventProtocol.TYPE_MOUSE_WHEEL, 0, 1, 0
        };
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(truncatedWheel));

        IOException failure = assertThrows(IOException.class,
            () -> UhidServer.runSession(input, new UhidEventDispatcher(new RecordingSink())));

        assertThat(failure).hasMessageThat().isEqualTo("Truncated UHID mouse-wheel event");
    }

    @Test public void closesBothDevicesWhenNeitherCloseFails() throws Exception {
        TrackingOutputStream keyboardOutput = new TrackingOutputStream("keyboard", false);
        TrackingOutputStream mouseOutput = new TrackingOutputStream("mouse", false);
        UhidServer server = serverWith(keyboardOutput, mouseOutput);

        server.close();

        assertThat(keyboardOutput.closed).isTrue();
        assertThat(mouseOutput.closed).isTrue();
    }

    @Test public void attemptsToCloseMouseWhenKeyboardCloseFails() {
        TrackingOutputStream keyboardOutput = new TrackingOutputStream("keyboard", true);
        TrackingOutputStream mouseOutput = new TrackingOutputStream("mouse", false);
        UhidServer server = serverWith(keyboardOutput, mouseOutput);

        IOException failure = assertThrows(IOException.class, server::close);

        assertThat(keyboardOutput.closed).isTrue();
        assertThat(mouseOutput.closed).isTrue();
        assertThat(failure).hasMessageThat().isEqualTo("keyboard close failed");
        assertThat(failure.getSuppressed()).isEmpty();
    }

    @Test public void reportsMouseCloseFailureAfterClosingKeyboard() {
        TrackingOutputStream keyboardOutput = new TrackingOutputStream("keyboard", false);
        TrackingOutputStream mouseOutput = new TrackingOutputStream("mouse", true);
        UhidServer server = serverWith(keyboardOutput, mouseOutput);

        IOException failure = assertThrows(IOException.class, server::close);

        assertThat(keyboardOutput.closed).isTrue();
        assertThat(mouseOutput.closed).isTrue();
        assertThat(failure).hasMessageThat().isEqualTo("mouse close failed");
    }

    @Test public void preservesBothDeviceCloseFailures() {
        TrackingOutputStream keyboardOutput = new TrackingOutputStream("keyboard", true);
        TrackingOutputStream mouseOutput = new TrackingOutputStream("mouse", true);
        UhidServer server = serverWith(keyboardOutput, mouseOutput);

        IOException failure = assertThrows(IOException.class, server::close);

        assertThat(keyboardOutput.closed).isTrue();
        assertThat(mouseOutput.closed).isTrue();
        assertThat(failure).hasMessageThat().isEqualTo("keyboard close failed");
        assertThat(failure.getSuppressed()).asList().hasSize(1);
        assertThat(failure.getSuppressed()[0]).hasMessageThat().isEqualTo("mouse close failed");
    }

    @Test public void closesKeyboardWhenMouseCreationFails() {
        TrackingOutputStream keyboardOutput = new TrackingOutputStream("keyboard", true);
        IOException creationFailure = new IOException("mouse creation failed");
        UhidServer.DeviceFactory factory = new UhidServer.DeviceFactory() {
            @Override public KeyboardDevice createKeyboard() {
                return new KeyboardDevice(keyboardOutput);
            }

            @Override public MouseDevice createMouse() throws IOException {
                throw creationFailure;
            }
        };

        IOException failure = assertThrows(IOException.class, () -> new UhidServer(factory));

        assertThat(failure).isSameInstanceAs(creationFailure);
        assertThat(keyboardOutput.closed).isTrue();
        assertThat(failure.getSuppressed()).asList().hasSize(1);
        assertThat(failure.getSuppressed()[0]).hasMessageThat().isEqualTo("keyboard close failed");
    }

    private UhidServer serverWith(OutputStream keyboardOutput, OutputStream mouseOutput) {
        return new UhidServer(new KeyboardDevice(keyboardOutput), new MouseDevice(mouseOutput));
    }

    private static class RecordingSink implements UhidEventDispatcher.EventSink {
        final List<String> events = new ArrayList<>();

        @Override public void keyDown(int hidUsage, byte modifiers) {
            events.add("keyDown:" + hidUsage + ":" + modifiers);
        }
        @Override public void keyUp(int hidUsage, byte modifiers) {
            events.add("keyUp:" + hidUsage + ":" + modifiers);
        }
        @Override public void mouseMove(int dx, int dy) {
            events.add("move:" + dx + ":" + dy);
        }
        @Override public void mouseButtonDown(byte button) {
            events.add("buttonDown:" + button);
        }
        @Override public void mouseButtonUp(byte button) {
            events.add("buttonUp:" + button);
        }
        @Override public void mouseWheel(short deltaX, short deltaY) {
            events.add("wheel:" + deltaX + ":" + deltaY);
        }
    }

    private static class TrackingOutputStream extends OutputStream {
        private final String device;
        private final boolean failOnClose;
        boolean closed;

        TrackingOutputStream(String device, boolean failOnClose) {
            this.device = device;
            this.failOnClose = failOnClose;
        }

        @Override public void write(int value) {}

        @Override public void close() throws IOException {
            closed = true;
            if (failOnClose) throw new IOException(device + " close failed");
        }
    }
}
