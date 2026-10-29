package com.example.usbhosttool;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import java.util.Arrays;

/** Emulator and view are used only on the main thread; USB I/O belongs to AdbShellManager. */
final class AdbTerminalSession extends TerminalOutput implements TerminalSession {
    private final TerminalSessionClient client;
    private final TerminalEmulator emulator;
    private AdbShellManager transport;
    AdbTerminalSession(TerminalSessionClient client) {
        this.client = client;
        emulator = new TerminalEmulator(this, 80, 24, 8, 16, 3000, client);
    }
    void bind(AdbShellManager transport) {
        this.transport = transport;
        if (transport != null) transport.resize(emulator.mColumns, emulator.mRows);
    }
    void append(byte[] bytes) { emulator.append(bytes, bytes.length); client.onTextChanged(this); }
    @Override public TerminalEmulator getEmulator() { return emulator; }
    @Override public void updateSize(int columns, int rows, int width, int height) {
        emulator.resize(columns, rows, width, height);
        if (transport != null) transport.resize(columns, rows);
    }
    @Override public void write(byte[] data, int offset, int count) {
        if (transport != null && !transport.writeBytes(Arrays.copyOfRange(data, offset, offset + count)))
            client.onBell(this);
    }
    @Override public void writeCodePoint(boolean altDown, int codePoint) {
        write((altDown ? "\033" : "") + new String(Character.toChars(codePoint)));
    }
    @Override public void titleChanged(String oldTitle, String newTitle) { client.onTitleChanged(this); }
    @Override public void onCopyTextToClipboard(String text) { client.onCopyTextToClipboard(this, text); }
    @Override public void onPasteTextFromClipboard() { client.onPasteTextFromClipboard(this); }
    @Override public void onBell() { client.onBell(this); }
    @Override public void onColorsChanged() { client.onColorsChanged(this); }
}
