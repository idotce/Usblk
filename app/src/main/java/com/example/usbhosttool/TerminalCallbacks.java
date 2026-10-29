package com.example.usbhosttool;

import android.view.KeyEvent;
import android.view.MotionEvent;
import com.termux.terminal.TerminalSession;

/** Shared defaults for terminal view and emulator callbacks. */
abstract class TerminalCallbacks implements com.termux.view.TerminalViewClient, com.termux.terminal.TerminalSessionClient {
    @Override public void onTextChanged(TerminalSession changedSession) {  }
    @Override public void onTitleChanged(TerminalSession changedSession) {  }
    @Override public void onSessionFinished(TerminalSession finishedSession) {  }
    @Override public void onCopyTextToClipboard(TerminalSession session, String text) {  }
    @Override public void onPasteTextFromClipboard(TerminalSession session) {  }
    @Override public void onBell(TerminalSession session) {  }
    @Override public void onColorsChanged(TerminalSession session) {  }
    @Override public void onTerminalCursorStateChange(boolean state) {  }
    @Override public void setTerminalShellPid(TerminalSession session, int pid) {  }
    @Override public Integer getTerminalCursorStyle() { return null; }
    @Override public void logError(String tag, String message) { android.util.Log.w(tag, message); }
    @Override public void logWarn(String tag, String message) { android.util.Log.w(tag, message); }
    @Override public void logInfo(String tag, String message) { android.util.Log.w(tag, message); }
    @Override public void logDebug(String tag, String message) { android.util.Log.w(tag, message); }
    @Override public void logVerbose(String tag, String message) { android.util.Log.w(tag, message); }
    @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { android.util.Log.w(tag, message); }
    @Override public void logStackTrace(String tag, Exception e) { android.util.Log.w(tag, String.valueOf(e)); }
    @Override public float onScale(float scale) { return scale; }
    @Override public void onSingleTapUp(MotionEvent e) {  }
    @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override public boolean shouldEnforceCharBasedInput() { return false; }
    @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override public boolean isTerminalViewSelected() { return true; }
    @Override public void copyModeChanged(boolean copyMode) {  }
    @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
    @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
    @Override public boolean onLongPress(MotionEvent event) { return false; }
    @Override public boolean readControlKey() { return false; }
    @Override public boolean readAltKey() { return false; }
    @Override public boolean readShiftKey() { return false; }
    @Override public boolean readFnKey() { return false; }
    @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
    @Override public void onEmulatorSet() {  }
}
