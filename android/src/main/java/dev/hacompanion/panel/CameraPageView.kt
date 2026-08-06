package dev.hacompanion.panel

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView

/** Lightweight page-scoped RTSP player backed by Scrypted's prebuffered stream. */
class CameraPageView(
    context: Context,
    private val widget: DashboardWidget,
    private val openFullscreen: (DashboardWidget) -> Unit,
) : FrameLayout(context), SurfaceHolder.Callback {
    private val surface = SurfaceView(context)
    private val status = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        setBackgroundColor(0x66000000)
        text = if (widget.streamBaseUrl.isNullOrBlank()) "Camera not configured" else "Connecting…"
    }
    private var player: MediaPlayer? = null

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

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }

    private fun startPlayer(holder: SurfaceHolder) {
        val source = widget.streamBaseUrl?.takeIf(String::isNotBlank) ?: return
        releasePlayer()
        status.alpha = 1f
        status.text = "Connecting to Scrypted prebuffer…"
        player = MediaPlayer().apply {
            setDisplay(holder)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setVolume(if (widget.incomingAudio) 1f else 0f, if (widget.incomingAudio) 1f else 0f)
            setDataSource(context, rtspUrl(source))
            setOnPreparedListener {
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
                status.alpha = 1f
                status.text = "Stream unavailable · $what/$extra"
                true
            }
            prepareAsync()
        }
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
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
