package dev.hacompanion.panel

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Receive-only native RTSP player backed by Scrypted's go2rtc prebuffer. */
class RtspDoorbellActivity : Activity(), SurfaceHolder.Callback {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var surface: SurfaceView
    private lateinit var status: TextView
    private var player: MediaPlayer? = null
    private var muted = false
    private var talkback: PcmTalkbackStreamer? = null
    private lateinit var talkButton: Button

    private val baseUrl by lazy {
        intent.getStringExtra(DoorbellActivity.EXTRA_STREAM_BASE_URL)
            ?: DoorbellActivity.DEFAULT_STREAM_BASE_URL
    }
    private val streamName by lazy {
        intent.getStringExtra(DoorbellActivity.EXTRA_STREAM_NAME)
            ?: DoorbellActivity.DEFAULT_STREAM_NAME
    }
    private val autoCloseMs by lazy {
        intent.getLongExtra(DoorbellActivity.EXTRA_AUTO_CLOSE_MS, 60_000L)
    }
    private val talkbackUrl by lazy {
        intent.getStringExtra(DoorbellActivity.EXTRA_TALKBACK_URL)?.trim()
    }
    private val talkbackKey by lazy {
        intent.getStringExtra(DoorbellActivity.EXTRA_TALKBACK_KEY)?.trim()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(buildContent())
        surface.holder.addCallback(this)
        prepareTalkback()
        if (autoCloseMs > 0) handler.postDelayed({ finish() }, autoCloseMs)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = startPlayer(holder)
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = releasePlayer()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        talkback?.stop()
        releasePlayer()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MICROPHONE_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) prepareTalkback()
    }

    private fun startPlayer(holder: SurfaceHolder) {
        releasePlayer()
        status.text = "Connecting to Scrypted prebuffer…"
        player = MediaPlayer().apply {
            setDisplay(holder)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setDataSource(this@RtspDoorbellActivity, rtspUrl())
            setOnPreparedListener {
                it.start()
                status.text = "Live · Scrypted prebuffer"
            }
            setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> status.text = "Buffering…"
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> status.text = "Live · Scrypted prebuffer"
                }
                false
            }
            setOnErrorListener { _, what, extra ->
                status.text = "Stream unavailable · $what/$extra"
                true
            }
            prepareAsync()
        }
    }

    private fun rtspUrl(): Uri {
        val source = Uri.parse(baseUrl)
        if (source.scheme == "rtsp") return source
        if (!source.path.isNullOrBlank() && source.path != "/" && source.port != 1984) {
            return source.buildUpon().scheme("rtsp").build()
        }
        return source.buildUpon()
            .scheme("rtsp")
            .encodedAuthority("${source.host}:8554")
            .path("/$streamName")
            .clearQuery()
            .build()
    }

    private fun buildContent(): View = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        surface = SurfaceView(this@RtspDoorbellActivity)
        addView(surface, FrameLayout.LayoutParams(-1, -1))

        addView(LinearLayout(this@RtspDoorbellActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 18, 24, 18)
            setBackgroundColor(0xAA111111.toInt())
            status = TextView(this@RtspDoorbellActivity).apply {
                text = "Doorbell"
                textSize = 18f
                setTextColor(Color.WHITE)
            }
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@RtspDoorbellActivity).apply {
                text = "Mute"
                setOnClickListener {
                    muted = !muted
                    val volume = if (muted) 0f else 1f
                    player?.setVolume(volume, volume)
                    text = if (muted) "Unmute" else "Mute"
                }
            })
            addView(Button(this@RtspDoorbellActivity).apply {
                text = "Close"
                setOnClickListener { finish() }
            })
        }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        talkButton = Button(this@RtspDoorbellActivity).apply {
            text = if (talkbackConfigured()) "Hold to talk" else "Talkback not configured"
            isEnabled = talkbackConfigured()
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startTalking()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopTalking()
                        true
                    }
                    else -> true
                }
            }
        }
        addView(talkButton, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun releasePlayer() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    private fun talkbackConfigured(): Boolean =
        !talkbackUrl.isNullOrBlank() && !talkbackKey.isNullOrBlank()

    private fun startTalking() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Microphone permission required"
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_REQUEST)
            return
        }
        prepareTalkback()
        talkback?.setTalking(true)
        talkButton.text = "Talking · release to stop"
    }

    private fun stopTalking() {
        talkback?.setTalking(false)
        talkButton.text = "Hold to talk"
    }

    private fun prepareTalkback() {
        if (!talkbackConfigured() || talkback != null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION_REQUEST)
            return
        }
        val endpoint = talkbackUrl ?: return
        val key = talkbackKey ?: return
        talkback = PcmTalkbackStreamer(endpoint, key) { message ->
            runOnUiThread {
                status.text = message
                if (message.contains("failed", ignoreCase = true) || message.startsWith("Talkback HTTP")) {
                    talkButton.text = "Talkback error"
                }
            }
        }.also { it.start() }
    }

    companion object {
        private const val MICROPHONE_PERMISSION_REQUEST = 73
    }
}
