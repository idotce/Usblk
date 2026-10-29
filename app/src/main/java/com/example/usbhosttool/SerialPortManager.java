package com.example.usbhosttool;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One port, bounded UI buffer and TX queue. No serial I/O is performed on the UI thread. */
final class SerialPortManager implements AutoCloseable {
    interface Listener { void onStatus(String message); void onReady(); void onClosed(String reason); }
    static final class Config {
        int baud, dataBits, stopBits, parity;
        boolean dtr, rts;
        UsbSerialPort.FlowControl flow = UsbSerialPort.FlowControl.NONE;
    }
    private final UsbManager usbManager;
    private final UsbSerialPort port;
    private final Config config;
    private final Listener listener;
    private final File log;
    private final Object bufferLock = new Object(), logLock = new Object();
    private final byte[] ring = new byte[256 * 1024];
    private int head, size;
    private long rx, tx, dropped, logSize;
    private BufferedOutputStream journal;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean ready;
    private volatile UsbDeviceConnection connection;
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64), r -> new Thread(r, "serial-writer"), new ThreadPoolExecutor.AbortPolicy());

    SerialPortManager(UsbManager manager, UsbSerialPort port, Config config, File log, Listener listener) {
        this.usbManager = manager; this.port = port; this.config = config; this.log = log; this.listener = listener;
    }
    void start() { new Thread(this::readLoop, "serial-reader").start(); }
    boolean isReady() { return ready && !closed.get(); }
    private void readLoop() {
        String reason = "串口已断开";
        try {
            synchronized (logLock) { journal = new BufferedOutputStream(new FileOutputStream(log)); }
            UsbDeviceConnection opened = usbManager.openDevice(port.getDriver().getDevice());
            connection = opened;
            if (opened == null) throw new IOException("无法打开 USB 设备，请检查权限");
            if (closed.get()) { opened.close(); return; }
            port.open(opened);
            port.setParameters(config.baud, config.dataBits, config.stopBits, config.parity);
            if (!port.getSupportedFlowControl().contains(config.flow)) throw new IOException("驱动不支持所选流控");
            port.setFlowControl(config.flow);
            java.util.EnumSet<UsbSerialPort.ControlLine> lines = port.getSupportedControlLines();
            if (lines.contains(UsbSerialPort.ControlLine.DTR) && config.flow != UsbSerialPort.FlowControl.DTR_DSR) port.setDTR(config.dtr);
            else if (config.dtr) throw new IOException("当前流控或驱动不允许手动 DTR");
            if (lines.contains(UsbSerialPort.ControlLine.RTS) && config.flow != UsbSerialPort.FlowControl.RTS_CTS) port.setRTS(config.rts);
            else if (config.rts) throw new IOException("当前流控或驱动不允许手动 RTS");
            if (closed.get()) return;
            ready = true;
            listener.onReady();
            byte[] buffer = new byte[16384];
            while (!closed.get()) {
                int count = port.read(buffer, 200);
                if (count <= 0) continue;
                byte[] data = Arrays.copyOf(buffer, count);
                synchronized (bufferLock) {
                    rx += count;
                    for (byte value : data) {
                        if (size == ring.length) { head = (head + 1) % ring.length; size--; dropped++; }
                        ring[(head + size++) % ring.length] = value;
                    }
                }
                record("RX", data);
            }
        } catch (Exception e) {
            if (!closed.get()) reason = "串口失败：" + e.getMessage();
        } finally {
            close();
            try { port.close(); } catch (Exception ignored) { }
            synchronized (logLock) {
                try { if (journal != null) journal.close(); } catch (IOException ignored) { }
                journal = null;
            }
            listener.onClosed(reason);
        }
    }
    boolean send(byte[] bytes) {
        if (!isReady() || bytes.length == 0 || bytes.length > 65536) return false;
        byte[] data = bytes.clone();
        try {
            writer.execute(() -> {
                if (!isReady()) return;
                try {
                    port.write(data, 2000);
                    synchronized (bufferLock) { tx += data.length; }
                    record("TX", data);
                } catch (Exception e) {
                    listener.onStatus("发送失败，可能已部分发送：" + e.getMessage()); close();
                }
            });
            return true;
        } catch (RejectedExecutionException e) { return false; }
    }
    private void record(String direction, byte[] data) {
        synchronized (logLock) {
            if (journal == null || logSize >= 8 * 1024 * 1024) return;
            try {
                byte[] entry = (System.currentTimeMillis() + " " + direction + " " + SerialData.hex(data) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                journal.write(entry); logSize += entry.length;
                if (logSize >= 8 * 1024 * 1024) listener.onStatus("日志达到 8 MiB 上限，停止记录；串口仍继续接收");
            } catch (IOException e) { listener.onStatus("日志写入失败：" + e.getMessage()); }
        }
    }
    byte[] drain() {
        synchronized (bufferLock) {
            int count = Math.min(size, 16384);
            byte[] data = new byte[count];
            for (int i = 0; i < count; i++) data[i] = ring[(head + i) % ring.length];
            head = (head + count) % ring.length; size -= count;
            return data;
        }
    }
    long[] counters() { synchronized (bufferLock) { return new long[]{rx, tx, dropped}; } }
    void clearDisplayBuffer() { synchronized (bufferLock) { head = size = 0; } }
    void export(OutputStream destination) throws IOException {
        long remaining;
        synchronized (logLock) {
            if (journal != null) journal.flush();
            remaining = logSize;
        }
        // Copy a fixed prefix without holding the receive log lock across slow document providers.
        try (InputStream source = new FileInputStream(log)) {
            byte[] bytes = new byte[16384];
            while (remaining > 0) {
                int count = source.read(bytes, 0, (int) Math.min(bytes.length, remaining));
                if (count < 0) throw new IOException("日志不完整");
                destination.write(bytes, 0, count); remaining -= count;
            }
        }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ready = false;
        writer.shutdownNow();
        UsbDeviceConnection active = connection;
        if (active != null) active.close();
    }
}
