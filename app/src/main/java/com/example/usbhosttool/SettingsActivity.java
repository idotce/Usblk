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
        setContentView(root);
        WindowInsetsHelper.apply(root);
        TextView title = new TextView(this);
        title.setText("连接设置（下次打开对应模块生效）");
        root.addView(title);
        addSwitch(root, "adb_auto", "插入设备启动时自动连接 ADB", true);
        addSwitch(root, "video_hd", "UVC 请求 1280×720（关闭为 640×480）", false);
        addSwitch(root, "uac", "启用 UAC：采集 USB 麦克风 PCM 文件", false);
        TextView note = new TextView(this);
        note.setText("UAC 依赖系统 USB 音频驱动，未识别到 USB 麦克风时不会使用内置麦克风。音频保存到应用私有目录，暂不混合为 MP4。视频分辨率以摄像头实际支持为准。");
        root.addView(note);
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
