package com.example.usbhosttool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Incremental shell_v2 framing, independent of USB/ADB packet boundaries. */
final class ShellProtocol {
    interface Listener { void onData(byte[] bytes); void onExit(int status); }
    private final byte[] header = new byte[5];
    private int headerSize, id, remaining;
    private final Listener listener;
    ShellProtocol(Listener listener) { this.listener = listener; }
    static byte[] frame(int id, byte[] data) {
        return ByteBuffer.allocate(5 + data.length).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) id).putInt(data.length).put(data).array();
    }
    void accept(byte[] data) throws IOException {
        int offset = 0;
        while (offset < data.length) {
            if (headerSize < 5) {
                int n = Math.min(5 - headerSize, data.length - offset);
                System.arraycopy(data, offset, header, headerSize, n);
                offset += n;
                headerSize += n;
                if (headerSize < 5) continue;
                id = header[0] & 255;
                remaining = ByteBuffer.wrap(header, 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (remaining < 0 || remaining > 16 * 1024 * 1024 || (id != 1 && id != 2 && id != 3)
                        || (id == 3 && remaining != 1)) throw new IOException("Shell v2 报文无效");
                if (remaining == 0) { headerSize = 0; continue; }
            }
            int n = Math.min(remaining, data.length - offset);
            if (n > 0) {
                if (id == 3) listener.onExit(data[offset] & 255);
                else listener.onData(Arrays.copyOfRange(data, offset, offset + n));
                offset += n;
                remaining -= n;
            }
            if (remaining == 0) headerSize = 0;
        }
    }
}
