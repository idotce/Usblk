package com.example.usbhosttool;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String ACTION_PERMISSION = "com.example.usbhosttool.USB_PERMISSION";
    private UsbManager usbManager;
    private android.app.Dialog terminal;
    private LinearLayout terminalContent;
    private TextView output;
    private ScrollView scroll;
    private EditText input;
    private Button connectButton;
    private AdbShellManager shell;
    private String deviceName;
    private String permissionDeviceName;
    private int generation;

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
        output = new TextView(this);
        output.setTextColor(Color.GREEN);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(14);
        output.setPadding(16, 16, 16, 16);
        scroll = new ScrollView(this);
        scroll.addView(output);
        terminalContent = new LinearLayout(this);
        terminalContent.setOrientation(LinearLayout.VERTICAL);
        terminalContent.setBackgroundColor(Color.BLACK);
        terminalContent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.GREEN);
        input.setHintTextColor(Color.GRAY);
        input.setHint("输入命令后按发送");
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setEnabled(false);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEND && (event == null || event.getKeyCode() != android.view.KeyEvent.KEYCODE_ENTER || event.getAction() != android.view.KeyEvent.ACTION_DOWN)) return false;
            String command = input.getText().toString();
            if (shell != null && shell.writeCommand(command)) input.setText("");
            else append("\n命令未发送：连接未就绪、命令过长或发送队列已满。\n");
            return true;
        });
        terminalContent.addView(input);
        Button send = new Button(this);
        send.setText("发送 / 回车");
        send.setOnClickListener(v -> {
            if (shell != null && shell.writeCommand(input.getText().toString())) input.setText("");
            else append("\n发送失败：请确认 ADB 已连接。\n");
        });
        terminalContent.addView(send);
        Button interrupt = new Button(this);
        interrupt.setText("Ctrl+C（中断）");
        interrupt.setOnClickListener(v -> { if (shell != null) shell.writeInput("\u0003"); });
        terminalContent.addView(interrupt);
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
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        UsbDevice device = deviceFrom(intent);
        if (device != null && findAdbInterface(device) != null) requestDevice(device);
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
            @Override public void onConnected() { post(current, () -> { input.setEnabled(true); append("\nADB Shell 已连接。\n"); }); }
            @Override public void onOutput(String text) { post(current, () -> append(text)); }
            @Override public void onDisconnected(String reason) { post(current, () -> { input.setEnabled(false); shell = null; deviceName = null; append("\n" + reason + "\n"); }); }
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
        input.requestFocus();
    }

    private void post(int current, Runnable action) { runOnUiThread(() -> { if (generation == current && !isFinishing()) action.run(); }); }
    private void append(String text) {
        output.append(text);
        if (output.length() > 200000) output.setText(output.getText().subSequence(output.length() - 150000, output.length()));
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
    private void disconnect() {
        generation++;
        if (shell != null) shell.close();
        shell = null;
        deviceName = null;
        if (input != null) input.setEnabled(false);
    }
    @Override protected void onDestroy() {
        if (terminal != null) terminal.dismiss();
        disconnect();
        unregisterReceiver(receiver);
        super.onDestroy();
    }
}
