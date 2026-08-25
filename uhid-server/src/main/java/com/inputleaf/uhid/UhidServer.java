package com.inputleaf.uhid;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class UhidServer implements Closeable {
    private static final String SOCKET_NAME = "inputleaf_uhid";
    private static final String EXPECTED_PACKAGE = "com.inputleaf.android";
    private static final String READY_LINE = "READY";
    private static final byte[] READY_MESSAGE = (READY_LINE + "\n").getBytes(StandardCharsets.US_ASCII);

    private final KeyboardDevice keyboard;
    private final MouseDevice mouse;
    private final UhidEventDispatcher dispatcher;

    public UhidServer() throws IOException {
        KeyboardDevice createdKeyboard = new KeyboardDevice();
        MouseDevice createdMouse;
        try {
            createdMouse = new MouseDevice();
        } catch (IOException | RuntimeException | Error creationFailure) {
            closeAfterFailure(createdKeyboard, creationFailure);
            throw creationFailure;
        }

        keyboard = createdKeyboard;
        mouse = createdMouse;
        dispatcher = createDispatcher();
    }

    UhidServer(KeyboardDevice keyboard, MouseDevice mouse) {
        this.keyboard = Objects.requireNonNull(keyboard, "keyboard");
        this.mouse = Objects.requireNonNull(mouse, "mouse");
        dispatcher = createDispatcher();
    }

    private UhidEventDispatcher createDispatcher() {
        return new UhidEventDispatcher(new UhidEventDispatcher.EventSink() {
            @Override public void keyDown(int hidUsage, byte modifiers) throws IOException {
                keyboard.keyDown(hidUsage, modifiers);
            }

            @Override public void keyUp(int hidUsage, byte modifiers) throws IOException {
                keyboard.keyUp(hidUsage, modifiers);
            }

            @Override public void mouseMove(int dx, int dy) throws IOException {
                mouse.move(dx, dy);
            }

            @Override public void mouseButtonDown(byte button) throws IOException {
                mouse.buttonDown(button);
            }

            @Override public void mouseButtonUp(byte button) throws IOException {
                mouse.buttonUp(button);
            }

            @Override public void mouseWheel(short deltaX, short deltaY) throws IOException {
                mouse.wheel(deltaX, deltaY);
            }
        });
    }

    public void run() throws IOException {
        android.net.LocalServerSocket server = new android.net.LocalServerSocket(SOCKET_NAME);
        Throwable serverFailure = null;
        try {
            System.out.println(READY_LINE);
            System.out.flush();

            android.net.LocalSocket client = server.accept();
            Throwable clientFailure = null;
            try {
                verifyPeerIdentity(client);
                client.getOutputStream().write(READY_MESSAGE);
                client.getOutputStream().flush();

                runSession(new DataInputStream(client.getInputStream()), dispatcher);
            } catch (IOException | RuntimeException | Error failure) {
                clientFailure = failure;
                throw failure;
            } finally {
                close(client, clientFailure);
            }
        } catch (IOException | RuntimeException | Error failure) {
            serverFailure = failure;
            throw failure;
        } finally {
            close(server, serverFailure);
        }
    }

    static void runSession(DataInputStream input, UhidEventDispatcher dispatcher) throws IOException {
        while (true) {
            byte type;
            try {
                type = input.readByte();
            } catch (EOFException disconnected) {
                return;
            }
            if (type == EventProtocol.TYPE_SHUTDOWN) return;
            dispatcher.dispatch(type, input);
        }
    }

    private static void close(android.net.LocalSocket socket, Throwable failure) throws IOException {
        try {
            socket.close();
        } catch (IOException closeFailure) {
            if (failure == null) throw closeFailure;
            failure.addSuppressed(closeFailure);
        }
    }

    private static void close(android.net.LocalServerSocket socket, Throwable failure) throws IOException {
        try {
            socket.close();
        } catch (IOException closeFailure) {
            if (failure == null) throw closeFailure;
            failure.addSuppressed(closeFailure);
        }
    }

    private static void closeAfterFailure(Closeable resource, Throwable failure) {
        try {
            resource.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private void verifyPeerIdentity(android.net.LocalSocket client) {
        int pid = -1;
        try {
            android.net.Credentials credentials = client.getPeerCredentials();
            pid = credentials.getPid();
            byte[] cmdline = Files.readAllBytes(Paths.get("/proc/" + pid + "/cmdline"));
            String processName = firstArgument(cmdline);
            if (!isAllowedProcessName(processName)) {
                throw new SecurityException("Rejected connection from unknown process: " + processName);
            }
        } catch (IOException failure) {
            String peer = pid < 0 ? "unknown" : Integer.toString(pid);
            throw new SecurityException("Cannot verify peer PID " + peer, failure);
        }
    }

    static String firstArgument(byte[] cmdline) {
        int end = 0;
        while (end < cmdline.length && cmdline[end] != 0) end++;
        return new String(cmdline, 0, end, StandardCharsets.UTF_8);
    }

    static boolean isAllowedProcessName(String processName) {
        return processName.equals(EXPECTED_PACKAGE) || processName.startsWith(EXPECTED_PACKAGE + ":");
    }

    @Override public void close() throws IOException {
        IOException failure = null;
        try {
            keyboard.close();
        } catch (IOException keyboardFailure) {
            failure = keyboardFailure;
        }
        try {
            mouse.close();
        } catch (IOException mouseFailure) {
            if (failure == null) {
                failure = mouseFailure;
            } else {
                failure.addSuppressed(mouseFailure);
            }
        }
        if (failure != null) throw failure;
    }
}
