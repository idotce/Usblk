package com.example.usbhosttool

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.jiangdg.ausbc.base.CameraActivity
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.ausbc.widget.IAspectRatio
import java.io.File

class CameraScreen : CameraActivity() {
    private lateinit var preview: AspectRatioTextureView
    private lateinit var container: FrameLayout
    private lateinit var status: TextView
    @Volatile private var recording = false
    private var worker: Thread? = null

    override fun getRootView(inflater: LayoutInflater): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { text = "等待 USB 摄像头授权" }
        root.addView(status)
        container = FrameLayout(this)
        preview = AspectRatioTextureView(this)
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "开始 / 停止 USB 音频采集"
            setOnClickListener { if (recording) recording = false else startAudio() }
        })
        WindowInsetsHelper.apply(root)
        return root
    }
    override fun getCameraView(): IAspectRatio = preview
    override fun getCameraViewContainer(): ViewGroup = container
    override fun getCameraRequest(): CameraRequest {
        val hd = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("video_hd", false)
        return CameraRequest.Builder().setPreviewWidth(if (hd) 1280 else 640)
            .setPreviewHeight(if (hd) 720 else 480).create()
    }
    override fun onCameraState(self: MultiCameraClient.ICamera, code: ICameraStateCallBack.State, msg: String?) {
        runOnUiThread { status.text = "摄像头：$code ${msg ?: ""}" }
    }
    private fun startAudio() {
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("uac", false)) {
            status.text = "请先在设置中开启 UAC"
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
            status.text = "授权后再次点击采集"
            return
        }
        if (worker?.isAlive == true) return
        val manager = getSystemService(AUDIO_SERVICE) as AudioManager
        val device = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (device == null) { status.text = "系统未识别到 USB 麦克风"; return }
        recording = true
        worker = Thread {
            var recorder: AudioRecord? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "不支持 48kHz 单声道 PCM16" }
                recorder = AudioRecord(MediaRecorder.AudioSource.DEFAULT, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minimum * 2)
                val active = recorder
                check(active.state == AudioRecord.STATE_INITIALIZED && active.setPreferredDevice(device)) { "USB 音频初始化失败" }
                active.startRecording()
                val file = File(filesDir, "usb-${System.currentTimeMillis()}-48k-mono-s16le.pcm")
                file.outputStream().use { output ->
                    val data = ByteArray(minimum)
                    while (recording) {
                        val n = active.read(data, 0, data.size, AudioRecord.READ_NON_BLOCKING)
                        check(n >= 0) { "音频设备断开或读取失败：$n" }
                        if (n == 0) { Thread.sleep(10); continue }
                        check(active.routedDevice?.id == device.id) { "音频路由不是所选 USB 麦克风，停止采集" }
                        output.write(data, 0, n)
                    }
                }
                runOnUiThread { status.text = "音频已保存：${file.absolutePath}" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "音频错误：${e.message}" }
            } finally {
                recording = false
                try { recorder?.stop() } catch (_: Exception) { }
                recorder?.release()
            }
        }.also { it.start() }
    }
    override fun onStop() { recording = false; super.onStop() }
}
