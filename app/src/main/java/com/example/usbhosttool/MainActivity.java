package com.example.usbhosttool;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    private static final String ACTION_PERMISSION = "com.example.usbhosttool.USB_PERMISSION";
    private UsbManager usbManager;
    private android.app.Dialog terminal;
    private LinearLayout terminalContent;
    private com.termux.view.TerminalView terminalView;
    private AdbTerminalSession terminalSession;
    private LinearLayout extraKeys;
    private boolean ctrl, alt;
    private Button connectButton;
    private AdbShellManager shell;
    private String deviceName;
    private String permissionDeviceName;
    private volatile int generation;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            UsbDevice device = deviceFrom(intent);
            if (ACTION_PERMISSION.equals(intent.getAction())) {
                if (device == null || !device.getDeviceName().equals(permissionDeviceName)) return;
                permissionDeviceName = null;
                connectButton.setEnabled(true);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        && usbManager.hasPermission(device)) connect(device);
                else append("\nUSB 访问权限被拒绝。\n");
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction()) && device != null) {
                if (device.getDeviceName().equals(permissionDeviceName)) {
                    permissionDeviceName = null;
                    connectButton.setEnabled(true);
                }
                if (device.getDeviceName().equals(deviceName)) {
                    disconnect();
                    append("\nUSB 设备已断开。\n");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction()) && device != null) {
                append("\n检测到 USB 设备，点击连接即可选择 ADB 设备。\n");
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        connectButton = new Button(this);
        connectButton.setText("连接 USB ADB");
        connectButton.setOnClickListener(v -> selectDevice());
        layout.addView(connectButton);
        Button serial = new Button(this);
        serial.setText("USB 串口助手");
        serial.setOnClickListener(v -> startActivity(new Intent(this, SerialActivity.class)));
        layout.addView(serial);
        Button settings = new Button(this);
        settings.setText("设置");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        layout.addView(settings);
        Button camera = new Button(this);
        camera.setText("UVC 视频 / UAC 音频");
        camera.setOnClickListener(v -> {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 30);
                return;
            }
            startActivity(new Intent(this, CameraScreen.class));
        });
        layout.addView(camera);
        terminalContent = new LinearLayout(this);
        terminalContent.setOrientation(LinearLayout.VERTICAL);
        terminalContent.setBackgroundColor(Color.BLACK);
        terminalView = new com.termux.view.TerminalView(this, null);
        TerminalCallbacks callbacks = new TerminalCallbacks() {
            @Override public void onTextChanged(com.termux.terminal.TerminalSession session) { terminalView.onScreenUpdated(); }
            @Override public void onColorsChanged(com.termux.terminal.TerminalSession session) { terminalView.invalidate(); }
            @Override public boolean readControlKey() { boolean value = ctrl; ctrl = false; refreshModifiers(); return value; }
            @Override public boolean readAltKey() { boolean value = alt; alt = false; refreshModifiers(); return value; }
            @Override public void onSingleTapUp(android.view.MotionEvent event) { showKeyboard(); }
            @Override public void onCopyTextToClipboard(com.termux.terminal.TerminalSession session, String text) {
                ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                        .setPrimaryClip(android.content.ClipData.newPlainText("终端", text));
            }
            @Override public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession session) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0)
                    terminalSession.getEmulator().paste(clipboard.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this).toString());
            }
            @Override public void onBell(com.termux.terminal.TerminalSession session) {
                android.widget.Toast.makeText(MainActivity.this, "终端提示：请检查连接或发送队列", android.widget.Toast.LENGTH_SHORT).show();
            }
        };
        terminalView.setTerminalViewClient(callbacks);
        terminalSession = new AdbTerminalSession(callbacks);
        terminalView.attachSession(terminalSession);
        terminalContent.addView(terminalView, new LinearLayout.LayoutParams(-1, 0, 1));
        extraKeys = new LinearLayout(this);
        extraKeys.setOrientation(LinearLayout.VERTICAL);
        terminalContent.addView(extraKeys);
        applyTerminalSettings();
        Button openTerminal = new Button(this);
        openTerminal.setText("打开 ADB 终端");
        openTerminal.setOnClickListener(v -> showTerminal());
        layout.addView(openTerminal);
        WindowInsetsHelper.apply(layout);
        setContentView(layout);
        IntentFilter filter = new IntentFilter(ACTION_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        append("请连接已启用 USB 调试的 Android 设备。\n");
        UsbDevice attached = deviceFrom(getIntent());
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("adb_auto", true) && attached != null && findAdbInterface(attached) != null) requestDevice(attached);
        else openAttachedSerial(attached);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        UsbDevice device = deviceFrom(intent);
        if (device != null && findAdbInterface(device) != null) requestDevice(device);
        else openAttachedSerial(device);
    }

    private void openAttachedSerial(UsbDevice device) {
        if (device != null && com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber().probeDevice(device) != null)
            startActivity(new Intent(this, SerialActivity.class));
    }

    private UsbDevice deviceFrom(Intent intent) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
        return intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private UsbInterface findAdbInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == 255 && iface.getInterfaceSubclass() == 66 && iface.getInterfaceProtocol() == 1) return iface;
        }
        return null;
    }

    private void selectDevice() {
        java.util.ArrayList<UsbDevice> devices = new java.util.ArrayList<>();
        for (UsbDevice device : usbManager.getDeviceList().values()) if (findAdbInterface(device) != null) devices.add(device);
        if (devices.isEmpty()) { append("\n未发现 ADB 设备，请检查连接和目标设备 USB 调试设置。\n"); return; }
        if (devices.size() == 1) { requestDevice(devices.get(0)); return; }
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) names[i] = devices.get(i).getDeviceName() + " (" + devices.get(i).getVendorId() + ":" + devices.get(i).getProductId() + ")";
        new android.app.AlertDialog.Builder(this).setTitle("选择 ADB 设备").setItems(names, (dialog, which) -> requestDevice(devices.get(which))).show();
    }

    private void requestDevice(UsbDevice device) {
        if (permissionDeviceName != null) return;
        disconnect();
        if (usbManager.hasPermission(device)) { connect(device); return; }
        permissionDeviceName = device.getDeviceName();
        connectButton.setEnabled(false);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        Intent intent = new Intent(ACTION_PERMISSION).setPackage(getPackageName());
        usbManager.requestPermission(device, PendingIntent.getBroadcast(this, 0, intent, flags));
    }

    private void connect(UsbDevice device) {
        UsbInterface iface = findAdbInterface(device);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (iface == null || connection == null) {
            if (connection != null) connection.close();
            append("\n无法打开 USB ADB 设备。\n");
            return;
        }
        disconnect();
        deviceName = device.getDeviceName();
        final int current = ++generation;
        showTerminal();
        append("\n正在连接 " + deviceName + "……\n");
        shell = new AdbShellManager(this, connection, iface, new AdbShellManager.Listener() {
            @Override public void onStatus(String text) { post(current, () -> append("\n" + text + "\n")); }
            @Override public void onConnected() { post(current, () -> { terminalSession.bind(shell); append("\nADB Shell 已连接。\n"); }); }
            @Override public void onOutput(byte[] bytes) {
                // Do not ACK another USB packet until the UI consumes this one: bounded backpressure.
                java.util.concurrent.CountDownLatch consumed = new java.util.concurrent.CountDownLatch(1);
                runOnUiThread(() -> {
                    try { if (generation == current && !isFinishing()) terminalSession.append(bytes); }
                    finally { consumed.countDown(); }
                });
                try {
                    while (!consumed.await(100, java.util.concurrent.TimeUnit.MILLISECONDS))
                        if (generation != current) return;
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            @Override public void onDisconnected(String reason) { post(current, () -> { terminalSession.bind(null); shell = null; deviceName = null; append("\n" + reason + "\n"); }); }
        });
        shell.start();
    }

    private void showTerminal() {
        if (terminal == null) {
            terminal = new android.app.Dialog(this);
            terminal.setTitle("USB ADB 终端");
            terminal.setContentView(terminalContent);
            terminal.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        terminal.show();
        terminal.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        WindowInsetsHelper.apply(terminalContent);
        terminalView.requestFocus();
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("terminal_keyboard", true))
            terminalView.post(this::showKeyboard);
    }

    private void post(int current, Runnable action) { runOnUiThread(() -> { if (generation == current && !isFinishing()) action.run(); }); }
    private void append(String text) {
        terminalSession.append(text.replace("\n", "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void showKeyboard() {
        terminalView.requestFocus();
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void refreshModifiers() {
        for (int i = 0; i < extraKeys.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) extraKeys.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                Button button = (Button) row.getChildAt(j);
                String key = (String) button.getTag();
                button.setText(key + ((key.equals("CTRL") && ctrl || key.equals("ALT") && alt) ? " ●" : ""));
            }
        }
    }

    private void applyTerminalSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int size;
        try { size = Integer.parseInt(prefs.getString("terminal_font", "14")); }
        catch (NumberFormatException e) { size = 14; }
        terminalView.setTextSize((int) (Math.max(8, Math.min(32, size)) * getResources().getDisplayMetrics().scaledDensity));
        java.util.Properties colors = new java.util.Properties();
        colors.setProperty("foreground", prefs.getBoolean("terminal_green", false) ? "#00ff00" : "#ffffff");
        com.termux.terminal.TerminalColors.COLOR_SCHEME.updateWith(colors);
        terminalSession.getEmulator().mColors.reset();
        extraKeys.removeAllViews();
        String layout = prefs.getString("terminal_keys", "ESC TAB CTRL ALT HOME UP END; / - | ~ LEFT DOWN RIGHT");
        for (String line : layout.split(";", 3)) {
            LinearLayout row = new LinearLayout(this);
            extraKeys.addView(row);
            String[] keys = line.trim().split("\\s+");
            for (int i = 0; i < Math.min(keys.length, 8); i++) {
                String key = keys[i];
                Button button = new Button(this);
                button.setTag(key);
                button.setText(key);
                button.setTextSize(11);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setPadding(0, 0, 0, 0);
                button.setFocusable(false);
                button.setOnClickListener(v -> extraKey(key));
                row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
            }
        }
        terminalView.invalidate();
    }

    private void extraKey(String key) {
        if (key.equals("CTRL")) { ctrl = !ctrl; refreshModifiers(); return; }
        if (key.equals("ALT")) { alt = !alt; refreshModifiers(); return; }
        if (key.equals("KEYBOARD")) { showKeyboard(); return; }
        int code;
        switch (key) {
            case "ESC": code = android.view.KeyEvent.KEYCODE_ESCAPE; break;
            case "TAB": code = android.view.KeyEvent.KEYCODE_TAB; break;
            case "HOME": code = android.view.KeyEvent.KEYCODE_MOVE_HOME; break;
            case "END": code = android.view.KeyEvent.KEYCODE_MOVE_END; break;
            case "UP": code = android.view.KeyEvent.KEYCODE_DPAD_UP; break;
            case "DOWN": code = android.view.KeyEvent.KEYCODE_DPAD_DOWN; break;
            case "LEFT": code = android.view.KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "RIGHT": code = android.view.KeyEvent.KEYCODE_DPAD_RIGHT; break;
            case "ENTER": code = android.view.KeyEvent.KEYCODE_ENTER; break;
            default:
                for (int offset = 0; offset < key.length();) {
                    int cp = key.codePointAt(offset);
                    terminalView.inputCodePoint(0, cp, false, false);
                    offset += Character.charCount(cp);
                }
                return;
        }
        int modifiers = (ctrl ? android.view.KeyEvent.META_CTRL_ON : 0) | (alt ? android.view.KeyEvent.META_ALT_ON : 0);
        terminalView.onKeyDown(code, new android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN, code, 0, modifiers));
        ctrl = alt = false;
        refreshModifiers();
    }

    @Override protected void onResume() {
        super.onResume();
        if (terminalView != null) applyTerminalSettings();
    }
    private void disconnect() {
        generation++;
        if (shell != null) shell.close();
        shell = null;
        deviceName = null;
        if (terminalSession != null) terminalSession.bind(null);
    }
    @Override protected void onDestroy() {
        if (terminal != null) terminal.dismiss();
        disconnect();
        unregisterReceiver(receiver);
        super.onDestroy();
    }
}
