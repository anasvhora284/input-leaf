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
        Throwable serverFailure = null;
        try {
            System.out.println("READY");
            System.out.flush();

            android.net.LocalSocket client = server.accept();
            Throwable clientFailure = null;
            try {
                verifyPeerIdentity(client);
                client.getOutputStream().write("READY\n".getBytes());
                client.getOutputStream().flush();

                DataInputStream input = new DataInputStream(client.getInputStream());
                while (true) {
                    byte type = input.readByte();
                    if (type == EventProtocol.TYPE_SHUTDOWN) break;
                    dispatcher.dispatch(type, input);
                }
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
