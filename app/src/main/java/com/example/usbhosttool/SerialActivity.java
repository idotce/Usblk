package com.example.usbhosttool;

import android.app.*;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import com.hoho.android.usbserial.driver.*;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import java.io.*;
import java.util.*;

/** Single USB serial port assistant; connection survives orientation changes. */
public final class SerialActivity extends Activity {
    private static final String PERMISSION = "com.example.usbhosttool.SERIAL_PERMISSION";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private UsbManager usb;
    private SerialPortManager manager;
    private UsbSerialPort pendingPort;
    private SerialPortManager exportSource;
    private String activeDevice;
    private int generation;
    private TextView status, counters, output;
    private EditText input, interval;
    private Spinner mode, ending;
    private CheckBox hexSend, timestamps, paused;
    private Button connect, repeat;
    private ScrollView outputScroll;
    private TerminalView terminal;
    private SerialTerminalSession session;
    private final SerialData decoder = new SerialData();
    private boolean ctrl, alt, resumed;
    private byte[] periodic;
    private int period;
    private long lastDropped;
    private final Runnable refresh = new Runnable() {
        public void run() {
            if (!resumed) return;
            if (manager != null) {
                long[] count = manager.counters();
                counters.setText("RX " + count[0] + " B   TX " + count[1] + " B   显示丢弃 " + count[2] + " B");
                if (!paused.isChecked()) {
                    byte[] data = manager.drain();
                    if (count[2] != lastDropped) { decoder.reset(); session.getEmulator().reset(); lastDropped = count[2]; }
                    if (data.length > 0) {
                        if (mode.getSelectedItemPosition() == 2) session.append(data);
                        else {
                            String value = mode.getSelectedItemPosition() == 1 ? SerialData.hex(data) : decoder.decode(data);
                            if (!value.isEmpty()) append((timestamps.isChecked() ? "[" + new java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date()) + "] " : "") + value);
                        }
                    }
                }
            }
            handler.postDelayed(this, 100);
        }
    };
    private final Runnable timer = new Runnable() {
        public void run() {
            if (periodic == null) return;
            if (manager == null || !manager.send(periodic)) { stopRepeat(); message("定时发送停止：连接不可用或队列已满"); return; }
            handler.postDelayed(this, period);
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            UsbDevice device = device(intent);
            if (PERMISSION.equals(intent.getAction())) {
                if (pendingPort == null || device == null || !device.getDeviceName().equals(pendingPort.getDriver().getDevice().getDeviceName())) return;
                UsbSerialPort selected = pendingPort; pendingPort = null; connect.setEnabled(true);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && usb.hasPermission(device)) configure(selected);
                else message("USB 串口权限被拒绝");
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction()) && device != null) {
                if (pendingPort != null && device.getDeviceName().equals(pendingPort.getDriver().getDevice().getDeviceName())) { pendingPort = null; connect.setEnabled(true); }
                if (device.getDeviceName().equals(activeDevice)) { disconnect(); message("USB 串口设备已拔出"); }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) message("检测到 USB 设备，点击选择设备查看串口");
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        usb = (UsbManager) getSystemService(USB_SERVICE);
        LinearLayout root = column();
        setContentView(root); WindowInsetsHelper.apply(root);
        LinearLayout top = row(root);
        connect = button(top, "选择设备", this::selectDevice);
        button(top, "断开", this::disconnect);
        button(top, "导出日志", () -> {
            if (manager == null) { message("尚无串口日志"); return; }
            exportSource = manager;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "serial-" + System.currentTimeMillis() + ".log");
            startActivityForResult(intent, 40);
        });
        status = new TextView(this); status.setText("连接 USB 转串口设备后选择端口"); root.addView(status);
        counters = new TextView(this); root.addView(counters);
        LinearLayout options = row(root);
        mode = spinner(options, new String[]{"文本 UTF-8", "HEX 接收", "交互终端"});
        paused = check(options, "暂停显示"); timestamps = check(options, "时间戳");
        output = new TextView(this); output.setTextIsSelectable(true); output.setTypeface(android.graphics.Typeface.MONOSPACE);
        outputScroll = new ScrollView(this); outputScroll.addView(output);
        root.addView(outputScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        terminal = new TerminalView(this, null);
        TerminalCallbacks callbacks = new TerminalCallbacks() {
            @Override public void onTextChanged(TerminalSession s) { terminal.onScreenUpdated(); }
            @Override public void onColorsChanged(TerminalSession s) { terminal.invalidate(); }
            @Override public boolean readControlKey() { boolean held = ctrl; ctrl = false; return held; }
            @Override public boolean readAltKey() { boolean held = alt; alt = false; return held; }
            @Override public void onSingleTapUp(MotionEvent e) { keyboard(); }
            @Override public void onBell(TerminalSession s) { message("终端提示：检查串口连接和发送队列"); }
            @Override public void onCopyTextToClipboard(TerminalSession s, String text) {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("串口", text));
            }
            @Override public void onPasteTextFromClipboard(TerminalSession s) {
                ClipboardManager clip = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clip.hasPrimaryClip() && clip.getPrimaryClip().getItemCount() > 0)
                    session.getEmulator().paste(clip.getPrimaryClip().getItemAt(0).coerceToText(SerialActivity.this).toString());
            }
        };
        terminal.setTerminalViewClient(callbacks);
        int font;
        try { font = Integer.parseInt(getSharedPreferences("settings", MODE_PRIVATE).getString("terminal_font", "14")); }
        catch (NumberFormatException e) { font = 14; }
        terminal.setTextSize((int) (Math.max(8, Math.min(32, font)) * getResources().getDisplayMetrics().scaledDensity));
        session = new SerialTerminalSession(callbacks); terminal.attachSession(session);
        terminal.setVisibility(View.GONE); root.addView(terminal, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout keys = row(root);
        String[] labels = {"ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→"};
        int[] codes = {KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB, 0, 0, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button key = button(keys, labels[i], () -> {
                if (index == 2) { ctrl = !ctrl; message("CTRL " + (ctrl ? "已按下" : "已释放")); return; }
                if (index == 3) { alt = !alt; message("ALT " + (alt ? "已按下" : "已释放")); return; }
                int modifiers = (ctrl ? KeyEvent.META_CTRL_ON : 0) | (alt ? KeyEvent.META_ALT_ON : 0);
                terminal.onKeyDown(codes[index], new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, codes[index], 0, modifiers));
                ctrl = alt = false;
            });
            key.setTextSize(11); key.setMinWidth(0); key.setMinimumWidth(0); key.setPadding(0, 0, 0, 0); key.setFocusable(false);
        }
        keys.setVisibility(View.GONE);
        LinearLayout sendOptions = row(root);
        hexSend = check(sendOptions, "HEX 发送");
        ending = spinner(sendOptions, new String[]{"无换行", "LF", "CR", "CRLF"});
        button(sendOptions, "清空显示", () -> { output.setText(""); decoder.reset(); session.getEmulator().reset(); terminal.onScreenUpdated(); if (manager != null) manager.clearDisplayBuffer(); });
        LinearLayout sendRow = row(root);
        input = new EditText(this); input.setSingleLine(true); input.setHint("发送内容；HEX 例：01 FF 0A"); sendRow.addView(input, new LinearLayout.LayoutParams(0, -2, 3));
        button(sendRow, "发送", () -> { try { if (manager == null || !manager.send(payload())) message("未发送：未连接、内容为空或队列已满"); } catch (IllegalArgumentException e) { message(e.getMessage()); } });
        LinearLayout periodicRow = row(root);
        interval = new EditText(this); interval.setSingleLine(true); interval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); interval.setText("1000"); interval.setHint("周期 ms"); periodicRow.addView(interval, new LinearLayout.LayoutParams(0, -2, 1));
        repeat = button(periodicRow, "定时发送", () -> {
            if (periodic != null) { stopRepeat(); return; }
            try {
                period = Integer.parseInt(interval.getText().toString());
                if (period < 100 || period > 3600000) throw new IllegalArgumentException("周期范围 100–3600000 ms");
                byte[] bytes = payload();
                if (manager == null || !manager.isReady() || bytes.length == 0) throw new IllegalArgumentException("请先连接并填写发送内容");
                periodic = bytes; repeat.setText("停止定时"); handler.post(timer);
            } catch (IllegalArgumentException e) { message("定时设置无效：" + e.getMessage()); }
        });
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                decoder.reset(); ctrl = alt = false; stopRepeat();
                sendOptions.setVisibility(position == 2 ? View.GONE : View.VISIBLE);
                sendRow.setVisibility(position == 2 ? View.GONE : View.VISIBLE);
                periodicRow.setVisibility(position == 2 ? View.GONE : View.VISIBLE);
                outputScroll.setVisibility(position == 2 ? View.GONE : View.VISIBLE);
                terminal.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                keys.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                if (position == 2) { terminal.requestFocus(); keyboard(); }
            }
        });
        IntentFilter filter = new IntentFilter(PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver, filter);
    }
    private LinearLayout column() { LinearLayout value = new LinearLayout(this); value.setOrientation(LinearLayout.VERTICAL); return value; }
    private LinearLayout row(LinearLayout parent) { LinearLayout value = new LinearLayout(this); parent.addView(value); return value; }
    private Button button(LinearLayout parent, String title, Runnable action) {
        Button value = new Button(this); value.setText(title); value.setOnClickListener(v -> action.run());
        parent.addView(value, new LinearLayout.LayoutParams(0, -2, 1)); return value;
    }
    private CheckBox check(LinearLayout parent, String title) { CheckBox value = new CheckBox(this); value.setText(title); parent.addView(value); return value; }
    private Spinner spinner(LinearLayout parent, String[] choices) {
        Spinner value = new Spinner(this); value.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
        parent.addView(value, new LinearLayout.LayoutParams(0, -2, 1)); return value;
    }
    private void message(String value) { status.setText(value); }
    private void append(String value) {
        output.append(value);
        if (output.length() > 100000) output.setText(output.getText().subSequence(output.length() - 75000, output.length()));
        outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
    }
    private byte[] payload() { return SerialData.encode(input.getText().toString(), hexSend.isChecked(), new String[]{"", "\n", "\r", "\r\n"}[ending.getSelectedItemPosition()]); }
    private void keyboard() { terminal.requestFocus(); ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(terminal, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); }
    private UsbDevice device(Intent intent) {
        return Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class) : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }
    private void selectDevice() {
        if (pendingPort != null) return;
        ArrayList<UsbSerialPort> ports = new ArrayList<>(); ArrayList<String> labels = new ArrayList<>();
        for (UsbDevice device : usb.getDeviceList().values()) {
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            String name = String.format(Locale.ROOT, "%s  %04X:%04X", device.getProductName() == null ? device.getDeviceName() : device.getProductName(), device.getVendorId(), device.getProductId());
            if (driver == null) { ports.add(null); labels.add(name + "（未匹配串口驱动）"); }
            else for (UsbSerialPort port : driver.getPorts()) { ports.add(port); labels.add(name + " / " + driver.getClass().getSimpleName() + " / 端口 " + port.getPortNumber()); }
        }
        if (labels.isEmpty()) { message("未发现 USB 设备，请检查 OTG 和连接"); return; }
        new AlertDialog.Builder(this).setTitle("选择 USB 串口").setItems(labels.toArray(new String[0]), (dialog, which) -> {
            UsbSerialPort port = ports.get(which);
            if (port == null) { message("此设备未匹配串口驱动，不能打开为串口"); return; }
            if (usb.hasPermission(port.getDriver().getDevice())) configure(port);
            else {
                pendingPort = port; connect.setEnabled(false);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                usb.requestPermission(port.getDriver().getDevice(), PendingIntent.getBroadcast(this, 1, new Intent(PERMISSION).setPackage(getPackageName()), flags));
            }
        }).show();
    }
    private void configure(UsbSerialPort port) {
        LinearLayout fields = column();
        android.content.SharedPreferences prefs = getSharedPreferences("serial", MODE_PRIVATE);
        EditText baud = new EditText(this); baud.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); baud.setHint("波特率"); baud.setText(prefs.getString("baud", "115200")); fields.addView(baud);
        LinearLayout parameters = row(fields);
        Spinner bits = spinner(parameters, new String[]{"8 数据位", "7 数据位", "6 数据位", "5 数据位"}); bits.setSelection(prefs.getInt("bits", 0));
        Spinner stops = spinner(parameters, new String[]{"1 停止位", "2 停止位", "1.5 停止位"}); stops.setSelection(prefs.getInt("stops", 0));
        Spinner parity = spinner(parameters, new String[]{"无校验", "奇校验", "偶校验", "MARK", "SPACE"}); parity.setSelection(prefs.getInt("parity", 0));
        Spinner flow = spinner(row(fields), new String[]{"无流控", "RTS/CTS", "DTR/DSR", "XON/XOFF"}); flow.setSelection(prefs.getInt("flow", 0));
        LinearLayout controls = row(fields); CheckBox dtr = check(controls, "DTR"); CheckBox rts = check(controls, "RTS");
        dtr.setChecked(prefs.getBoolean("dtr", false)); rts.setChecked(prefs.getBoolean("rts", false));
        TextView note = new TextView(this); note.setText("DTR/RTS 变化可能复位开发板；未勾选表示不置位，实际引脚电平取决于硬件；打开驱动时也可能产生变化。流控与串口参数以芯片实际能力为准。"); fields.addView(note);
        ScrollView scroll = new ScrollView(this); scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("串口参数").setView(scroll).setNegativeButton("取消", null).setPositiveButton("连接", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            try {
                SerialPortManager.Config config = new SerialPortManager.Config(); config.baud = Integer.parseInt(baud.getText().toString());
                if (config.baud <= 0) throw new IllegalArgumentException("波特率必须大于零");
                config.dataBits = 8 - bits.getSelectedItemPosition();
                config.stopBits = new int[]{UsbSerialPort.STOPBITS_1, UsbSerialPort.STOPBITS_2, UsbSerialPort.STOPBITS_1_5}[stops.getSelectedItemPosition()];
                config.parity = parity.getSelectedItemPosition();
                config.flow = new UsbSerialPort.FlowControl[]{UsbSerialPort.FlowControl.NONE, UsbSerialPort.FlowControl.RTS_CTS, UsbSerialPort.FlowControl.DTR_DSR, UsbSerialPort.FlowControl.XON_XOFF}[flow.getSelectedItemPosition()];
                config.dtr = dtr.isChecked(); config.rts = rts.isChecked();
                prefs.edit().putString("baud", baud.getText().toString()).putInt("bits", bits.getSelectedItemPosition()).putInt("stops", stops.getSelectedItemPosition()).putInt("parity", parity.getSelectedItemPosition()).putInt("flow", flow.getSelectedItemPosition()).putBoolean("dtr", config.dtr).putBoolean("rts", config.rts).apply();
                open(port, config); dialog.dismiss();
            } catch (IllegalArgumentException e) { baud.setError("参数无效：" + e.getMessage()); }
        })); dialog.show();
    }
    private void open(UsbSerialPort port, SerialPortManager.Config config) {
        disconnect(); decoder.reset(); session.getEmulator().reset(); terminal.onScreenUpdated(); lastDropped = 0;
        final int current = ++generation;
        activeDevice = port.getDriver().getDevice().getDeviceName();
        File directory = new File(getFilesDir(), "serial");
        if (!directory.exists() && !directory.mkdirs()) { message("无法创建串口日志目录"); return; }
        File[] oldLogs = directory.listFiles((folder, name) -> name.startsWith("serial-") && name.endsWith(".log"));
        if (oldLogs != null && oldLogs.length >= 8) {
            Arrays.sort(oldLogs, java.util.Comparator.comparing(File::getName));
            for (int i = 0; i <= oldLogs.length - 8; i++) oldLogs[i].delete();
        }
        manager = new SerialPortManager(usb, port, config, new File(directory, "serial-" + System.currentTimeMillis() + ".log"), new SerialPortManager.Listener() {
            public void onStatus(String value) { post(current, () -> message(value)); }
            public void onReady() { post(current, () -> { session.bind(manager); message("串口已连接：" + config.baud + " baud；当前会话已记录日志"); }); }
            public void onClosed(String reason) { post(current, () -> { session.bind(null); stopRepeat(); message(reason); }); }
        });
        message("正在打开串口……"); manager.start();
    }
    private void post(int current, Runnable action) { handler.post(() -> { if (generation == current && !isFinishing()) action.run(); }); }
    private void stopRepeat() { periodic = null; handler.removeCallbacks(timer); if (repeat != null) repeat.setText("定时发送"); }
    private void disconnect() {
        generation++; stopRepeat();
        if (manager != null) manager.close();
        if (session != null) session.bind(null);
        activeDevice = null;
        if (status != null) message("串口已断开");
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 40 || result != RESULT_OK || data == null || data.getData() == null || manager == null) return;
        SerialPortManager source = exportSource; exportSource = null;
        if (source == null) return;
        android.net.Uri target = data.getData();
        new Thread(() -> {
            try (OutputStream destination = getContentResolver().openOutputStream(target)) {
                if (destination == null) throw new IOException("无法打开目标文件"); source.export(destination);
                runOnUiThread(() -> { if (!isFinishing()) message("日志已导出：时间戳、RX/TX、HEX 原始数据"); });
            } catch (IOException e) { runOnUiThread(() -> { if (!isFinishing()) message("导出失败：" + e.getMessage()); }); }
        }, "serial-export").start();
    }
    @Override protected void onResume() { super.onResume(); resumed = true; handler.post(refresh); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(refresh); stopRepeat(); super.onPause(); }
    @Override protected void onDestroy() { disconnect(); unregisterReceiver(receiver); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
