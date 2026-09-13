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
import android.hardware.Sensor
import android.hardware.SensorManager
import android.provider.Settings
import android.view.WindowManager
import java.util.Locale

/**
 * What the light sensor says, and what the panel is doing about it.
 *
 * Reported so a brightness curve can be built from what these rooms
 * actually read rather than from the units the sensor claims. This hardware
 * reports its proximity sensor as raw reflectance rather than centimetres,
 * and the light sensor reads about 8890 in a lit room in the evening, which
 * is far too high to be lux — so the number means nothing until it has been
 * watched moving.
 */
internal fun ambientLight(context: Context): String {
    val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val sensor = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return "no sensor"
    val reading = LightReading.latest
    val auto = Settings.System.getInt(
        context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1,
    )
    return buildString {
        append(if (reading == null) "not read yet" else String.format(Locale.US, "%.1f", reading))
        append(" (range ${String.format(Locale.US, "%.1f", sensor.maximumRange)}")
        append(", android auto-brightness ")
        append(
            when (auto) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> "on"
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> "off"
                else -> "unknown"
            },
        )
        append(")")
    }
}

/**
 * The last thing the light sensor said.
 *
 * Held rather than asked for: a sensor reports on change, so a reading taken
 * at the moment a report is built could be minutes old or absent entirely.
 * One listener, kept by the activity, costs nothing on a panel where the
 * system already samples this sensor for its own brightness.
 */
object LightReading {
    @Volatile
    var latest: Float? = null
}

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
        lines += "Ambient light: ${ambientLight(context)}"
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
