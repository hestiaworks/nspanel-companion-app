package dev.hacompanion.panel

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

/** Debug harness hook that records the exact PCM frames supplied to libwebrtc. */
class TalkbackTestRecorder(
    private val uploadUrl: String?,
    private val httpClient: OkHttpClient,
) {
    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "talkback-test-recorder").apply { isDaemon = true }
    }
    private var output: ByteArrayOutputStream? = null
    private var sampleRate = 48_000
    private var channels = 1
    private var lastLevelAt = 0L

    fun start() {
        if (uploadUrl == null) return
        worker.execute {
            synchronized(lock) { output = ByteArrayOutputStream() }
            postJson(
                "$uploadUrl/level",
                JSONObject().put("active", true).put("rms", 0).put("peak", 0),
            )
        }
    }

    fun onSamples(samples: JavaAudioDeviceModule.AudioSamples, talking: Boolean) {
        if (!talking || uploadUrl == null || samples.audioFormat != 2) return
        val data = samples.data.copyOf()
        val rate = samples.sampleRate
        val channelCount = samples.channelCount
        worker.execute { processSamples(data, rate, channelCount) }
    }

    private fun processSamples(data: ByteArray, rate: Int, channelCount: Int) {
        var rms = 0.0
        var peak = 0
        val pcm = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var count = 0
        while (pcm.remaining() >= 2) {
            val value = pcm.short.toInt()
            rms += value.toDouble() * value
            peak = maxOf(peak, abs(value))
            count++
        }
        synchronized(lock) {
            output?.write(data)
            sampleRate = rate
            channels = channelCount
        }
        val now = System.currentTimeMillis()
        if (count > 0 && now - lastLevelAt >= 200L) {
            lastLevelAt = now
            postJson(
                "$uploadUrl/level",
                JSONObject()
                    .put("active", true)
                    .put("rms", sqrt(rms / count) / 32768.0)
                    .put("peak", peak / 32768.0),
            )
        }
    }

    fun finishAndUpload() {
        worker.execute(::finishAndUploadOnWorker)
    }

    private fun finishAndUploadOnWorker() {
        val pcm: ByteArray
        val rate: Int
        val channelCount: Int
        synchronized(lock) {
            pcm = output?.toByteArray() ?: return
            output = null
            rate = sampleRate
            channelCount = channels
        }
        postJson("$uploadUrl/level", JSONObject().put("active", false).put("rms", 0).put("peak", 0))
        if (pcm.isEmpty()) return
        val wav = wav(pcm, rate, channelCount)
        val request = Request.Builder()
            .url(uploadUrl ?: return)
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        httpClient.newCall(request).enqueue(discardingCallback)
    }

    fun cancel() {
        worker.execute {
            synchronized(lock) { output = null }
            uploadUrl?.let {
                postJson("$it/level", JSONObject().put("active", false).put("rms", 0).put("peak", 0))
            }
        }
    }

    fun close() {
        cancel()
        worker.shutdown()
    }

    private fun postJson(url: String, json: JSONObject) {
        val request = Request.Builder().url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).enqueue(discardingCallback)
    }

    private fun wav(pcm: ByteArray, rate: Int, channelCount: Int): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(channelCount.toShort()).putInt(rate)
        header.putInt(rate * channelCount * 2).putShort((channelCount * 2).toShort()).putShort(16)
        header.put("data".toByteArray()).putInt(pcm.size)
        return header.array() + pcm
    }

    private val discardingCallback = object : Callback {
        override fun onFailure(call: Call, e: IOException) = Unit
        override fun onResponse(call: Call, response: Response) = response.close()
    }
}
