package com.inputleaf.uhid;

import java.io.*;
import java.nio.file.Files;

public class UhidServer implements Closeable {
    private final KeyboardDevice keyboard;
    private final MouseDevice mouse;

    public UhidServer() throws IOException {
        keyboard = new KeyboardDevice();
        mouse = new MouseDevice();
    }

    public void run() throws IOException {
        // Use Android's LocalServerSocket for abstract namespace
        android.net.LocalServerSocket server = new android.net.LocalServerSocket("inputleaf_uhid");
        System.out.println("READY");
        System.out.flush();

        android.net.LocalSocket client = server.accept();
        verifyPeerIdentity(client);
        // Send socket-level READY after PID verification
        client.getOutputStream().write("READY\n".getBytes());
        client.getOutputStream().flush();

        DataInputStream din = new DataInputStream(client.getInputStream());
        while (true) {
            byte type = din.readByte();
            if (type == EventProtocol.TYPE_SHUTDOWN) break;
            handleEvent(type, din);
        }
        client.close();
        server.close();
    }

    private void verifyPeerIdentity(android.net.LocalSocket client) throws IOException {
        android.net.Credentials cred = client.getPeerCredentials();
        int pid = cred.getPid();
        try {
            byte[] cmdline = Files.readAllBytes(java.nio.file.Paths.get("/proc/" + pid + "/cmdline"));
            String cmd = new String(cmdline).replace('\0', ' ').trim();
            if (!cmd.contains("com.inputleaf.android")) {
                throw new SecurityException("Rejected connection from unknown process: " + cmd);
            }
        } catch (IOException e) {
            throw new SecurityException("Cannot verify peer PID " + pid);
        }
    }

    private void handleEvent(byte type, DataInputStream din) throws IOException {
        switch (type) {
            case EventProtocol.TYPE_KEY_EVENT: {
                int keysym = din.readInt();
                byte action = din.readByte();
                byte modifiers = din.readByte();
                Integer hid = KeysymToHid.lookup(keysym);
                if (hid == null) break;
                if (action == EventProtocol.ACTION_DOWN) keyboard.keyDown(hid, modifiers);
                else keyboard.keyUp(hid);
                break;
            }
            case EventProtocol.TYPE_MOUSE_MOVE:
                mouse.move(din.readInt(), din.readInt());
                break;
            case EventProtocol.TYPE_MOUSE_BTN: {
                byte btn = din.readByte(), act = din.readByte();
                if (act == EventProtocol.ACTION_DOWN) mouse.buttonDown(btn);
                else mouse.buttonUp(btn);
                break;
            }
            case EventProtocol.TYPE_MOUSE_WHEEL:
                // Protocol: [2B deltaX (horizontal)][2B deltaY (vertical)]
                din.readShort(); // deltaX — horizontal scroll, ignored in v1
                mouse.wheel(din.readShort()); // deltaY — vertical scroll
                break;
        }
    }

    @Override public void close() throws IOException {
        keyboard.close(); mouse.close();
    }
}
