package dev.hacompanion.panel

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Choose which stream URL to play.
 *
 * Scrypted stream URLs are session scoped, so one resolved from the bridge now
 * is always preferred. The stored URL is the manual rebroadcast override, and
 * the only thing available when the bridge cannot be reached.
 */
internal fun chooseStreamSource(fresh: String?, stored: String?): String =
    fresh?.takeIf(String::isNotBlank) ?: stored.orEmpty()

/** Lightweight page-scoped RTSP player backed by Scrypted's prebuffered stream. */
class CameraPageView(
    context: Context,
    private val widget: DashboardWidget,
    private val openFullscreen: (DashboardWidget) -> Unit,
) : FrameLayout(context), SurfaceHolder.Callback {
    private val surface = SurfaceView(context)
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val status = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        setBackgroundColor(0x66000000)
        text = if (widget.streamBaseUrl.isNullOrBlank()) "Camera not configured" else "Connecting…"
    }
    private var player: MediaPlayer? = null
    private var attached = false
    private val connectTimeout = Runnable {
        if (attached && player != null) {
            status.alpha = 1f
            status.text = "Camera reconnecting…"
            releasePlayer()
            if (surface.holder.surface.isValid) handler.postDelayed({ startPlayer(surface.holder) }, 600)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, dp(38), Gravity.BOTTOM))
        surface.holder.addCallback(this)
        if (widget.tapAction != "none") setOnClickListener { openFullscreen(widget) }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = startPlayer(holder)
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = releasePlayer()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        // Some NSPanel firmware creates the Surface before delivering callbacks
        // registered by a dynamically inserted page. Cover that race explicitly.
        if (surface.holder.surface.isValid) post { startPlayer(surface.holder) }
    }

    override fun onDetachedFromWindow() {
        attached = false
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        super.onDetachedFromWindow()
    }

    private fun startPlayer(holder: SurfaceHolder) {
        if (player != null || !holder.surface.isValid) return
        status.alpha = 1f
        status.text = "Connecting to Scrypted prebuffer…"
        resolveSource { source ->
            if (!attached || player != null || !holder.surface.isValid) return@resolveSource
            if (source.isBlank()) {
                status.text = "Camera not configured"
                return@resolveSource
            }
            beginPlayback(holder, source)
        }
    }

    /** Ask the bridge where the stream is now, falling back to what the layout carries. */
    private fun resolveSource(onResolved: (String) -> Unit) {
        val endpoint = widget.talkbackUrl?.takeIf(String::isNotBlank)
        val key = widget.talkbackKey?.takeIf(String::isNotBlank)
        val stored = widget.streamBaseUrl
        if (endpoint == null || key == null) {
            onResolved(chooseStreamSource(null, stored))
            return
        }
        Thread {
            val fresh = runCatching {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer $key")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use ""
                    JSONObject(response.body?.string().orEmpty()).optString("video_url")
                }
            }.getOrElse {
                Log.w(TAG, "Could not resolve a current stream URL: ${it.message}")
                ""
            }
            handler.post { onResolved(chooseStreamSource(fresh, stored)) }
        }.start()
    }

    private fun beginPlayback(holder: SurfaceHolder, source: String) {
        releasePlayer()
        Log.i(TAG, "Starting camera stream ${Uri.parse(source).host}:${Uri.parse(source).port}")
        player = MediaPlayer().apply {
            setDisplay(holder)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setVolume(if (widget.incomingAudio) 1f else 0f, if (widget.incomingAudio) 1f else 0f)
            setDataSource(context, rtspUrl(source))
            setOnPreparedListener {
                handler.removeCallbacks(connectTimeout)
                it.start()
                status.text = "Live"
                status.postDelayed({ status.alpha = 0f }, 1_200)
            }
            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    status.alpha = 1f
                    status.text = "Buffering…"
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    status.text = "Live"
                    status.postDelayed({ status.alpha = 0f }, 1_200)
                }
                false
            }
            setOnErrorListener { _, what, extra ->
                handler.removeCallbacks(connectTimeout)
                Log.w(TAG, "Camera stream failed: $what/$extra")
                status.alpha = 1f
                status.text = "Stream unavailable · $what/$extra"
                true
            }
            prepareAsync()
        }
        handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    private fun rtspUrl(source: String): Uri {
        val uri = Uri.parse(source)
        if (uri.scheme == "rtsp") return uri
        if (!uri.path.isNullOrBlank() && uri.path != "/" && uri.port != 1984) {
            return uri.buildUpon().scheme("rtsp").build()
        }
        return uri.buildUpon()
            .scheme("rtsp")
            .encodedAuthority("${uri.host}:8554")
            .path("/${widget.streamName ?: "doorbell_sub"}")
            .clearQuery()
            .build()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(connectTimeout)
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "NSPanelCamera"
        private const val CONNECT_TIMEOUT_MS = 12_000L
    }
}
