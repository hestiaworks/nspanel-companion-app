package dev.hacompanion.panel

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    /** A URL warmed while the camera was one swipe away, if there is one. */
    private val claimWarmed: () -> String? = { null },
    /**
     * Shown as a CLOSE button beside the talk button when the page is a
     * doorbell rather than a dashboard page: a ring is something you finish
     * with, where a page is something you swipe away from.
     */
    private val onClose: (() -> Unit)? = null,
    /**
     * Offer a mute toggle beside the talk button.
     *
     * A ring is the one place incoming audio needs turning off mid-call —
     * the layout's quiet mode only decides how it starts.
     */
    private val showMute: Boolean = false,
    /** Told while the microphone is live, so a ring can hold its timer open. */
    private val onTalkingChanged: (Boolean) -> Unit = {},
) : FrameLayout(context), TextureView.SurfaceTextureListener {
    /**
     * A TextureView, not a SurfaceView.
     *
     * A SurfaceView needs its own window layer punched through the hierarchy,
     * and inside Compose's AndroidView it is never given one: measured on the
     * panel, the view sits attached, visible and correctly sized at 472x439
     * with its surface still invalid a second later, and surfaceCreated never
     * arrives. Four cold starts in five stopped at "Connecting…" that way.
     *
     * A TextureView composites as an ordinary texture in the view hierarchy,
     * which is what Compose is drawing anyway. It costs a GPU copy per frame
     * that an overlay would not — cheap for one 640x480 stream on a 480 px
     * panel, and it is what makes the page work at all.
     */
    private val surface = TextureView(context)
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private var talkback: PcmTalkbackStreamer? = null

    /**
     * The hold-to-talk button, when the page is configured to offer one.
     *
     * The page is already full-bleed, so the button lives on it rather than
     * behind a tap that opens the same picture again — which is what the
     * fullscreen action it replaced did.
     */
    private val talkButton: TextView? =
        if (!widget.showIntercom || widget.talkbackUrl.isNullOrBlank() ||
            widget.talkbackKey.isNullOrBlank()
        ) null else TextView(context).apply {
            text = "HOLD TO TALK"
            gravity = Gravity.CENTER
            textSize = 18f
            letterSpacing = .08f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#0E1012"))
            setBackgroundColor(Color.parseColor("#4F8FFF"))
        }

    /** Starts wherever quiet mode left it, and is changeable from here on. */
    private var muted = !widget.incomingAudio

    /**
     * A secondary button, built the way the intercom call screen builds its
     * pair: a 26 px glyph over a 16 px label, centred, 8 px apart.
     *
     * Destructive is the only coloured one and sits on the right, which is
     * the rule the whole design follows for a confirm.
     */
    private fun secondary(
        glyph: String,
        label: String,
        danger: Boolean = false,
        onTap: () -> Unit,
    ): LinearLayout {
        val ink = if (danger) "#0E1012" else "#F2F5F7"
        val mark = TextView(context).apply {
            // Text presentation, or Android draws these from the colour
            // emoji font and the tint is ignored.
            text = glyph + "\ufe0e"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (danger) ink else "#8A9299"))
        }
        val caption = TextView(context).apply {
            text = label
            textSize = 16f
            letterSpacing = .08f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor(ink))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor(if (danger) "#D24A3F" else "#14171A"))
            addView(mark, LinearLayout.LayoutParams(-2, -2))
            addView(
                caption,
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) },
            )
            setOnClickListener { onTap() }
        }
    }

    private val muteButton: LinearLayout? =
        if (!showMute) null
        else secondary("\u2298", if (muted) "UNMUTE" else "MUTE") { toggleMute() }

    /** The caption inside the mute button, which changes with its state. */
    private val muteLabel: TextView?
        get() = muteButton?.getChildAt(1) as? TextView

    private val closeButton: LinearLayout? =
        onClose?.let { close -> secondary("\u2715", "CLOSE", danger = true) { close() } }

    private fun toggleMute() {
        muted = !muted
        applyVolume()
        muteLabel?.text = if (muted) "UNMUTE" else "MUTE"
    }

    private fun applyVolume() {
        val level = if (muted) 0f else 1f
        runCatching { player?.setVolume(level, level) }
    }

    private val stripes = StripedBackground(context)
    private val badge = LiveBadge(context)

    /** The camera's name, which the badge says the state of. */
    private val name = widget.label?.takeIf(String::isNotBlank) ?: "Camera"

    /**
     * Say what the stream is doing, in the corner rather than along the foot.
     *
     * The stripes go once there is a picture to cover them, and come back
     * whenever there is not — so the page never shows a black rectangle and
     * leaves you guessing whether it is loading or broken.
     */
    private fun say(state: String, live: Boolean = false) {
        badge.show("$name · $state", live)
        stripes.visibility = if (live) GONE else VISIBLE
    }
    private var player: MediaPlayer? = null

    // Phase timings, so the start-up cost can be attributed rather than guessed.
    private var startedAt = 0L

    // A warmed URL is a session someone else minted; if it turns out to be
    // dead, the page resolves for itself once rather than showing a failure.
    private var playingWarmed = false
    private var retriedFresh = false
    private fun since() = android.os.SystemClock.elapsedRealtime() - startedAt
    private var attached = false
    private val connectTimeout = Runnable {
        if (attached && player != null) {
            say("reconnecting")
            releasePlayer()
            currentSurface()?.let { ready -> handler.postDelayed({ startPlayer(ready) }, 600) }
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        // The picture gives up the button row's height rather than being
        // covered by it: a talk button over the doorstep is a talk button
        // you cannot see past.
        // Talk takes the width it can get and close takes a fixed 132 px
        // beside it, so the thing you reach for in a hurry is the big one.
        val row = if (talkButton == null && muteButton == null && closeButton == null) null
        else LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            talkButton?.let {
                addView(it, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 2f))
            }
            listOfNotNull(muteButton, closeButton).forEach {
                val width = if (talkButton == null) 0 else dp(132)
                addView(
                    it,
                    LinearLayout.LayoutParams(width, LayoutParams.MATCH_PARENT).apply {
                        if (talkButton == null) weight = 1f
                    },
                )
            }
        }
        val below = if (row == null) 0 else dp(88)
        fun picture() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            .apply { bottomMargin = below }
        addView(stripes, picture())
        addView(surface, picture())
        addView(badge, LiveBadge.layout(context))
        row?.let { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, dp(88), Gravity.BOTTOM)) }
        talkButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startTalking()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopTalking()
            }
            true
        }
        surface.surfaceTextureListener = this
        say(if (widget.streamBaseUrl.isNullOrBlank()) "not configured" else "connecting")
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) =
        startPlayer(Surface(texture))

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    /** The surface currently being drawn into, or null when there is none. */
    private fun currentSurface(): Surface? =
        surface.surfaceTexture?.let { Surface(it) }?.takeIf { surface.isAvailable }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        // The texture can already exist when a page is re-attached, in which
        // case no listener callback is coming and nothing would start.
        currentSurface()?.let { ready -> post { startPlayer(ready) } }
    }

    /**
     * Let the stream go while something is covering the page.
     *
     * The fullscreen doorbell is a separate activity over this one, and the
     * view stays attached underneath it — so without this the page keeps
     * decoding and the two ask Scrypted for a session at once. The doorbell
     * loses: measured on the panel, its player comes back 100/0, server
     * died. One camera, one stream at a time.
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            handler.removeCallbacksAndMessages(null)
            releasePlayer()
        } else {
            currentSurface()?.let { ready -> post { startPlayer(ready) } }
        }
    }

    override fun onDetachedFromWindow() {
        attached = false
        handler.removeCallbacksAndMessages(null)
        stopTalking()
        talkback?.stop()
        talkback = null
        releasePlayer()
        super.onDetachedFromWindow()
    }

    private fun startPlayer(target: Surface) {
        if (player != null || !target.isValid) return
        startedAt = android.os.SystemClock.elapsedRealtime()
        retriedFresh = false
        Log.i(TAG, "timing: surface ready, resolving source")
        say("connecting")
        resolveSource(allowWarmed = true) { source ->
            Log.i(TAG, "timing: source resolved at ${since()} ms")
            if (!attached || player != null || !target.isValid) return@resolveSource
            if (source.isBlank()) {
                say("not configured")
                return@resolveSource
            }
            beginPlayback(target, source)
        }
    }

    /** Ask the bridge where the stream is now, falling back to what the layout carries. */
    private fun resolveSource(allowWarmed: Boolean, onResolved: (String) -> Unit) {
        val endpoint = widget.talkbackUrl?.takeIf(String::isNotBlank)
        val key = widget.talkbackKey?.takeIf(String::isNotBlank)
        val stored = widget.streamBaseUrl
        if (allowWarmed) {
            val warmed = claimWarmed()?.takeIf(String::isNotBlank)
            if (warmed != null) {
                playingWarmed = true
                Log.i(TAG, "timing: warmed source used at ${since()} ms")
                onResolved(warmed)
                return
            }
        }
        playingWarmed = false
        if (endpoint == null || key == null) {
            onResolved(chooseStreamSource(null, stored))
            return
        }
        Thread {
            val fresh = fetchStreamUrl(client, endpoint, key)
            handler.post { onResolved(chooseStreamSource(fresh, stored)) }
        }.start()
    }

    private fun beginPlayback(target: Surface, source: String) {
        releasePlayer()
        Log.i(TAG, "Starting camera stream ${Uri.parse(source).host}:${Uri.parse(source).port}")
        player = MediaPlayer().apply {
            setSurface(target)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            val level = if (muted) 0f else 1f
            setVolume(level, level)
            setDataSource(context, rtspUrl(source))
            setOnPreparedListener {
                Log.i(TAG, "timing: prepared at ${since()} ms")
                handler.removeCallbacks(connectTimeout)
                it.start()
                say("live", live = true)
            }
            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    Log.i(TAG, "timing: first frame at ${since()} ms")
                }
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    say("buffering")
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    say("live", live = true)
                }
                false
            }
            setOnErrorListener { _, what, extra ->
                handler.removeCallbacks(connectTimeout)
                Log.w(TAG, "Camera stream failed: $what/$extra")
                if (retryWithFreshSource()) return@setOnErrorListener true
                say("unavailable")
                true
            }
            Log.i(TAG, "timing: prepareAsync at ${since()} ms")
            prepareAsync()
        }
        handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    /**
     * Resolve again without the warmed URL, once, if that is what failed. A
     * warmed session can die before it is played; falling back to the request
     * the page would have made anyway costs the user a delay, not the stream.
     */
    private fun retryWithFreshSource(): Boolean {
        if (!playingWarmed || retriedFresh) return false
        val target = currentSurface() ?: return false
        if (!attached) return false
        retriedFresh = true
        Log.i(TAG, "Warmed session was dead; resolving a fresh one")
        releasePlayer()
        say("reconnecting")
        resolveSource(allowWarmed = false) { source ->
            if (!attached || player != null || !target.isValid) return@resolveSource
            if (source.isBlank()) {
                say("not configured")
                return@resolveSource
            }
            beginPlayback(target, source)
        }
        return true
    }

    /**
     * Begin talking, asking for the microphone the first time.
     *
     * A View cannot request a permission, so the request goes through the
     * activity hosting the page; the button does nothing until it is
     * granted, and the next press works.
     */
    private fun startTalking() {
        val endpoint = widget.talkbackUrl ?: return
        val key = widget.talkbackKey ?: return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            say("microphone not allowed")
            hostActivity()?.requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), MainActivity.MICROPHONE_REQUEST,
            )
            return
        }
        if (talkback == null) {
            talkback = PcmTalkbackStreamer(endpoint, key) { message ->
                handler.post { if (message.contains("failed", true)) say("talkback failed") }
            }
        }
        talkback?.setTalking(true)
        MicUsageTracker.setActive(context, true)
        onTalkingChanged(true)
        talkButton?.text = "RELEASE TO STOP"
    }

    private fun stopTalking() {
        talkback?.setTalking(false)
        MicUsageTracker.setActive(context, false)
        onTalkingChanged(false)
        talkButton?.text = "HOLD TO TALK"
    }

    private tailrec fun Context.unwrap(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.unwrap()
        else -> null
    }

    private fun hostActivity(): Activity? = context.unwrap()

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
