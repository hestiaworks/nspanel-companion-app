package dev.hacompanion.panel

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import java.util.Locale

object DeviceDiagnostics {
    fun createReport(context: Context): String {
        val lines = mutableListOf<String>()
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
        val size = Point().also(display::getRealSize)
        val metrics = context.resources.displayMetrics
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
        val packageManager = context.packageManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        lines += "HA Companion diagnostic ${BuildConfig.VERSION_NAME}"
        lines += ""
        lines += "DEVICE"
        lines += "Manufacturer: ${Build.MANUFACTURER}"
        lines += "Model: ${Build.MODEL}"
        lines += "Product: ${Build.PRODUCT}"
        lines += "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        lines += "Security patch: ${Build.VERSION.SECURITY_PATCH.orUnknown()}"
        lines += "ABIs: ${Build.SUPPORTED_ABIS.joinToString()}"
        lines += ""
        lines += "DISPLAY"
        lines += "Physical pixels: ${size.x} × ${size.y}"
        lines += "Density: ${metrics.densityDpi} dpi (${format(metrics.density)}x)"
        lines += "Refresh rate: ${format(display.refreshRate)} Hz"
        lines += ""
        lines += "MEMORY"
        lines += "Total RAM: ${memory.totalMem.toMiB()} MiB"
        lines += "Available RAM: ${memory.availMem.toMiB()} MiB"
        lines += "Low-memory state: ${memory.lowMemory}"
        lines += "App memory class: ${activityManager.memoryClass} MiB"
        lines += ""
        lines += "CAPABILITIES"
        lines += "Microphone feature: ${packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)}"
        lines += "Microphone permission: ${permissionState(context)}"
        lines += "AudioRecord 16 kHz mono: ${audioRecordSupport(context)}"
        lines += "Touchscreen: ${packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)}"
        lines += "Network connected: ${connectivity.activeNetworkInfo?.isConnected == true}"
        lines += "Lock task permitted: ${dpm.isLockTaskPermitted(context.packageName)}"
        lines += "Android ID: ${Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)}"
        lines += "Panel device ID: ${PanelIdentityStore(context).deviceId}"
        lines += ""
        lines += "H.264 HARDWARE DECODERS"
        lines += h264Decoders().ifEmpty { listOf("None reported") }

        return lines.joinToString("\n")
    }

    private fun permissionState(context: Context): String =
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            "granted"
        } else {
            "not granted"
        }

    private fun audioRecordSupport(context: Context): String {
        val minimum = AudioRecord.getMinBufferSize(
            16_000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimum <= 0) return "unsupported ($minimum)"
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "permission required (minimum buffer: $minimum bytes)"
        }

        return try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                16_000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minimum * 2,
            )
            val state = if (recorder.state == AudioRecord.STATE_INITIALIZED) "supported" else "failed"
            recorder.release()
            state
        } catch (error: RuntimeException) {
            "failed (${error.javaClass.simpleName})"
        }
    }

    private fun h264Decoders(): List<String> {
        return MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .asSequence()
            .filterNot(MediaCodecInfo::isEncoder)
            .filter { codec ->
                codec.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }
            }
            .filterNot { it.name.startsWith("OMX.google.", ignoreCase = true) }
            .map { codec ->
                val capabilities = codec.getCapabilitiesForType("video/avc")
                val profiles = capabilities.profileLevels
                    .map { it.profile }
                    .distinct()
                    .sorted()
                    .joinToString()
                "${codec.name} (profiles: ${profiles.ifEmpty { "unknown" }})"
            }
            .toList()
    }

    private fun Long.toMiB(): Long = this / 1024L / 1024L
    private fun String?.orUnknown(): String = if (isNullOrBlank()) "unknown" else this
    private fun format(value: Float): String = String.format(Locale.US, "%.1f", value)
}
