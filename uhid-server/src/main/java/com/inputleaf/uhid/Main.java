package com.inputleaf.uhid;

public class Main {
    public static void main(String[] args) {
        try (UhidServer server = new UhidServer()) {
            server.run();
        } catch (Exception e) {
            System.err.println("UHID server error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
