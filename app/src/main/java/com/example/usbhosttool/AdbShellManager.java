package com.example.usbhosttool;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Base64;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbCrypto;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** One USB ADB connection and one interactive legacy shell stream. */
public final class AdbShellManager implements AutoCloseable {
    public interface Listener {
        void onStatus(String text);
        void onConnected();
        void onOutput(String text);
        void onDisconnected(String reason);
    }

    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f, WRTE = 0x45545257, CLSE = 0x45534c43;
    private static final int LIMIT = 4096;
    private final Context context;
    private final UsbDeviceConnection usb;
    private final UsbInterface iface;
    private final Listener listener;
    private final BlockingQueue<byte[]> pending = new LinkedBlockingQueue<>(128);
    private UsbEndpoint in, out;
    private volatile boolean closed;
    private volatile boolean ready;
    private int remoteId;
    private boolean writeReady;
    private final Object writeLock = new Object();

    public AdbShellManager(Context context, UsbDeviceConnection usb, UsbInterface iface, Listener listener) {
        this.context = context.getApplicationContext();
        this.usb = usb;
        this.iface = iface;
        this.listener = listener;
    }

    public void start() { new Thread(this::run, "adb-usb-reader").start(); }

    public boolean writeCommand(String command) {
        return writeInput(command + "\n");
    }

    public boolean writeInput(String text) {
        if (!ready || closed) return false;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LIMIT) return false;
        return pending.offer(bytes);
    }

    private void run() {
        String reason = "连接已关闭";
        try {
            for (int i = 0; i < iface.getEndpointCount(); i++) {
                UsbEndpoint ep = iface.getEndpoint(i);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) in = ep; else out = ep;
            }
            if (in == null || out == null || !usb.claimInterface(iface, true)) throw new IOException("无法占用 ADB USB 接口");
            AdbCrypto crypto = loadKey();
            send(CNXN, 0x01000000, LIMIT, "host::\0".getBytes(StandardCharsets.UTF_8));
            boolean signed = false;
            while (!closed) {
                Packet packet = readPacket();
                if (packet.command == CNXN) {
                    if (packet.arg0 < 0x01000000 || packet.arg1 < LIMIT) throw new IOException("目标 ADB 协议版本或缓冲区不受支持");
                    break;
                }
                if (packet.command != AUTH || packet.arg0 != 1 || packet.data.length != 20) throw new IOException("ADB 握手响应无效");
                if (!signed) {
                    send(AUTH, 2, 0, crypto.signAdbTokenPayload(packet.data));
                    signed = true;
                } else {
                    listener.onStatus("请在目标设备上确认 USB 调试授权");
                    send(AUTH, 3, 0, crypto.getAdbPublicKeyPayload());
                }
            }
            if (closed) return;
            send(OPEN, 1, 0, "shell:\0".getBytes(StandardCharsets.UTF_8));
            Packet first = readPacket();
            if (first.command != OKAY || first.arg1 != 1 || first.arg0 == 0) throw new IOException("目标设备拒绝打开 Shell");
            remoteId = first.arg0;
            synchronized (writeLock) { writeReady = true; }
            ready = true;
            listener.onConnected();
            new Thread(this::writeLoop, "adb-usb-writer").start();
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
            ByteBuffer bytes = ByteBuffer.allocate(LIMIT + 4);
            CharBuffer chars = CharBuffer.allocate(LIMIT + 4);
            while (!closed) {
                Packet packet = readPacket();
                if (packet.arg0 != remoteId || packet.arg1 != 1) throw new IOException("ADB 流编号无效");
                if (packet.command == WRTE) {
                    bytes.put(packet.data);
                    bytes.flip();
                    decoder.decode(bytes, chars, false);
                    bytes.compact();
                    chars.flip();
                    if (chars.hasRemaining()) listener.onOutput(chars.toString());
                    chars.clear();
                    send(OKAY, 1, remoteId, new byte[0]);
                } else if (packet.command == OKAY) {
                    synchronized (writeLock) { writeReady = true; writeLock.notifyAll(); }
                } else if (packet.command == CLSE) {
                    send(CLSE, 1, remoteId, new byte[0]);
                    reason = "远端 Shell 已退出";
                    break;
                } else throw new IOException("未知 ADB 报文");
            }
        } catch (Exception e) {
            if (!closed) reason = "ADB 连接失败：" + e.getMessage();
        } finally {
            close();
            listener.onDisconnected(reason);
        }
    }

    private static final Object KEY_LOCK = new Object();

    private AdbCrypto loadKey() throws Exception {
        synchronized (KEY_LOCK) {
        File privateKey = new File(context.getNoBackupFilesDir(), "adb-private.key");
        File publicKey = new File(context.getNoBackupFilesDir(), "adb-public.key");
        AdbBase64 base64 = data -> Base64.encodeToString(data, Base64.NO_WRAP);
        if (privateKey.exists() && publicKey.exists()) return AdbCrypto.loadAdbKeyPair(base64, privateKey, publicKey);
        if (privateKey.exists() || publicKey.exists()) throw new IOException("ADB 密钥文件不完整");
        AdbCrypto crypto = AdbCrypto.generateAdbKeyPair(base64);
        crypto.saveAdbKeyPair(privateKey, publicKey);
        return crypto;
        }
    }

    private void writeLoop() {
        try {
            while (!closed) {
                byte[] data = pending.take();
                synchronized (writeLock) {
                    while (!closed && !writeReady) writeLock.wait();
                    if (closed) return;
                    writeReady = false;
                }
                send(WRTE, 1, remoteId, data);
            }
        } catch (Exception e) {
            if (!closed) { listener.onStatus("发送失败：" + e.getMessage()); close(); }
        }
    }

    private static final class Packet {
        int command, arg0, arg1;
        byte[] data;
    }

    private Packet readPacket() throws IOException {
        ByteBuffer h = ByteBuffer.wrap(readExact(24)).order(ByteOrder.LITTLE_ENDIAN);
        Packet p = new Packet();
        p.command = h.getInt(); p.arg0 = h.getInt(); p.arg1 = h.getInt();
        int length = h.getInt(), checksum = h.getInt(), magic = h.getInt();
        if (magic != (p.command ^ -1) || length < 0 || length > LIMIT) throw new IOException("ADB 报文头无效");
        p.data = readExact(length);
        int sum = 0;
        for (byte b : p.data) sum += b & 255;
        if (sum != checksum) throw new IOException("ADB 报文校验失败");
        return p;
    }

    private byte[] readExact(int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length && !closed) {
            int n = usb.bulkTransfer(in, data, offset, length - offset, 0);
            if (n <= 0) throw new IOException("USB 读取失败");
            offset += n;
        }
        if (closed) throw new IOException("连接已关闭");
        return data;
    }

    private synchronized void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        int checksum = 0;
        for (byte b : data) checksum += b & 255;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(checksum).putInt(command ^ -1);
        writeExact(h.array());
        if (data.length > 0) {
            writeExact(data);
            if (data.length % out.getMaxPacketSize() == 0
                    && usb.bulkTransfer(out, new byte[0], 0, 5000) < 0)
                throw new IOException("USB 零长度包发送失败");
        }
    }

    private void writeExact(byte[] data) throws IOException {
        int offset = 0;
        while (offset < data.length && !closed) {
            int n = usb.bulkTransfer(out, data, offset, data.length - offset, 5000);
            if (n <= 0) throw new IOException("USB 写入失败");
            offset += n;
        }
        if (closed) throw new IOException("连接已关闭");
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        ready = false;
        synchronized (writeLock) { writeLock.notifyAll(); }
        pending.offer(new byte[0]);
        usb.close();
    }
}
