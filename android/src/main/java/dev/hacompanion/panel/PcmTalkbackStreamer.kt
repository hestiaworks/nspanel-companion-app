package dev.hacompanion.panel

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Streams microphone PCM independently from the receive-only RTSP player. */
class PcmTalkbackStreamer(
    private val endpoint: String,
    private val accessKey: String,
    private val onStatus: (String) -> Unit,
) {
    private val active = AtomicBoolean(false)
    private val talking = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var worker: Thread? = null

    fun start() {
        if (!active.compareAndSet(false, true)) return
        worker = Thread({ stream() }, "nspanel-pcm-talkback").also { it.start() }
    }

    fun stop() {
        talking.set(false)
        active.set(false)
        recorder?.runCatching { stop() }
    }

    fun setTalking(enabled: Boolean) {
        talking.set(enabled)
        onStatus(if (enabled) "Talking" else "Talkback ready")
    }

    private fun stream() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            active.set(false)
            onStatus("Microphone unavailable")
            return
        }
        try {
            onStatus("Warming talkback…")
            while (active.get()) streamOneSession(minBuffer)
            onStatus("Talkback closed")
        }
        catch (error: Exception) {
            Log.e(TAG, "Talkback streaming failed", error)
            if (active.get()) onStatus(error.message?.take(100) ?: "Talkback failed")
        }
        finally {
            active.set(false)
            recorder?.runCatching { stop() }
            recorder?.release()
            recorder = null
            worker = null
        }
    }

    private fun streamOneSession(minBuffer: Int) {
        val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = -1L
                override fun writeTo(sink: BufferedSink) {
                    val sessionStarted = SystemClock.elapsedRealtime()
                    val buffer = ByteArray(SILENCE_BYTES_PER_FRAME)
                    val silence = ByteArray(SILENCE_BYTES_PER_FRAME)
                    var record: AudioRecord? = null
                    while (active.get() &&
                        (talking.get() || SystemClock.elapsedRealtime() - sessionStarted < SESSION_ROTATE_MS)
                    ) {
                        if (talking.get()) {
                            if (record == null) {
                                record = createRecorder(minBuffer).also {
                                    recorder = it
                                    it.startRecording()
                                }
                            }
                            val count = record.read(buffer, 0, buffer.size)
                            if (count > 0) sink.write(buffer, 0, count)
                        } else {
                            record?.runCatching { stop() }
                            record?.release()
                            record = null
                            recorder = null
                            sink.write(silence)
                            Thread.sleep(FRAME_MILLIS)
                        }
                        sink.emit()
                    }
                    record?.runCatching { stop() }
                    record?.release()
                    recorder = null
                }
            }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $accessKey")
            .header("X-NSPanel-Audio-Format", "s16le;rate=16000;channels=1")
            .post(body)
            .build()
        Log.i(TAG, "Starting warm PCM session")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string()?.take(120).orEmpty()
                throw IllegalStateException("Talkback HTTP ${response.code}${if (detail.isBlank()) "" else ": $detail"}")
            }
        }
        Log.i(TAG, "Warm PCM session rotated")
    }

    private fun createRecorder(minBuffer: Int): AudioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBuffer * 4, 4096),
    )

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val TAG = "NSPanelTalkback"
        private const val FRAME_MILLIS = 20L
        private const val SILENCE_BYTES_PER_FRAME = 640
        private const val SESSION_ROTATE_MS = 20_000L
    }
}
