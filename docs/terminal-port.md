# Termux 终端接入

更新时间：2026-10-29；Agent：Codex

已接入固定版本 termux/termux-app 8629e632fcb95da272221be327db653fb24befe9 的 terminal-emulator、terminal-view Java 及资源，位于 terminal 模块。项目按 GPL-3.0-only 发布，保留上游 Apache-2.0 例外；逐文件来源、哈希和许可见 terminal/NOTICE.md。完整许可同时打包到应用 assets/licenses。

## 架构

- TerminalSession 改为传输无关接口；AdbTerminalSession 连接模拟器与 USB ADB，不启动本地进程，不依赖 Termux JNI 或包管理系统。
- 输出保持原始字节，UTF-8、ANSI、光标、颜色、屏幕更新由模拟器处理；输入经有界队列由 USB 写线程发送，回显和命令历史由远端 Shell 处理。
- 模拟器及视图只在主线程更新。读线程等待主线程消费后才 ACK，避免大量终端输出无限积压 UI 消息；切换连接时通过 generation 丢弃旧会话回调。
- 协商 shell_v2 时打开 shell,v2,pty,TERM=xterm-256color:，增量解析 stdout/stderr/exit 帧；窗口变化通过 ID 5 控制帧同步远端 PTY。写线程合并连续尺寸变化。协议依据 AOSP shell_protocol.h、daemon/shell_service.cpp。
- 不支持 shell_v2 时降级 shell:，明确提示不能同步远端尺寸。
- 横竖屏切换保留 Activity 和连接，终端视图重新布局时同步尺寸；Activity 真正销毁则关闭 USB 和写线程。

## 操作与设置

直接点击终端输入；支持软键盘、硬件键盘、退格、Tab、方向键、Ctrl/Alt 与长按选区复制粘贴。扩展键的 CTRL/ALT 是单次修饰键，例如点 CTRL 后输入 c。

设置页可保存字号（8–32）、白色/绿色文字、自动显示软键盘、扩展键布局；返回终端生效。布局用空格分隔键、分号换行，最多三排，每排八键。特殊键支持 ESC TAB CTRL ALT HOME END UP DOWN LEFT RIGHT ENTER KEYBOARD，其余标签作为文字输入。未实现扩展键长按重复。

## 验证与限制

macOS 使用项目 build.py 的 run_gradle 入口执行 :app:compileDebugJavaWithJavac，包含 terminal Java、应用 Kotlin/Java 与资源处理；通过，无完整 APK 打包。保留 JDK 21 对 Java 8 目标和已有 Android API 的弃用警告。

独立 ShellProtocolTest 覆盖所有分片尺寸、合并帧、空帧、中文/ANSI 字节保持、退出码和非法报文头，通过。复现命令：

```sh
mkdir -p _temp/test-classes
javac -d _temp/test-classes app/src/main/java/com/example/usbhosttool/ShellProtocol.java tests/java/com/example/usbhosttool/ShellProtocolTest.java
java -cp _temp/test-classes com.example.usbhosttool.ShellProtocolTest
```

尚无真机验证。需检查 USB 授权及重连、中文输入/粘贴、Tab/历史、Ctrl+C、横竖屏与软键盘变化后的 stty size，以及设备支持时的 top/vi 全屏刷新。旧协议设备尺寸限制保留。未验证其他构建主机与所有 Android 版本的输入法/绘制兼容性。
