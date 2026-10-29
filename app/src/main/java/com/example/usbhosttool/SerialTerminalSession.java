package com.example.usbhosttool;

import com.termux.terminal.*;
import java.util.Arrays;

final class SerialTerminalSession extends TerminalOutput implements TerminalSession {
    private final TerminalSessionClient client;
    private final TerminalEmulator emulator;
    private SerialPortManager manager;
    SerialTerminalSession(TerminalSessionClient client) {
        this.client = client;
        emulator = new TerminalEmulator(this, 80, 24, 8, 16, 3000, client);
    }
    void bind(SerialPortManager manager) { this.manager = manager; }
    void append(byte[] bytes) { emulator.append(bytes, bytes.length); client.onTextChanged(this); }
    public TerminalEmulator getEmulator() { return emulator; }
    public void updateSize(int columns, int rows, int width, int height) { emulator.resize(columns, rows, width, height); }
    public void write(byte[] bytes, int offset, int count) {
        if (manager == null || !manager.send(Arrays.copyOfRange(bytes, offset, offset + count))) client.onBell(this);
    }
    public void writeCodePoint(boolean alt, int cp) { write((alt ? "\033" : "") + new String(Character.toChars(cp))); }
    public void titleChanged(String oldTitle, String title) { client.onTitleChanged(this); }
    public void onCopyTextToClipboard(String text) { client.onCopyTextToClipboard(this, text); }
    public void onPasteTextFromClipboard() { client.onPasteTextFromClipboard(this); }
    public void onBell() { client.onBell(this); }
    public void onColorsChanged() { client.onColorsChanged(this); }
}
