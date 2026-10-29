package com.example.usbhosttool;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        WindowInsetsHelper.apply(root);
        TextView title = new TextView(this);
        title.setText("连接设置（下次打开对应模块生效）");
        root.addView(title);
        addSwitch(root, "adb_auto", "插入设备启动时自动连接 ADB", true);
        addSwitch(root, "video_hd", "UVC 请求 1280×720（关闭为 640×480）", false);
        addSwitch(root, "uac", "启用 UAC：采集 USB 麦克风 PCM 文件", false);
        addText(root, "terminal_font", "终端字号（8–32）", "14");
        addSwitch(root, "terminal_green", "终端使用绿色文字", false);
        addSwitch(root, "terminal_keyboard", "打开终端时显示软键盘", true);
        addText(root, "terminal_keys", "扩展键：空格分隔，分号换行，最多三排，每排八键", "ESC TAB CTRL ALT HOME UP END; / - | ~ LEFT DOWN RIGHT");
        TextView license = new TextView(this);
        license.setText("终端组件来自 Termux，遵循 GPLv3-only；保留 Apache-2.0 来源代码许可。完整许可及来源见应用 assets/licenses 和项目 terminal 目录。");
        root.addView(license);
        TextView note = new TextView(this);
        note.setText("UAC 依赖系统 USB 音频驱动，未识别到 USB 麦克风时不会使用内置麦克风。音频保存到应用私有目录，暂不混合为 MP4。视频分辨率以摄像头实际支持为准。");
        root.addView(note);
    }
    private void addText(LinearLayout root, String key, String label, String fallback) {
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText(label);
        root.addView(title);
        android.widget.EditText field = new android.widget.EditText(this);
        android.content.SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        field.setSingleLine(true);
        field.setText(prefs.getString(key, fallback));
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(key, s.toString()).apply();
            }
            @Override public void afterTextChanged(android.text.Editable text) { }
        });
        root.addView(field);
    }
    private void addSwitch(LinearLayout root, String key, String label, boolean fallback) {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        Switch option = new Switch(this);
        option.setText(label);
        option.setChecked(prefs.getBoolean(key, fallback));
        option.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        root.addView(option);
    }
}
