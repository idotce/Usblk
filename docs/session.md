# 当前工作快照

更新时间：2026-10-29；Agent：Codex

目标：处理状态栏遮挡，改善 USB ADB 交互。

状态：新增 WindowInsetsHelper，为主界面、设置、摄像头及终端窗口处理系统栏、刘海与键盘避让。ADB 改为独立 Dialog，连接时自动弹出；增加发送按钮和 Ctrl+C，保留软键盘发送。AdbShellManager 增加 writeInput 供控制字符使用。

参考：查看 jackpal/Android-Terminal-Emulator 的 TermSession 流接口；当前未集成其模拟器，不支持完整 ANSI 屏幕控制。

验证：Windows python3.exe build.py build release 成功，脚本返回 0；APK 位于 app/build/outputs/apk/release/app-release.apk。使用 debug.keystore 签名；脚本未找到 zipalign。日志 _temp/windows-release.log。未安装或真机验证。

待续：用户设备上无法交互的具体原因尚未复现，需观察连接/授权/发送错误提示。完整 ANSI 终端与硬件验证尚未完成。
