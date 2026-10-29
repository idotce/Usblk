# Termux 终端移植评估

更新时间：2026-10-29；Agent：Codex

结论：技术上可行，但不是直接添加依赖即可使用。评估源码固定为 termux/termux-app 提交 8629e632fcb95da272221be327db653fb24befe9，临时检出位于 _temp/termux-eval。

## 已核实的接口

- terminal-emulator 的 TerminalEmulator.append(byte[], int) 接收原始输出；TerminalOutput.write(byte[], int, int) 回传键盘输入及终端查询响应；resize 更新本地显示尺寸。
- terminal-view 的 TerminalView 直接依赖 TerminalSession，涉及 attachSession、write、writeCodePoint、updateSize、getEmulator 等调用。
- TerminalSession 是 final 类，initializeEmulator 会通过 JNI.createSubprocess 创建本地 PTY/进程，不能通过继承改为 USB ADB 会话。
- 当前项目在 AdbShellManager 中先解码 UTF-8 再回调字符串；需要增加原始字节回调和原始输入接口，避免终端模拟器之前的信息损失。
- 当前 shell: 是旧协议会话，没有窗口尺寸控制通道。需要协商 shell_v2 并打开 PTY 会话，解析其独立于 ADB WRTE 的数据帧；尺寸变化发送 window-size 控制帧。旧设备应降级并说明尺寸同步限制。

## 建议实施范围

1. 固定上游版本，核查逐文件许可证，保留来源、版权和改动说明。
2. 提取终端模拟器和视图，抽象视图所需的会话接口，增加 AdbTerminalSession；去除对本地进程启动的运行时依赖，不引入 Termux 包管理系统。
3. 实现原始字节双向传输、背压、断连与线程生命周期；解析器和绘制在主线程更新，USB 读写在工作线程。
4. 完成 shell_v2 PTY 和窗口尺寸同步，再处理横竖屏、键盘弹出、退格、补全、方向键、Ctrl/Alt、剪贴板与 ANSI 屏幕刷新。
5. 自行实现可配置扩展按键栏，参考 Termux 的交互方式；不直接整包引入 termux-shared。
6. 设置保存字号、配色、按键布局及键盘选项。命令历史由远端 Shell 处理，不在 UI 伪造。

## 依赖与许可证边界

上游根 LICENSE.md 声明 GPLv3-only，同时列出终端库所含 Apache-2.0 来源代码的例外；不能把整个 terminal-view 或 terminal-emulator 一概认定为 Apache-2.0。termux-shared/LICENSE.md 对 shared/termux 路径默认采用 GPLv3-only，ExtraKeysView 位于该路径。实际移植前必须逐文件记录许可证，不默认项目已经选择 GPL 发布。

上游当前模块构建采用 Java 8、minSdk 21、compileSdk 36，模拟器模块含 JNI 构建和上游发布配置。本项目 minSdk 23、compileSdk 35；需要单独验证选定源码及 Android API 要求，不能仅修改 Gradle 数字宣称兼容。

## 验证状态

本次为源码评估，未修改应用代码、未构建移植版本、未验证硬件。Windows Python 构建方式沿用现有授权，实施后使用 build.py 验证。
