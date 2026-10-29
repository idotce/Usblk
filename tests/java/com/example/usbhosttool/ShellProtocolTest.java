package com.example.usbhosttool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Run with javac/java; no Android runtime or test dependencies. */
public final class ShellProtocolTest {
    public static void main(String[] args) throws Exception {
        byte[] text = "中文\033[31mred\033[0m".getBytes("UTF-8");
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.write(ShellProtocol.frame(1, text));
        wire.write(ShellProtocol.frame(2, new byte[0]));
        wire.write(ShellProtocol.frame(2, new byte[]{13, 10}));
        wire.write(ShellProtocol.frame(3, new byte[]{7}));
        byte[] stream = wire.toByteArray();
        for (int chunk = 1; chunk <= stream.length; chunk++) {
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            int[] exit = {-1};
            ShellProtocol parser = new ShellProtocol(new ShellProtocol.Listener() {
                public void onData(byte[] bytes) { received.write(bytes, 0, bytes.length); }
                public void onExit(int status) { exit[0] = status; }
            });
            for (int i = 0; i < stream.length; i += chunk)
                parser.accept(Arrays.copyOfRange(stream, i, Math.min(i + chunk, stream.length)));
            ByteArrayOutputStream expected = new ByteArrayOutputStream();
            expected.write(text); expected.write(new byte[]{13, 10});
            if (!Arrays.equals(expected.toByteArray(), received.toByteArray()) || exit[0] != 7)
                throw new AssertionError("Fragment size " + chunk);
        }
        for (byte[] invalid : new byte[][]{
                {1, -1, -1, -1, -1}, {9, 0, 0, 0, 0}, {3, 2, 0, 0, 0}}) {
            ShellProtocol parser = new ShellProtocol(new ShellProtocol.Listener() {
                public void onData(byte[] bytes) { }
                public void onExit(int status) { }
            });
            try { parser.accept(invalid); throw new AssertionError("Invalid frame accepted"); }
            catch (IOException expected) { }
        }
        System.out.println("ShellProtocolTest passed: all fragmentation sizes, UTF-8/ANSI, empty frames, exit and invalid headers");
    }
}
