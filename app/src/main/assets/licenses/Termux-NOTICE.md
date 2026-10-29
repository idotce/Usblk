# Termux 终端组件来源与许可

上游：https://github.com/termux/termux-app
提交：8629e632fcb95da272221be327db653fb24befe9

项目按 GPL-3.0-only 发布，全文见根目录 LICENSE；Termux 的许可例外说明见 UPSTREAM-LICENSE.md，Apache-2.0 全文见 APACHE-2.0.txt。保留上游文件中的版权及许可声明。

仅移入 terminal-emulator 与 terminal-view 的 Java/资源，不包含 Termux 应用、包管理、JNI 和本地进程启动。TerminalSession.java 是本项目新增的传输无关接口，替换上游同名本地进程会话；其余移入文件逐字保留。USB ADB 适配代码位于 app 模块。

## 文件清单

| 本地文件 | 上游文件 | SHA-256 / 许可 |
| --- | --- | --- |
| src/main/AndroidManifest.xml | 本项目新增/替换 | GPL-3.0-only |
| src/main/java/com/termux/terminal/AndroidUtils.java | terminal-emulator/src/main/java/com/termux/terminal/AndroidUtils.java | f792680a65bb173698e1d5cc68cf1f94b356d4c32276518c171294e4c39e382e / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/ITermImage.java | terminal-emulator/src/main/java/com/termux/terminal/ITermImage.java | 2fb9f744c425139f8f966544e1715f5e896de676421856729749c12b04e5b70f / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/KeyHandler.java | terminal-emulator/src/main/java/com/termux/terminal/KeyHandler.java | 78688caed123d644f49cdaee2515849b704a6be29e00e6e865179e358b5f1eee / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/Logger.java | terminal-emulator/src/main/java/com/termux/terminal/Logger.java | 7ab53c7ee035e9658a60525c93613e6b8126db36abc46f8cdbc5fdf49032a04f / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalBitmap.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalBitmap.java | 95e81caa1893d2e216b7b8b57117a7439d737412707b74ccc93b9262cc38dc61 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalBuffer.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java | 36de5bd628926dd03ce4f9684cabf463b9e1553df471c561c3ef2c3828618cc6 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalColorScheme.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalColorScheme.java | a303825f79925387b2a70087b640fdb097c723cfb8e341b7d82c958d73eb4563 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalColors.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalColors.java | 27cc579af682cab7e6a30bdd166630d596e6215d33ab6b50c58dd55d3fb6867a / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalEmulator.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java | 5510c124feb5f863c87683a426738dfed500b273d2b4e467d10ee63ce9b1ca7b / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalOutput.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalOutput.java | f844b820e8a344e97bdbf421251daf9312dbf3f69a367b084bd920ed8a4898bc / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalRow.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java | 918e709de8e66879f2aec22eb22f4b1dc76bb6cd43616d45f8d2f85a433837f9 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalSession.java | 本项目新增/替换 | GPL-3.0-only |
| src/main/java/com/termux/terminal/TerminalSessionClient.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalSessionClient.java | 7db66110b370ffb359c5a3858b49b8376ddbbe530ccfd5b192b317bdb5ae6e3d / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TerminalSixel.java | terminal-emulator/src/main/java/com/termux/terminal/TerminalSixel.java | ac3946316cd9f39a03f7e6c201e1e85ce568ae73ff923533ec70e0c101aeb7ae / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/TextStyle.java | terminal-emulator/src/main/java/com/termux/terminal/TextStyle.java | f4898ec2b5b46c70b3694733d21e68638665873179363382525548eff8f18584 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/terminal/WcWidth.java | terminal-emulator/src/main/java/com/termux/terminal/WcWidth.java | 68fc84a28813b13e4a1dfec8d9acbd9d7bd2a6cea62fe2f38319d1eca6cca776 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/view/GestureAndScaleRecognizer.java | terminal-view/src/main/java/com/termux/view/GestureAndScaleRecognizer.java | 0df6451d1187ffe2d4c9aa5496cdcc04c96f75839cc990e70b40afaf0bd6e071 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/view/TerminalRenderer.java | terminal-view/src/main/java/com/termux/view/TerminalRenderer.java | 8100deeade9922eec7e44903603c1cec0f09699bc836079d1b779220341d52e4 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/view/TerminalView.java | terminal-view/src/main/java/com/termux/view/TerminalView.java | 37b7cd1acbe7688da1c3e42f0b3819d3ae2174fe48d420c882fc5d40bb7ae821 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/view/TerminalViewClient.java | terminal-view/src/main/java/com/termux/view/TerminalViewClient.java | 8f8e647e734e7a888104511979f00084e2e5fc0a887ebbd56c601fd4c314a795 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/view/support/PopupWindowCompatGingerbread.java | terminal-view/src/main/java/com/termux/view/support/PopupWindowCompatGingerbread.java | 00c60e0045aab77c1925331f78ed2946696c11a3dd9bef3b94f0f679d3a9aa59 / Apache-2.0（文件头） |
| src/main/java/com/termux/view/textselection/CursorController.java | terminal-view/src/main/java/com/termux/view/textselection/CursorController.java | 6a7331f375881be9b24d34467900ec84a232ca07feeff69575b3b642abdc3e60 / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/view/textselection/TextSelectionCursorController.java | terminal-view/src/main/java/com/termux/view/textselection/TextSelectionCursorController.java | 25c723b8026902dadb7e5c0c831e4be3e040a8d68d8e53d3d80985f993e1ca0c / GPL-3.0-only（上游默认） |
| src/main/java/com/termux/view/textselection/TextSelectionHandleView.java | terminal-view/src/main/java/com/termux/view/textselection/TextSelectionHandleView.java | 207a0a152e8150e9977006c8f987fb4e87b712baa7e1da69b1c827a55fe8a173 / GPL-3.0-only（上游默认） |
| src/main/res/drawable/text_select_handle_left_material.xml | terminal-view/src/main/res/drawable/text_select_handle_left_material.xml | 3ce66a831646933bea6adc67ed8d4b8dfd3bb86b992b1ea789b0ba577add4551 / GPL-3.0-only（上游默认） |
| src/main/res/drawable/text_select_handle_right_material.xml | terminal-view/src/main/res/drawable/text_select_handle_right_material.xml | 977579c11e9c28ef74af25ab61b8f39f812006e8ac5d2a9c5bf185a2456d522f / GPL-3.0-only（上游默认） |
| src/main/res/values/strings.xml | terminal-view/src/main/res/values/strings.xml | c2880e9b490ea42e0816dc2ad1b9bd6b1e75098508afd3681433ba07b14a1bfb / GPL-3.0-only（上游默认） |
