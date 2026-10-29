# 当前工作快照

更新时间：2026-10-29；Agent：Codex

目标：USB ADB 交互终端、USB 串口助手，并构建 Release APK。

状态：Termux 终端接入已完成；新增独立 USB 串口助手，包含设备/端口识别、参数保存、DTR/RTS 与流控配置、UTF-8/HEX 收发、换行符、暂停/清空显示、收发与丢弃计数、时间戳、定时发送、日志导出和交互终端。固定使用 usb-serial-for-android 3.10.0，MIT 声明已保留。用户接受 GPL 开源，Agent 未提交或上传代码。

验证：macOS 使用 build.py build_variant('release') 完整构建成功，完成 zipalign 和测试密钥签名；APK：app/build/outputs/apk/release/app-release.apk（6108041 字节）。apksigner verify 返回 0，v1/v2/v3 验证通过。日志 _temp/serial-release.log。SerialDataTest 与 ShellProtocolTest 均通过；git diff --check 通过。未安装。

待续：代码与构建任务完成，等待用户真机检查 USB 授权、设备识别、收发、流控、DTR/RTS、热拔插、输入法、横竖屏和终端刷新。无硬件验证；其他主机构建未验证。首版不含前台服务、多串口并发及 BREAK。详见 docs/serial.md、docs/terminal-port.md。
