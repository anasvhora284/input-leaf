package com.inputleaf.uhid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.Test;

public class UhidEventDispatcherTest {
    private static class FakeSink implements UhidEventDispatcher.EventSink {
        String event;
        int first;
        int second;
        byte modifiers;

        @Override public void keyDown(int hidUsage, byte modifiers) { event = "keyDown"; first = hidUsage; this.modifiers = modifiers; }
        @Override public void keyUp(int hidUsage) { event = "keyUp"; first = hidUsage; }
        @Override public void mouseMove(int dx, int dy) { event = "move"; first = dx; second = dy; }
        @Override public void mouseButtonDown(byte button) { event = "buttonDown"; first = button; }
        @Override public void mouseButtonUp(byte button) { event = "buttonUp"; first = button; }
        @Override public void mouseWheel(short delta) { event = "wheel"; first = delta; }
    }

    private DataInputStream input(ThrowingConsumer<DataOutputStream> writer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writer.accept(output);
        return new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private interface ThrowingConsumer<T> { void accept(T value) throws Exception; }

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
            out.writeInt('A'); out.writeByte(EventProtocol.ACTION_UP); out.writeByte(0);
        }));
        assertThat(sink.event).isEqualTo("keyUp");

        sink.event = null;
        dispatcher.dispatch(EventProtocol.TYPE_KEY_EVENT, input(out -> {
            out.writeInt(0x123456); out.writeByte(EventProtocol.ACTION_DOWN); out.writeByte(0);
        }));
        assertThat(sink.event).isNull();
    }

    @Test public void decodesMouseEvents() throws Exception {
        FakeSink sink = new FakeSink();
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(sink);

        dispatcher.dispatch(EventProtocol.TYPE_MOUSE_MOVE, input(out -> { out.writeInt(-5); out.writeInt(7); }));
        assertThat(sink.event).isEqualTo("move");
        assertThat(sink.first).isEqualTo(-5);
        assertThat(sink.second).isEqualTo(7);

        dispatcher.dispatch(EventProtocol.TYPE_MOUSE_BTN, input(out -> {
            out.writeByte(3); out.writeByte(EventProtocol.ACTION_DOWN);
        }));
        assertThat(sink.event).isEqualTo("buttonDown");
        assertThat(sink.first).isEqualTo(3);

        dispatcher.dispatch(EventProtocol.TYPE_MOUSE_WHEEL, input(out -> { out.writeShort(4); out.writeShort(-8); }));
        assertThat(sink.event).isEqualTo("wheel");
        assertThat(sink.first).isEqualTo(-8);
    }

    @Test public void rejectsMalformedAndUnsupportedEvents() {
        UhidEventDispatcher dispatcher = new UhidEventDispatcher(new FakeSink());

        assertThrows(IOException.class, () -> dispatcher.dispatch(EventProtocol.TYPE_MOUSE_MOVE,
            new DataInputStream(new ByteArrayInputStream(new byte[] {0, 0, 0, 1}))));
        assertThrows(IOException.class, () -> dispatcher.dispatch((byte) 99,
            new DataInputStream(new ByteArrayInputStream(new byte[0]))));
        assertThrows(IOException.class, () -> dispatcher.dispatch(EventProtocol.TYPE_KEY_EVENT,
            input(out -> { out.writeInt('A'); out.writeByte(99); out.writeByte(0); })));
        assertThrows(IOException.class, () -> dispatcher.dispatch(EventProtocol.TYPE_MOUSE_BTN,
            input(out -> { out.writeByte(1); out.writeByte(99); })));
        assertThrows(IOException.class, () -> dispatcher.dispatch(EventProtocol.TYPE_MOUSE_BTN,
            input(out -> { out.writeByte(4); out.writeByte(EventProtocol.ACTION_DOWN); })));
    }
}
