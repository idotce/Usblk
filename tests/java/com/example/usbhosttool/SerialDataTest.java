package com.example.usbhosttool;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class SerialDataTest {
    public static void main(String[] args) {
        if (!Arrays.equals(SerialData.encode("00 ff 0A", true, "\r\n"), new byte[]{0, -1, 10, 13, 10})) throw new AssertionError("HEX");
        if (!Arrays.equals(SerialData.encode("中文", false, "\n"), "中文\n".getBytes(StandardCharsets.UTF_8))) throw new AssertionError("UTF-8 input");
        for (String invalid : new String[]{"A", "GG", "0x12", "FF-ZZ"}) {
            try { SerialData.encode(invalid, true, ""); throw new AssertionError("Invalid HEX accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        byte[] bytes = "中文😀\r\n".getBytes(StandardCharsets.UTF_8);
        for (int split = 0; split <= bytes.length; split++) {
            SerialData decoder = new SerialData();
            String text = decoder.decode(Arrays.copyOfRange(bytes, 0, split)) + decoder.decode(Arrays.copyOfRange(bytes, split, bytes.length));
            if (!text.equals("中文😀\r\n")) throw new AssertionError("UTF-8 split " + split);
        }
        SerialData decoder = new SerialData();
        decoder.decode(new byte[]{(byte) 0xe4}); decoder.reset();
        if (!decoder.decode(new byte[]{65}).equals("A")) throw new AssertionError("Reset");
        if (!SerialData.hex(new byte[]{0, 15, -1}).equals("00 0F FF ")) throw new AssertionError("HEX output");
        System.out.println("SerialDataTest passed: HEX validation, suffixes, every UTF-8 split, reset and byte formatting");
    }
}
