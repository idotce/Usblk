#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
android 通用构建脚本（Gradle / Android 工程）

支持平台: Windows / macOS / Linux

用法:
    python build.py                # 交互菜单
    python build.py build debug    # 构建 Debug（JNI + AAR + APK 一次完成）
    python build.py build release  # 构建 Release
    python build.py install        # 安装 APK 到已连接设备
    python build.py clean          # 清理（gradle clean）

说明:
    - 选择 debug / release 后，gradle 的 assemble 任务链会自动依次完成
      JNI 编译（externalNativeBuild）、AAR 打包、APK 打包，无需单独选择；
    - SDK 路径按平台自动探测，不依赖 local.properties：
      优先级 CONFIG['sdk_dir'] > ANDROID_SDK_ROOT > ANDROID_HOME >
      平台默认位置（Windows: %LOCALAPPDATA%\\Android\\Sdk，
      macOS: ~/Library/Android/sdk，Linux: ~/Android/Sdk）；
    - NDK / CMake 目录同理（CONFIG['ndk_dir'] / ANDROID_NDK / SDK 内 ndk，
      CONFIG['cmake_bin_dir'] / ANDROID_CMAKE_BIN_DIR / SDK 内 cmake）；
    - 构建时通过环境变量把 SDK 传给 Gradle（ANDROID_HOME 等），
      工程内的 local.properties 即使缺失或被 gitignore 也不影响构建；
    - 代理用于 Gradle 下载依赖（http/https 系统属性）。
"""

import atexit
import glob
import os
import platform
import shutil
import subprocess
import sys
import time

# 中文 Windows 控制台默认代码页为 CP936（GBK），Python 管道接收 Gradle/JDK
# 输出时若按 GBK 解码会乱码。强制 Python 自身按 UTF-8 输出。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

# ================================ 配置区 ================================
CONFIG = {
    # Android SDK 目录，留空自动探测（见模块顶部说明的优先级）。
    # 可按平台分别指定（提交后各平台免改）：
    #   "sdk_dir": {"windows": r"D:\Android\Sdk", "macos": "", "linux": ""},
    # 或所有平台共用同一字符串："sdk_dir": r"D:\Android\Sdk"
    "sdk_dir": {
        "windows": r"D:\Documents\Android\Sdk",
        "macos": "~/Library/Android/sdk",
        "linux": "~/Android/Sdk",
    },

    # NDK 目录，留空自动探测。同样支持按平台字典或全局字符串：
    #   "ndk_dir": {"windows": r"D:\Android\Sdk\ndk\25.1.8937393", "macos": "", "linux": ""},
    # 探测优先级: CONFIG['ndk_dir'] > ANDROID_NDK >
    # <sdk>/ndk/<ndk_version> > <sdk>/ndk 下已安装的任意版本
    "ndk_dir": {
        "windows": "",
        "macos": "",
        "linux": "",
    },
    "ndk_version": "25.1.8937393",

    # SDK 自带 CMake 目录，留空自动探测。同样支持按平台字典或全局字符串：
    #   "cmake_bin_dir": {"windows": r"D:\Android\Sdk\cmake\3.22.1\bin", "macos": "", "linux": ""},
    # 探测优先级: CONFIG['cmake_bin_dir'] > ANDROID_CMAKE_BIN_DIR >
    # <sdk>/cmake/<cmake_version>/bin > <sdk>/cmake 下已安装的任意版本
    "cmake_bin_dir": {
        "windows": "",
        "macos": "",
        "linux": "",
    },
    "cmake_version": "3.22.1",

    # JDK 目录，留空使用环境变量 JAVA_HOME。同样支持按平台字典或全局字符串
    "java_home": {
        "windows": "",
        "macos": "",
        "linux": "",
    },

    # Gradle 命令，留空自动使用 ./gradlew（Windows 为 gradlew.bat）。
    # 同样支持按平台字典或全局字符串
    "gradle_cmd": {
        "windows": "",
        "macos": "",
        "linux": "",
    },

    # 默认变体: debug / release
    "default_variant": "debug",

    # 日志文件（相对本脚本目录），所有输出同时写入该文件
    "log_file": "build.log",

    # 各变体执行的 Gradle 任务（assemble 自动覆盖 JNI + AAR + APK）
    "tasks": {
        "debug": [":app:assembleDebug"],
        "release": [":app:assembleRelease"],
    },

    # APK 输出路径（相对 android 工程目录），留空自动在 outputs 下查找
    "apk_path": {
        "debug": "app/build/outputs/apk/debug/app-debug.apk",
        "release": "app/build/outputs/apk/release/app-release.apk",
    },

    # APK 签名配置：keystore 留空则不签名，配置了构建后自动用 apksigner 签名
    # 默认使用 Android 工具链公共签名 ~/.android/debug.keystore
    # （密码 android，alias androiddebugkey），正式发布请换成自己的签名文件
    "signing": {
        "keystore": "~/.android/debug.keystore",
        "keystore_pass": "android",
        "key_alias": "androiddebugkey",
        "key_pass": "android",
    },

    # 构建成功后是否询问安装到设备
    "ask_install": True,

    # Gradle 附加参数（字符串列表）
    "gradle_extra_args": [],

    # 代理（Gradle 下载依赖时使用），host / port 留空则不使用
    "proxy": {
        "host": "",      # 例如 "127.0.0.1"
        "port": 0,       # 例如 7890
        "username": "",
        "password": "",
    },
}
# ========================================================================

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
VARIANTS = ("debug", "release")
LOG_PATH = os.path.join(SCRIPT_DIR, CONFIG.get("log_file", "build.log"))


class Tee:
    """同时输出到终端与日志文件的流包装。"""

    def __init__(self, stream, logfile):
        self._stream = stream
        self._logfile = logfile

    def write(self, data):
        self._stream.write(data)
        self._logfile.write(data)
        self._logfile.flush()
        return len(data)

    def flush(self):
        self._stream.flush()
        self._logfile.flush()

    def fileno(self):
        return self._stream.fileno()

    def isatty(self):
        return self._stream.isatty()


def setup_log():
    """用 Tee 包装 sys.stdout / sys.stderr，所有输出同时写入 build.log。"""
    logfile = open(LOG_PATH, "a", encoding="utf-8", errors="replace")

    def _mark(text):
        logfile.write("\n===== %s %s =====\n"
                      % (text, time.strftime("%Y-%m-%d %H:%M:%S")))
        logfile.flush()

    _mark("构建日志开始")
    sys.stdout = Tee(sys.stdout, logfile)
    sys.stderr = Tee(sys.stderr, logfile)

    def _finish():
        _mark("构建日志结束")
        sys.stdout.flush()
        sys.stderr.flush()
        sys.stdout = sys.__stdout__
        sys.stderr = sys.__stderr__
        logfile.close()

    atexit.register(_finish)


def run_cmd(cmd, env=None, cwd=None):
    """执行命令，输出实时回显并同时写入 build.log。"""
    proc = subprocess.Popen(
        cmd, env=env, cwd=cwd,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace", bufsize=1,
    )
    assert proc.stdout is not None
    for line in proc.stdout:
        sys.stdout.write(line)
        sys.stdout.flush()
    return proc.wait()


def log(msg):
    print("[build] " + msg)


def error(msg):
    print("[build][ERROR] " + msg, file=sys.stderr)


def platform_key():
    """返回当前平台的短 key（windows / macos / linux）。"""
    if os.name == "nt":
        return "windows"
    if sys.platform == "darwin":
        return "macos"
    return "linux"


def config_value(key):
    """解析 CONFIG 中可能按平台配置的字段。

    值可以是字符串（所有平台共用），也可以是
    {"windows": ..., "macos": ..., "linux": ...} 形式的字典
    （按当前平台取对应值，缺省平台回退为空字符串）。
    """
    value = CONFIG.get(key) or ""
    if isinstance(value, dict):
        value = value.get(platform_key(), "") or ""
    return value


def find_sdk():
    """按平台解析 Android SDK 路径，不依赖 local.properties。"""
    sdk = config_value("sdk_dir")
    if sdk:
        return os.path.expanduser(sdk)
    # 环境变量优先级: ANDROID_SDK_ROOT > ANDROID_HOME（兼容不同 CI 习惯）
    for env_name in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        if os.environ.get(env_name):
            return os.path.expanduser(os.environ[env_name])
    # 平台默认安装位置
    if os.name == "nt":
        return os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
    if sys.platform == "darwin":
        return os.path.expanduser("~/Library/Android/sdk")
    return os.path.expanduser("~/Android/Sdk")


def find_ndk():
    """解析 NDK 目录；找不到返回空字符串。"""
    ndk = config_value("ndk_dir")
    if not ndk:
        ndk = os.environ.get("ANDROID_NDK", "")
    if ndk:
        return os.path.expanduser(ndk)
    sdk = find_sdk()
    version = CONFIG.get("ndk_version") or ""
    if not sdk or not os.path.isdir(os.path.join(sdk, "ndk")):
        return ""
    if version:
        direct = os.path.join(sdk, "ndk", version)
        if os.path.isdir(direct):
            return direct
    # SDK 内装了多个 NDK 版本时，取已安装的任意一个
    for v in sorted(os.listdir(os.path.join(sdk, "ndk"))):
        candidate = os.path.join(sdk, "ndk", v)
        if os.path.isdir(candidate):
            return candidate
    return ""


def find_cmake_bin():
    """解析 SDK 自带 CMake 的 bin 目录；找不到返回空字符串。"""
    cmake_dir = config_value("cmake_bin_dir")
    if not cmake_dir:
        cmake_dir = os.environ.get("ANDROID_CMAKE_BIN_DIR", "")
    if cmake_dir:
        return os.path.expanduser(cmake_dir)
    sdk = find_sdk()
    cmake_root = os.path.join(sdk, "cmake")
    version = CONFIG.get("cmake_version") or ""
    if not sdk or not os.path.isdir(cmake_root):
        return ""
    if version:
        direct = os.path.join(cmake_root, version, "bin")
        if os.path.isdir(direct):
            return direct
    for v in sorted(os.listdir(cmake_root), reverse=True):
        candidate = os.path.join(cmake_root, v, "bin")
        if os.path.isdir(candidate):
            return candidate
    return ""


def find_java_home():
    java = config_value("java_home") or os.environ.get("JAVA_HOME", "")
    return os.path.expanduser(java) if java else ""


def gradle_command():
    """返回 gradle 命令路径；均不可用时返回空字符串。"""
    cmd = config_value("gradle_cmd")
    if cmd:
        return cmd
    script_dir = SCRIPT_DIR
    if platform.system() == "Windows":
        gradlew_bat = os.path.join(script_dir, "gradlew.bat")
        if os.path.exists(gradlew_bat):
            return gradlew_bat
        gradle = shutil.which("gradle")
        return gradle or ""
    gradlew = os.path.join(script_dir, "gradlew")
    if os.path.exists(gradlew):
        return gradlew
    gradle = shutil.which("gradle")
    return gradle or ""


def wrapper_jar():
    """Gradle Wrapper 的 jar 路径，不存在则返回空字符串。"""
    jar = os.path.join(SCRIPT_DIR, "gradle", "wrapper", "gradle-wrapper.jar")
    return jar if os.path.isfile(jar) else ""


def gradle_env():
    """给 Gradle 子进程注入环境变量。

    通过环境变量（而非 local.properties）把 SDK / NDK / JDK 传给 Gradle，
    这样脚本在 Windows / macOS / Linux 上都无需手写本机路径。
    同时强制 Gradle 与 JDK 输出 UTF-8，避免中文 Windows 控制台乱码。
    """
    env = os.environ.copy()
    sdk = find_sdk()
    if sdk:
        env["ANDROID_HOME"] = sdk
        env["ANDROID_SDK_ROOT"] = sdk
    ndk = find_ndk()
    if ndk:
        env["ANDROID_NDK"] = ndk
    java_home = find_java_home()
    if java_home:
        env["JAVA_HOME"] = java_home
        env["PATH"] = os.path.join(java_home, "bin") + os.pathsep + env.get("PATH", "")

    proxy = CONFIG.get("proxy") or {}
    opts = []
    if proxy.get("host"):
        opts += ["-Dhttp.proxyHost=" + proxy["host"],
                 "-Dhttps.proxyHost=" + proxy["host"]]
        if proxy.get("port"):
            opts += ["-Dhttp.proxyPort=" + str(proxy["port"]),
                     "-Dhttps.proxyPort=" + str(proxy["port"])]
        if proxy.get("username"):
            opts += ["-Dhttp.proxyUser=" + proxy["username"],
                     "-Dhttps.proxyUser=" + proxy["username"]]
        if proxy.get("password"):
            opts += ["-Dhttp.proxyPassword=" + proxy["password"],
                     "-Dhttps.proxyPassword=" + proxy["password"]]
    gradle_opts = env.get("GRADLE_OPTS", "")
    if "-Dfile.encoding" not in gradle_opts:
        opts += ["-Dfile.encoding=UTF-8"]
    # JDK 18+ 中 stdout/stderr 编码由 stdout.encoding / stderr.encoding 决定
    # （默认跟随系统 native 编码，中文 Windows 上是 GBK）。显式设为 UTF-8，
    # 让 javac / AAPT 等工具的本地化中文输出统一为 UTF-8。
    for flag in ("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"):
        if flag not in gradle_opts:
            opts.append(flag)
    if opts:
        env["GRADLE_OPTS"] = (gradle_opts + " " + " ".join(opts)).strip()
    env["PYTHONUTF8"] = "1"
    env["PYTHONIOENCODING"] = "utf-8"
    return env


def run_gradle(args):
    cmd = gradle_command()
    jar = wrapper_jar()
    if cmd:
        if platform.system() == "Windows":
            full_cmd = [cmd] + args
        elif os.access(cmd, os.X_OK):
            full_cmd = [cmd] + args
        else:
            # 无执行权限（如 macOS quarantine 拦截）时改用 sh 调用，不依赖 x 位
            log("gradlew 无执行权限，改用 sh 调用")
            full_cmd = ["sh", cmd] + args
    elif jar:
        # 工程缺 gradlew / gradle 命令时，直接用 java 执行 wrapper jar
        log("未找到 gradlew/gradle，改用 java 执行 gradle-wrapper.jar")
        full_cmd = ["java", "-cp", jar,
                    "org.gradle.wrapper.GradleWrapperMain"] + args
    else:
        error("找不到 Gradle 命令（gradlew / gradle 均不可用），"
              "请配置 CONFIG['gradle_cmd']")
        return 1
    log("执行: " + " ".join(full_cmd))
    try:
        # 流式读取子进程输出并按 UTF-8 解码，再经 Python 的 console stdout
        # （Windows 用 Unicode 宽字符 API 写控制台）输出，与终端代码页无关，
        # 避免 GBK 终端把 UTF-8 中文渲染成乱码。
        proc = subprocess.Popen(
            full_cmd,
            env=gradle_env(),
            cwd=SCRIPT_DIR,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        assert proc.stdout is not None
        for line in proc.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
        return proc.wait()
    except PermissionError as e:
        error("无法执行 Gradle 命令: %s" % e)
        error("请检查 gradlew 权限: chmod +x gradlew"
              "（macOS 如报 Operation not permitted，可先执行: "
              "xattr -d com.apple.quarantine gradlew）")
        return 1
    except OSError as e:
        error("执行 Gradle 命令失败: %s" % e)
        return 1


def build_variant(variant):
    if variant not in VARIANTS:
        error("未知变体: %s（可用: %s）" % (variant, ", ".join(VARIANTS)))
        return 1
    tasks = CONFIG["tasks"].get(variant) or []
    args = tasks + CONFIG.get("gradle_extra_args") or []
    ret = run_gradle(args)
    if ret == 0:
        apk = find_apk(variant)
        if apk:
            log("构建成功，APK: %s" % apk)
            ret = sign_apk(apk)
            if ret != 0:
                return ret
            # 签名可能重命名了 APK（去掉 -unsigned），重新定位最终产物
            apk = find_apk(variant)
            if CONFIG.get("ask_install", True):
                answer = input("是否安装到设备? [y/N]: ").strip().lower()
                if answer in ("y", "yes"):
                    return install_apk(apk)
        else:
            log("构建成功，但未找到 APK（请检查 CONFIG['apk_path']）")
    else:
        error("构建失败，返回码: %d" % ret)
    return ret


def build_tools_dir():
    sdk = find_sdk()
    if not sdk:
        return ""
    tools_root = os.path.join(sdk, "build-tools")
    if not os.path.isdir(tools_root):
        return ""
    versions = [d for d in os.listdir(tools_root)
                if os.path.isdir(os.path.join(tools_root, d))]
    versions.sort(reverse=True)
    return os.path.join(tools_root, versions[0]) if versions else ""


def tool_path(name):
    tools = build_tools_dir()
    if not tools:
        return ""
    exe = name + (".bat" if platform.system() == "Windows" else "")
    path = os.path.join(tools, exe)
    return path if os.path.exists(path) else ""


def is_apk_signed(apk):
    apksigner = tool_path("apksigner")
    if not apksigner:
        error("未找到 apksigner（请检查 SDK build-tools）")
        return False
    cmd = [apksigner, "verify", apk]
    if platform.system() == "Windows":
        cmd = ["cmd", "/c"] + cmd
    return subprocess.call(cmd, stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL) == 0


def sign_apk(apk):
    """配置了 keystore 则签名，未配置则跳过。返回 0 表示成功/跳过。"""
    signing = CONFIG.get("signing") or {}
    keystore = (signing.get("keystore") or "").strip()
    if not keystore:
        log("未配置签名 keystore（CONFIG['signing']['keystore']），跳过签名")
        return 0
    keystore = os.path.expanduser(keystore)
    if not os.path.isfile(keystore):
        error("签名文件不存在: %s" % keystore)
        return 1

    apksigner = tool_path("apksigner")
    zipalign = tool_path("zipalign")
    if not apksigner:
        error("未找到 apksigner（请检查 SDK build-tools）")
        return 1

    # 未签名 APK 先做 zip 对齐，再签名；已签名 APK 直接重签，避免破坏签名
    if not is_apk_signed(apk):
        if zipalign:
            aligned = apk + ".aligned"
            log("执行: zipalign -p 4 %s %s" % (apk, aligned))
            cmd = [zipalign, "-f", "-p", "4", apk, aligned]
            if platform.system() == "Windows":
                cmd = ["cmd", "/c"] + cmd
            if run_cmd(cmd) != 0:
                error("zipalign 失败")
                return 1
            os.replace(aligned, apk)
        else:
            log("未找到 zipalign，跳过对齐（签名后请确认 APK 可安装）")
    else:
        log("APK 已有签名，将用配置的 keystore 重新签名")

    signed = apk + ".signed"
    args = ["sign",
            "--ks", keystore,
            "--ks-pass", "pass:" + signing.get("keystore_pass", ""),
            "--key-pass", "pass:" + signing.get("key_pass", ""),
            "--ks-key-alias", signing.get("key_alias", ""),
            "--out", signed, apk]
    cmd = [apksigner] + args
    if platform.system() == "Windows":
        cmd = ["cmd", "/c"] + cmd
    log("执行: apksigner sign（keystore: %s）" % keystore)
    ret = run_cmd(cmd)
    if ret != 0:
        error("apksigner 签名失败，返回码: %d" % ret)
        return ret
    os.replace(signed, apk)
    # 清理 apksigner 生成的增量签名辅助文件
    idsig = signed + ".idsig"
    if os.path.exists(idsig):
        os.remove(idsig)

    # gradle 未配置签名时产物名为 app-*-unsigned.apk，签名后去掉该后缀
    final_apk = apk
    basename = os.path.basename(apk)
    if "-unsigned" in basename:
        final_apk = os.path.join(os.path.dirname(apk),
                                 basename.replace("-unsigned", ""))
        os.replace(apk, final_apk)
    log("签名完成: %s" % final_apk)
    return 0


def find_apk(variant):
    apk = CONFIG.get("apk_path", {}).get(variant) or ""
    if apk:
        full = os.path.join(SCRIPT_DIR, apk)
        if os.path.isfile(full):
            return full
    pattern = os.path.join(SCRIPT_DIR, "app", "build", "outputs", "apk",
                           variant, "*.apk")
    files = sorted(glob.glob(pattern), key=os.path.getmtime, reverse=True)
    return files[0] if files else ""


def adb_command():
    sdk = find_sdk()
    if not sdk:
        return ""
    adb = os.path.join(sdk, "platform-tools", "adb")
    if platform.system() == "Windows":
        adb += ".exe"
    return adb if os.path.exists(adb) else ""


def install_apk(apk):
    if not apk:
        error("没有可安装的 APK")
        return 1
    adb = adb_command()
    if not adb:
        error("未找到 adb（请检查 SDK 路径配置）")
        return 1
    log("执行: %s install -r %s" % (adb, apk))
    return run_cmd([adb, "install", "-r", apk])


def install():
    variant = CONFIG.get("default_variant", "debug")
    apk = find_apk(variant)
    if not apk:
        log("未找到 %s 版本 APK，先构建..." % variant)
        ret = build_variant(variant)
        if ret != 0:
            return ret
        apk = find_apk(variant)
    return install_apk(apk)


def clean():
    return run_gradle(["clean"] + CONFIG.get("gradle_extra_args") or [])


ACTIONS = [
    ("build_debug", "Debug 构建（JNI + AAR + APK）", lambda: build_variant("debug")),
    ("build_release", "Release 构建（JNI + AAR + APK）", lambda: build_variant("release")),
    ("install", "安装 APK 到设备", install),
    ("clean", "清理 (clean)", clean),
]


def print_menu():
    print()
    print("========== android 构建菜单 ==========")
    for i, (key, desc, func) in enumerate(ACTIONS, 1):
        print("  %d. %s" % (i, desc))
    print("  x. 退出")
    print("======================================")


def interactive():
    while True:
        print_menu()
        choice = input("请选择: ").strip().lower()
        if choice in ("x", "q", "exit", "quit"):
            print("再见。")
            break
        if choice.isdigit() and 1 <= int(choice) <= len(ACTIONS):
            key, desc, func = ACTIONS[int(choice) - 1]
            ret = func()
            print("返回码: %d" % ret)
        else:
            print("无效选项，请重新输入。")


def print_config():
    print("---- 常用配置（如需修改：编辑 build.py 顶部 CONFIG 或环境变量）----")
    sdk = find_sdk()
    print("  SDK: %s" % (sdk or "未找到（请配置 CONFIG['sdk_dir'] 或环境变量 ANDROID_HOME）"))
    ndk = find_ndk()
    print("  NDK: %s" % (ndk or "未找到（仅当工程需要 JNI 编译时必需）"))
    cmake = find_cmake_bin() or ""
    print("  SDK CMake: %s" % (cmake or "未找到（Gradle 内部可自带）"))
    java_home = find_java_home()
    print("  JAVA_HOME: %s" % (java_home or "未找到（建议安装 JDK 并设置 JAVA_HOME）"))
    gradle = gradle_command() or "（将用 java 执行 gradle-wrapper.jar）"
    print("  Gradle: %s" % gradle)
    print("  默认变体: %s" % CONFIG.get("default_variant", "debug"))
    proxy = CONFIG.get("proxy") or {}
    if proxy.get("host"):
        print("  代理: %s:%s" % (proxy["host"], proxy.get("port") or ""))
    else:
        print("  代理: 无")


def main():
    setup_log()
    print("android 构建脚本 | 平台: %s | Python: %s"
          % (platform.system(), platform.python_version()))
    print_config()
    if len(sys.argv) > 1:
        action = sys.argv[1].lower()
        if action == "build" and len(sys.argv) > 2:
            sys.exit(build_variant(sys.argv[2].lower()) or 0)
        for key, desc, func in ACTIONS:
            if action == key.replace("build_debug", "build"):
                sys.exit(func() or 0)
        if action == "build":
            sys.exit(build_variant(CONFIG.get("default_variant", "debug")) or 0)
        print("未知操作: %s" % action)
        print("可用操作: build debug | build release | install | clean")
        sys.exit(1)
    interactive()


if __name__ == "__main__":
    main()
