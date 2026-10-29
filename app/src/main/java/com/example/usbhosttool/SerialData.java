package com.example.usbhosttool;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict HEX input and incremental UTF-8 decoding across USB read boundaries. */
final class SerialData {
    static byte[] encode(String text, boolean hex, String ending) {
        if (text.length() > 32768) throw new IllegalArgumentException("单次输入最多 32768 个字符");
        byte[] body;
        if (hex) {
            String value = text.replaceAll("\\s", "");
            if ((value.length() & 1) != 0 || !value.matches("[0-9a-fA-F]*"))
                throw new IllegalArgumentException("HEX 需要成对的十六进制数字，例如 01 FF 0A");
            body = new byte[value.length() / 2];
            for (int i = 0; i < body.length; i++) body[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        } else body = text.getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ending.getBytes(StandardCharsets.US_ASCII);
        byte[] result = java.util.Arrays.copyOf(body, body.length + suffix.length);
        System.arraycopy(suffix, 0, result, body.length, suffix.length);
        return result;
    }
    static String hex(byte[] bytes) {
        char[] digits = "0123456789ABCDEF".toCharArray();
        StringBuilder result = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) result.append(digits[(b & 255) >>> 4]).append(digits[b & 15]).append(' ');
        return result.toString();
    }
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
    private byte[] tail = new byte[0];
    String decode(byte[] bytes) {
        ByteBuffer input = ByteBuffer.allocate(tail.length + bytes.length);
        input.put(tail).put(bytes).flip();
        CharBuffer output = CharBuffer.allocate(input.remaining() + 2);
        decoder.decode(input, output, false);
        tail = new byte[input.remaining()]; input.get(tail);
        output.flip();
        return output.toString();
    }
    void reset() { tail = new byte[0]; decoder.reset(); }
}
