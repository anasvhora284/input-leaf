package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import org.junit.Test;

public class UhidEventDispatcherTest {
    private static class FakeSink implements UhidEventDispatcher.EventSink {
        String event;
        int first;
        int second;
        byte modifiers;

        @Override public void keyDown(int hidUsage, byte modifiers) { event = "keyDown"; first = hidUsage; this.modifiers = modifiers; }
        @Override public void keyUp(int hidUsage, byte modifiers) { event = "keyUp"; first = hidUsage; this.modifiers = modifiers; }
        @Override public void mouseMove(int dx, int dy) { event = "move"; first = dx; second = dy; }
        @Override public void mouseButtonDown(byte button) { event = "buttonDown"; first = button; }
        @Override public void mouseButtonUp(byte button) { event = "buttonUp"; first = button; }
        @Override public void mouseWheel(short deltaX, short deltaY) { event = "wheel"; first = deltaX; second = deltaY; }
    }

    private static class ThrowingSink implements UhidEventDispatcher.EventSink {
        final IOException failure;

        ThrowingSink(IOException failure) {
            this.failure = failure;
        }

        @Override public void keyDown(int hidUsage, byte modifiers) throws IOException { throw failure; }
        @Override public void keyUp(int hidUsage, byte modifiers) throws IOException { throw failure; }
        @Override public void mouseMove(int dx, int dy) throws IOException { throw failure; }
        @Override public void mouseButtonDown(byte button) throws IOException { throw failure; }
        @Override public void mouseButtonUp(byte button) throws IOException { throw failure; }
        @Override public void mouseWheel(short deltaX, short deltaY) throws IOException { throw failure; }
    }

    private DataInputStream input(ThrowingConsumer<DataOutputStream> writer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writer.accept(output);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private interface ThrowingConsumer<T> { void accept(T value) throws Exception; }

    @Test public void rejectsNullSinkImmediately() {
        NullPointerException error = assertThrows(NullPointerException.class,
            () -> new UhidEventDispatcher(null));

        assertThat(error).hasMessageThat().isEqualTo("sink");
    }

    @Test public void decodesKeyboardEventsAndIgnoresUnknownKeysyms() throws Exception {
        FakeSink sink = new FakeSink();
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(sink);

        dispatcher.dispatch(EventProtocol.TYPE_KEY_EVENT, input(out -> {
            out.writeInt('A'); out.writeByte(EventProtocol.ACTION_DOWN); out.writeByte(2);
        }));
        assertThat(sink.event).isEqualTo("keyDown");
        assertThat(sink.first).isEqualTo(0x04);
        assertThat(sink.modifiers).isEqualTo((byte) 2);

        dispatcher.dispatch(EventProtocol.TYPE_KEY_EVENT, input(out -> {
            out.writeInt('A'); out.writeByte(EventProtocol.ACTION_UP); out.writeByte(4);
        }));
        assertThat(sink.event).isEqualTo("keyUp");
        assertThat(sink.modifiers).isEqualTo((byte) 4);

        sink.event = null;
        dispatcher.dispatch(EventProtocol.TYPE_KEY_EVENT, input(out -> {
            out.writeInt(0x123456); out.writeByte(EventProtocol.ACTION_DOWN); out.writeByte(0);
        }));
        assertThat(sink.event).isNull();
    }

    @Test public void decodesMouseMovementBoundaryValues() throws Exception {
        FakeSink sink = new FakeSink();
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(sink);

        dispatcher.dispatch(EventProtocol.TYPE_MOUSE_MOVE, input(out -> {
            out.writeInt(Integer.MIN_VALUE); out.writeInt(Integer.MAX_VALUE);
        }));

        assertThat(sink.event).isEqualTo("move");
        assertThat(sink.first).isEqualTo(Integer.MIN_VALUE);
        assertThat(sink.second).isEqualTo(Integer.MAX_VALUE);
    }

    @Test public void decodesDownAndUpForEverySupportedMouseButton() throws Exception {
        FakeSink sink = new FakeSink();
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(sink);

        for (int button = MouseDevice.MIN_BUTTON; button <= MouseDevice.MAX_BUTTON; button++) {
            int currentButton = button;
            dispatcher.dispatch(EventProtocol.TYPE_MOUSE_BTN, input(out -> {
                out.writeByte(currentButton); out.writeByte(EventProtocol.ACTION_DOWN);
            }));
            assertThat(sink.event).isEqualTo("buttonDown");
            assertThat(sink.first).isEqualTo(currentButton);

            dispatcher.dispatch(EventProtocol.TYPE_MOUSE_BTN, input(out -> {
                out.writeByte(currentButton); out.writeByte(EventProtocol.ACTION_UP);
            }));
            assertThat(sink.event).isEqualTo("buttonUp");
            assertThat(sink.first).isEqualTo(currentButton);
        }
    }

    @Test public void decodesHorizontalAndVerticalWheelBoundaryValues() throws Exception {
        FakeSink sink = new FakeSink();
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(sink);

        dispatcher.dispatch(EventProtocol.TYPE_MOUSE_WHEEL, input(out -> {
            out.writeShort(Short.MAX_VALUE); out.writeShort(Short.MIN_VALUE);
        }));

        assertThat(sink.event).isEqualTo("wheel");
        assertThat(sink.first).isEqualTo(Short.MAX_VALUE);
        assertThat(sink.second).isEqualTo(Short.MIN_VALUE);
    }

    @Test public void preservesSinkFailures() throws Exception {
        IOException expected = new IOException("device write failed");
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(new ThrowingSink(expected));

        IOException actual = assertThrows(IOException.class, () -> dispatcher.dispatch(
            EventProtocol.TYPE_MOUSE_MOVE,
            input(out -> { out.writeInt(1); out.writeInt(2); })
        ));

        assertThat(actual).isSameInstanceAs(expected);
    }

    @Test public void identifiesEveryTruncatedEventPayload() {
        assertTruncated(EventProtocol.TYPE_KEY_EVENT, new byte[5], "keyboard");
        assertTruncated(EventProtocol.TYPE_MOUSE_MOVE, new byte[7], "mouse-move");
        assertTruncated(EventProtocol.TYPE_MOUSE_BTN, new byte[1], "mouse-button");
        assertTruncated(EventProtocol.TYPE_MOUSE_WHEEL, new byte[3], "mouse-wheel");
    }

    @Test public void rejectsUnsupportedEventsActionsAndButtonsWithUsefulMessages() {
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(new FakeSink());

        IOException typeFailure = assertThrows(IOException.class, () -> dispatcher.dispatch((byte) 99,
            new DataInputStream(new ByteArrayInputStream(new byte[0]))));
        assertThat(typeFailure).hasMessageThat().isEqualTo("Unsupported UHID event type: 99");

        IOException keyFailure = assertThrows(IOException.class, () -> dispatcher.dispatch(
            EventProtocol.TYPE_KEY_EVENT,
            input(out -> { out.writeInt(0x123456); out.writeByte(99); out.writeByte(0); })
        ));
        assertThat(keyFailure).hasMessageThat().isEqualTo("Unsupported key action: 99");

        IOException actionFailure = assertThrows(IOException.class, () -> dispatcher.dispatch(
            EventProtocol.TYPE_MOUSE_BTN,
            input(out -> { out.writeByte(1); out.writeByte(99); })
        ));
        assertThat(actionFailure).hasMessageThat().isEqualTo("Unsupported mouse action: 99");

        for (int button : new int[] {0, 4, 255}) {
            IOException buttonFailure = assertThrows(IOException.class, () -> dispatcher.dispatch(
                EventProtocol.TYPE_MOUSE_BTN,
                input(out -> { out.writeByte(button); out.writeByte(EventProtocol.ACTION_DOWN); })
            ));
            assertThat(buttonFailure).hasMessageThat()
                .isEqualTo("Unsupported mouse button: " + button);
        }
    }

    private void assertTruncated(byte type, byte[] payload, String event) {
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(new FakeSink());

        IOException error = assertThrows(IOException.class, () -> dispatcher.dispatch(type,
            new DataInputStream(new ByteArrayInputStream(payload))));

        assertThat(error).hasMessageThat().isEqualTo("Truncated UHID " + event + " event");
        assertThat(error).hasCauseThat().isInstanceOf(EOFException.class);
    }
}
