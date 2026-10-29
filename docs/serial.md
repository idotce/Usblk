# USB 串口助手

更新时间：2026-10-29；Agent：Codex

## 组件与入口

主界面“USB 串口助手”进入 SerialActivity。依赖固定为 usb-serial-for-android 3.10.0，采用该版本的 compileSdk 35 接口，无需 root；MIT 许可保留在 app/src/main/assets/licenses/usb-serial-MIT.txt。来源：https://github.com/mik3y/usb-serial-for-android/tree/v3.10.0 。未修改第三方库源码。

默认探测器按库支持的 VID/PID 与 CDC-ACM 接口识别设备，列出名称、VID/PID、驱动与端口；未匹配的 USB 设备显示为不支持，不盲目打开。支持多端口设备选择其中一个端口，首版仅同时连接一个串口。插入过滤补充 CDC-ACM 和常见 FTDI、Prolific、Silabs、Qinheng 厂商；过滤命中后仍需驱动探测。非串口接口不因此成为串口。其他 VID/PID 的设备在助手内手动扫描，暂未提供自定义驱动映射。

## 操作

- 选择设备、端口，再申请 USB 权限和配置参数。连接参数保存供下次使用。
- 参数包括波特率、5–8 数据位、1/1.5/2 停止位、无/奇/偶/MARK/SPACE 校验、无/RTS-CTS/DTR-DSR/XON-XOFF 流控。驱动或硬件不支持时报告连接错误。
- DTR/RTS 可在连接时选择，默认不置位；实际电平和打开驱动时的瞬态取决于硬件。可能触发开发板复位。硬件流控使用对应控制线时不允许同时手动置位该线。
- 文本模式按 UTF-8 增量解码，保留跨接收包的多字节字符；HEX 模式展示原始字节。发送可选择 UTF-8/HEX，追加无/LF/CR/CRLF；HEX 必须成对，不接受 0x 前缀。
- 可暂停显示、清空显示、查看 RX/TX 与显示丢弃字节计数。时间戳为界面处理时刻，不代表每个字节到达硬件的准确时间。
- 定时发送捕获启动时的内容，周期范围 100–3600000 ms；发送队列满、连接关闭、切换显示模式或离开页面时停止。
- 交互终端复用 Termux 模拟器和 TerminalView，提供 ESC/Tab/Ctrl/Alt/方向键、软硬键盘、长按复制粘贴。修饰键作用于下一次输入。接收不做本地回显。普通 UART 不发送远端窗口尺寸控制报文。

## 线程、缓存与日志

SerialPortManager 在独立读线程打开与读取端口；写入通过单线程有界队列，最多 64 项，每项最多 64 KiB，写超时 2 秒。失败可能已部分发送，错误提示不声称原子发送。UI 每 100 ms 最多消费 16 KiB；接收缓存 256 KiB，满时丢弃最旧的显示数据并累计计数。暂停显示仍继续接收和记录日志，不能承诺在持续高吞吐下不丢数据。

日志独立记录时间戳、RX/TX、HEX 字节，位于应用私有 files/serial。每会话约 8 MiB 上限，达到后停止日志但继续收发；新会话保留最近 8 个日志文件。导出按钮通过系统文件选择器导出当前/最近会话的固定日志前缀，目标文件写入在后台进行，避免慢文档提供器持有接收日志锁。

横竖屏变化保留 Activity 和连接；页面后台期间接收线程仍运行，但未接入前台服务，不保证系统回收后继续连接。退出页面销毁时关闭端口。ADB 与串口使用独立连接管理；同一复合 USB 设备、USB Hub 下与 ADB/UVC 并用尚未验证。

## 验证

macOS 编译目标 :app:compileDebugJavaWithJavac 通过。SerialDataTest 验证 HEX 非法输入、换行符、UTF-8 所有拆分位置、解码重置及 HEX 格式；ShellProtocolTest 回归通过。

使用 build.py 的 build_variant('release') 执行完整 Release 构建、zipalign 和 apksigner，关闭安装询问，未安装设备；日志 _temp/serial-release.log。产物 app/build/outputs/apk/release/app-release.apk，使用本机 debug.keystore 测试签名。

未连接串口硬件，设备识别、参数、收发、流控、热拔插、长时间接收、输入法及界面布局未作真机验证。未验证 Windows/Linux 构建；无后台前台服务、多端口并发、BREAK、控制线实时监测或扩展键长按重复。
