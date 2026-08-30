package dev.hacompanion.panel

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/**
 * The screen a ring puts in front of you.
 *
 * It draws the same view the camera page does. The two were separate
 * implementations of one thing and drifted: the page asks the bridge for a
 * live session and prefers a warmed one, while this screen replayed whatever
 * URL the layout was configured with — a Scrypted session that had long
 * since expired, which negotiated correctly and then never produced a frame.
 *
 * What is genuinely a ring's own stays here: waking the display, a timer
 * that closes the screen when nobody answers, and a CLOSE button, because a
 * ring is something you finish with where a page is something you swipe
 * away from.
 */
class RtspDoorbellActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }
    private var closeDeadline = 0L
    private var pausedCloseRemainingMs = 0L

    private val autoCloseMs by lazy {
        intent.getLongExtra(DoorbellIntent.EXTRA_AUTO_CLOSE_MS, 60_000L)
    }
    private val talkExtendMs by lazy {
        intent.getLongExtra(DoorbellIntent.EXTRA_TALK_EXTEND_MS, 15_000L).coerceIn(0L, 60_000L)
    }

    /**
     * The ring, described the way a camera widget is.
     *
     * The view takes its configuration from a widget, so the extras are
     * turned into one rather than the view learning a second vocabulary.
     */
    private val widget by lazy {
        DashboardWidget(
            type = "camera",
            label = intent.getStringExtra(DoorbellIntent.EXTRA_STREAM_NAME)
                ?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercase() },
            streamBaseUrl = intent.getStringExtra(DoorbellIntent.EXTRA_STREAM_BASE_URL)
                ?: DoorbellIntent.DEFAULT_STREAM_BASE_URL,
            streamName = intent.getStringExtra(DoorbellIntent.EXTRA_STREAM_NAME)
                ?: DoorbellIntent.DEFAULT_STREAM_NAME,
            talkbackUrl = intent.getStringExtra(DoorbellIntent.EXTRA_TALKBACK_URL)?.trim(),
            talkbackKey = intent.getStringExtra(DoorbellIntent.EXTRA_TALKBACK_KEY)?.trim(),
            // Quiet mode is a ring you can see but not hear.
            incomingAudio = !intent.getBooleanExtra(DoorbellIntent.EXTRA_QUIET_MODE, false),
            showIntercom = true,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A ring usually arrives while the display has timed out. Without these the
        // activity resumes behind a dark screen and the visitor is never seen.
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val camera = CameraPageView(
            this,
            widget,
            onClose = { finish() },
            // Quiet mode only says how the ring starts; muting mid-call is
            // the thing you reach for when the dog is barking.
            showMute = true,
            // Talking is answering: the screen must not close under you
            // mid-sentence, and it gets a grace period afterwards.
            onTalkingChanged = { talking ->
                if (talking) pauseAutoClose() else resumeAutoCloseWithGrace()
            },
        )
        setContentView(
            FrameLayout(this).apply {
                addView(camera, FrameLayout.LayoutParams(-1, -1))
                // The clock, and the microphone indicator. This screen is the
                // one that opens the microphone, so the indicator saying so
                // belongs here more than anywhere else in the app.
                addView(
                    PanelStatusView(this@RtspDoorbellActivity).apply {
                        configure(
                            intent.getBooleanExtra(EXTRA_SHOW_CLOCK, true),
                            intent.getBooleanExtra(EXTRA_SHOW_MIC_INDICATOR, true),
                            intent.getIntExtra(EXTRA_MIC_LINGER_SECONDS, 15),
                        )
                        synchronize(
                            intent.getLongExtra(EXTRA_SERVER_TIME_MS, System.currentTimeMillis()),
                            intent.getStringExtra(EXTRA_SERVER_TIMEZONE)
                                ?: java.util.TimeZone.getDefault().id,
                        )
                    },
                    FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                        val d = resources.displayMetrics.density
                        topMargin = (18 * d).toInt()
                        marginEnd = (20 * d).toInt()
                    },
                )
            },
        )
        if (autoCloseMs > 0) scheduleAutoClose(autoCloseMs)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        MicUsageTracker.setActive(this, false)
        super.onDestroy()
    }

    private fun pauseAutoClose() {
        if (autoCloseMs == 0L) return
        pausedCloseRemainingMs =
            (closeDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(1_000L)
        handler.removeCallbacks(autoClose)
    }

    private fun resumeAutoCloseWithGrace() {
        if (autoCloseMs == 0L) return
        scheduleAutoClose(
            (pausedCloseRemainingMs + talkExtendMs).coerceAtMost(MAX_AUTO_CLOSE_MS),
        )
    }

    private fun scheduleAutoClose(delayMs: Long) {
        handler.removeCallbacks(autoClose)
        closeDeadline = SystemClock.elapsedRealtime() + delayMs
        handler.postDelayed(autoClose, delayMs)
    }

    companion object {
        const val EXTRA_SHOW_CLOCK = "show_clock"
        const val EXTRA_SHOW_MIC_INDICATOR = "show_mic_indicator"
        const val EXTRA_MIC_LINGER_SECONDS = "mic_linger_seconds"
        const val EXTRA_SERVER_TIME_MS = "server_time_ms"
        const val EXTRA_SERVER_TIMEZONE = "server_timezone"
        private const val MAX_AUTO_CLOSE_MS = 300_000L
    }
}
