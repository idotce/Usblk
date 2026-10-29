package com.termux.terminal;

/** Transport-independent session contract added for USB ADB; no local subprocess or JNI. */
public interface TerminalSession {
    TerminalEmulator getEmulator();
    void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);
    void write(String text);
    void writeCodePoint(boolean altDown, int codePoint);
    void onCopyTextToClipboard(String text);
    void onPasteTextFromClipboard();
}
