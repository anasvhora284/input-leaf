package com.inputleaf.uhid;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;

public class UhidServer implements Closeable {
    private final KeyboardDevice keyboard;
    private final MouseDevice mouse;
    private final UhidEventDispatcher dispatcher;

    public UhidServer() throws IOException {
        keyboard = new KeyboardDevice();
        mouse = new MouseDevice();
        dispatcher = new UhidEventDispatcher(new UhidEventDispatcher.EventSink() {
            @Override public void keyDown(int hidUsage, byte modifiers) throws IOException {
                keyboard.keyDown(hidUsage, modifiers);
            }

            @Override public void keyUp(int hidUsage) throws IOException {
                keyboard.keyUp(hidUsage);
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

            @Override public void mouseWheel(short delta) throws IOException {
                mouse.wheel(delta);
            }
        });
    }

    public void run() throws IOException {
        android.net.LocalServerSocket server = new android.net.LocalServerSocket("inputleaf_uhid");
        System.out.println("READY");
        System.out.flush();

        android.net.LocalSocket client = server.accept();
        verifyPeerIdentity(client);
        client.getOutputStream().write("READY\n".getBytes());
        client.getOutputStream().flush();

        DataInputStream input = new DataInputStream(client.getInputStream());
        while (true) {
            byte type = input.readByte();
            if (type == EventProtocol.TYPE_SHUTDOWN) break;
            dispatcher.dispatch(type, input);
        }
        client.close();
        server.close();
    }

    private void verifyPeerIdentity(android.net.LocalSocket client) throws IOException {
        android.net.Credentials credentials = client.getPeerCredentials();
        int pid = credentials.getPid();
        try {
            byte[] cmdline = Files.readAllBytes(java.nio.file.Paths.get("/proc/" + pid + "/cmdline"));
            String command = new String(cmdline).replace('\0', ' ').trim();
            if (!command.contains("com.inputleaf.android")) {
                throw new SecurityException("Rejected connection from unknown process: " + command);
            }
        } catch (IOException e) {
            throw new SecurityException("Cannot verify peer PID " + pid);
        }
    }

    @Override public void close() throws IOException {
        keyboard.close();
        mouse.close();
    }
}
