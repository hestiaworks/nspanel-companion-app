package dev.hacompanion.panel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.webrtc.SurfaceViewRenderer

class DoorbellActivity : Activity() {
    private lateinit var renderer: SurfaceViewRenderer
    private lateinit var session: NativeDoorbellSession
    private lateinit var status: TextView
    private lateinit var countdown: TextView
    private lateinit var diagnostics: TextView
    private lateinit var talkButton: Button
    private lateinit var talkStatus: TextView
    private lateinit var incomingPanel: View
    private lateinit var activePanel: View
    private lateinit var retryButton: Button
    private lateinit var failureCloseButton: Button
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionPhase = DoorbellSessionPhase.CONNECTING
    private var microphoneAllowed = false
    private var talking = false
    private var answered = false
    private var closeDeadline = 0L
    private var pausedCloseRemainingMs = 0L

    private val streamBaseUrl: String by lazy {
        validatedStringExtra(EXTRA_STREAM_BASE_URL) {
            it.startsWith("http://") || it.startsWith("https://")
        } ?: DEFAULT_STREAM_BASE_URL
    }
    private val streamName: String by lazy {
        validatedStringExtra(EXTRA_STREAM_NAME) {
            it.matches(Regex("[A-Za-z0-9_-]+"))
        } ?: DEFAULT_STREAM_NAME
    }
    private val talkbackTestUrl: String? by lazy {
        validatedStringExtra(EXTRA_TALKBACK_TEST_URL) {
            BuildConfig.DEBUG && (it.startsWith("http://") || it.startsWith("https://"))
        }
    }
    private val autoCloseMs: Long by lazy {
        if (intent.hasExtra(EXTRA_AUTO_CLOSE_MS)) {
            intent.getLongExtra(EXTRA_AUTO_CLOSE_MS, DEFAULT_AUTO_CLOSE_MS)
                .let { if (it == 0L) 0L else it.coerceIn(10_000L, 300_000L) }
        } else {
            DEFAULT_AUTO_CLOSE_MS
        }
    }
    private val autoClose = Runnable { finish() }
    private val countdownTick = object : Runnable {
        override fun run() {
            updateCountdown()
            mainHandler.postDelayed(this, 500L)
        }
    }
    private val diagnosticsTick = object : Runnable {
        override fun run() {
            if (::session.isInitialized && BuildConfig.DEBUG) {
                session.requestDiagnostics { snapshot ->
                    runOnUiThread { showDiagnostics(snapshot) }
                }
            }
            mainHandler.postDelayed(this, 1_000L)
        }
    }
    private val quietMode: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_QUIET_MODE, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DoorbellProcessLifecycle.activityStarted()
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_USE_WEBVIEW, false)) {
            startActivity(Intent(this, WebViewDoorbellActivity::class.java).putExtras(intent))
            finish()
            return
        }
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        answered = intent.getBooleanExtra(EXTRA_START_TALKING, false)
        renderer = SurfaceViewRenderer(this)
        setContentView(createContent())
        enterImmersiveMode()
        resetAutoClose()
        mainHandler.post(countdownTick)
        if (BuildConfig.DEBUG) mainHandler.post(diagnosticsTick)

        microphoneAllowed =
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (microphoneAllowed) {
            startSession()
        } else {
            status.text = "Microphone permission required"
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_REQUEST,
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetAutoClose()
        window.decorView.post {
            enterImmersiveMode()
            if (::session.isInitialized && sessionPhase != DoorbellSessionPhase.LIVE) {
                session.retryNow()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        resetAutoClose()
    }

    override fun onPause() {
        setTalking(false)
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetAutoClose()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MICROPHONE_PERMISSION_REQUEST || ::session.isInitialized) return
        microphoneAllowed =
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        startSession()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoClose)
        mainHandler.removeCallbacks(countdownTick)
        mainHandler.removeCallbacks(diagnosticsTick)
        if (::session.isInitialized) session.stop()
        super.onDestroy()
        DoorbellProcessLifecycle.activityFinished()
    }

    private fun startSession() {
        session = NativeDoorbellSession(
            context = this,
            renderer = renderer,
            streamBaseUrl = streamBaseUrl,
            streamName = streamName,
            microphoneAllowed = microphoneAllowed,
            quietMode = quietMode,
            talkbackTestUrl = talkbackTestUrl,
            onStatus = ::showSessionStatus,
        )
        session.start()
        if (
            microphoneAllowed &&
            BuildConfig.DEBUG &&
            intent.getBooleanExtra(EXTRA_START_TALKING, false)
        ) {
            setTalking(true)
        }
    }

    private fun showSessionStatus(newStatus: DoorbellSessionStatus) {
        sessionPhase = newStatus.phase
        status.text = newStatus.detail
        retryButton.visibility =
            if (newStatus.phase == DoorbellSessionPhase.FAILED) View.VISIBLE else View.GONE
        failureCloseButton.visibility =
            if (newStatus.phase == DoorbellSessionPhase.FAILED) View.VISIBLE else View.GONE
        talkButton.isEnabled =
            answered && microphoneAllowed && newStatus.phase == DoorbellSessionPhase.LIVE
        if (newStatus.phase != DoorbellSessionPhase.LIVE) {
            if (talking) {
                talking = false
                resumeAutoClose()
            }
            talkButton.text = "Hold to talk"
            if (::talkStatus.isInitialized) talkStatus.text = "Waiting for live audio"
        } else if (::talkStatus.isInitialized && !talking) {
            talkStatus.text = if (microphoneAllowed) {
                "Press and hold to speak"
            } else {
                "Microphone permission unavailable"
            }
        }
    }

    private fun createContent(): View =
        FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                renderer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                View(this@DoorbellActivity).apply {
                    setBackgroundColor(Color.argb(58, 0, 0, 0))
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )

            val header = LinearLayout(this@DoorbellActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(8))
            }
            header.addView(
                LinearLayout(this@DoorbellActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(labelText("FRONT DOOR"))
                    addView(TextView(this@DoorbellActivity).apply {
                        text = "Doorbell"
                        textSize = 23f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                    })
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            status = TextView(this@DoorbellActivity).apply {
                text = "●  Connecting"
                textSize = 11f
                setTextColor(Color.WHITE)
                background = roundedBackground(Color.argb(190, 20, 22, 21), 18)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                maxLines = 1
            }
            header.addView(status)
            addView(
                header,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.TOP },
            )

            countdown = TextView(this@DoorbellActivity).apply {
                textSize = 10f
                setTextColor(Color.argb(210, 255, 255, 255))
                background = roundedBackground(Color.argb(170, 20, 22, 21), 12)
                setPadding(dp(8), dp(5), dp(8), dp(5))
            }
            addView(
                countdown,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(58)
                    rightMargin = dp(16)
                },
            )

            diagnostics = TextView(this@DoorbellActivity).apply {
                visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
                textSize = 9f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                background = roundedBackground(Color.argb(150, 0, 0, 0), 10)
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }
            addView(
                diagnostics,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = dp(16)
                    topMargin = dp(72)
                },
            )

            retryButton = actionButton("Retry", COLOR_NEUTRAL, dp(104)) {
                resetAutoClose()
                session.retryNow()
            }.apply { visibility = View.GONE }
            addView(
                retryButton,
                FrameLayout.LayoutParams(dp(104), dp(48)).apply {
                    gravity = Gravity.CENTER
                    leftMargin = -dp(112)
                },
            )
            failureCloseButton = actionButton("Close", COLOR_DANGER, dp(104)) { finish() }
                .apply { visibility = View.GONE }
            addView(
                failureCloseButton,
                FrameLayout.LayoutParams(dp(104), dp(48)).apply {
                    gravity = Gravity.CENTER
                    rightMargin = -dp(112)
                },
            )

            incomingPanel = callPanel(
                title = "Someone is at the door",
                subtitle = "Ringing now",
                actions = LinearLayout(this@DoorbellActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(
                        actionButton("Dismiss", COLOR_DANGER, dp(122)) { finish() },
                        LinearLayout.LayoutParams(dp(122), dp(58)).apply { rightMargin = dp(8) },
                    )
                    addView(
                        actionButton("Answer", COLOR_ANSWER, dp(122)) { setAnswered(true) },
                        LinearLayout.LayoutParams(dp(122), dp(58)).apply { leftMargin = dp(8) },
                    )
                },
            )
            addView(
                incomingPanel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(dp(16), dp(16), dp(16), dp(16))
                },
            )

            talkButton = actionButton("Hold to talk", COLOR_ACCENT, dp(180)) {}.apply {
                isEnabled = false
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> setTalking(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setTalking(false)
                    }
                    true
                }
            }
            talkStatus = TextView(this@DoorbellActivity).apply {
                text = "Waiting for live audio"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.argb(180, 255, 255, 255))
            }
            val activeActions = LinearLayout(this@DoorbellActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                addView(
                    actionButton("End", COLOR_DANGER, dp(92)) { finish() },
                    LinearLayout.LayoutParams(dp(92), dp(58)).apply { rightMargin = dp(12) },
                )
                addView(
                    LinearLayout(this@DoorbellActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        addView(talkButton, LinearLayout.LayoutParams(dp(180), dp(58)))
                    },
                )
            }
            activePanel = callPanel("Connected", "", activeActions).also {
                it.addView(
                    talkStatus,
                    1,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(8) },
                )
                it.visibility = if (answered) View.VISIBLE else View.GONE
            }
            incomingPanel.visibility = if (answered) View.GONE else View.VISIBLE
            addView(
                activePanel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(dp(16), dp(16), dp(16), dp(16))
                },
            )
        }

    private fun setAnswered(value: Boolean) {
        answered = value
        incomingPanel.visibility = if (value) View.GONE else View.VISIBLE
        activePanel.visibility = if (value) View.VISIBLE else View.GONE
        talkButton.isEnabled =
            value && microphoneAllowed && sessionPhase == DoorbellSessionPhase.LIVE
        resetAutoClose()
    }

    private fun callPanel(title: String, subtitle: String, actions: View): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.argb(226, 20, 22, 21), 24)
            addView(TextView(this@DoorbellActivity).apply {
                text = title
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            })
            if (subtitle.isNotBlank()) {
                addView(TextView(this@DoorbellActivity).apply {
                    text = subtitle
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.argb(180, 255, 255, 255))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(12) })
            }
            addView(actions)
        }

    private fun labelText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setTextColor(Color.argb(190, 255, 255, 255))
    }

    private fun actionButton(
        label: String,
        color: Int,
        width: Int,
        action: () -> Unit,
    ): Button = Button(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        minWidth = width
        minHeight = dp(48)
        setTextColor(Color.WHITE)
        background = roundedBackground(color, 18)
        setOnClickListener { action() }
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun setTalking(enabled: Boolean) {
        if (!::talkButton.isInitialized || !::session.isInitialized) return
        val accepted = session.setTalkEnabled(enabled)
        talking = enabled && accepted
        talkButton.text = if (talking) "Speaking…" else "Hold to talk"
        talkStatus.text = if (talking) "Microphone is live" else "Press and hold to speak"
        if (talking) pauseAutoClose() else resumeAutoClose()
    }

    private fun resetAutoClose() {
        mainHandler.removeCallbacks(autoClose)
        pausedCloseRemainingMs = autoCloseMs
        if (autoCloseMs > 0L && !talking) scheduleAutoClose(autoCloseMs)
        updateCountdown()
    }

    private fun pauseAutoClose() {
        if (autoCloseMs == 0L) return
        pausedCloseRemainingMs =
            (closeDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1_000L)
        mainHandler.removeCallbacks(autoClose)
        updateCountdown()
    }

    private fun resumeAutoClose() {
        if (autoCloseMs == 0L || talking) return
        scheduleAutoClose(pausedCloseRemainingMs.coerceAtLeast(1_000L))
    }

    private fun scheduleAutoClose(delayMs: Long) {
        mainHandler.removeCallbacks(autoClose)
        closeDeadline = android.os.SystemClock.elapsedRealtime() + delayMs
        mainHandler.postDelayed(autoClose, delayMs)
        updateCountdown()
    }

    private fun updateCountdown() {
        if (!::countdown.isInitialized) return
        countdown.text = when {
            autoCloseMs == 0L -> "Auto-close off"
            talking -> "Auto-close paused"
            else -> {
                val remaining =
                    (closeDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                "Closing in ${(remaining + 999L) / 1_000L}s"
            }
        }
    }

    private fun showDiagnostics(snapshot: DoorbellDiagnostics) {
        if (!::diagnostics.isInitialized || isFinishing) return
        val connection = snapshot.connectionTimeMs?.let { "${it}ms" } ?: "—"
        diagnostics.text = buildString {
            append("${snapshot.width}×${snapshot.height}  ")
            append(String.format(java.util.Locale.US, "%.1f fps", snapshot.framesPerSecond))
            append("\n${snapshot.codec} · ${snapshot.decoder}")
            append("\nconnect $connection · retries ${snapshot.reconnectCount}")
            append("\nmedia ${snapshot.memoryPssMb} MB PSS")
            if (quietMode) append(" · quiet")
        }
    }

    private fun controlButton(label: String, action: () -> Unit = {}): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun validatedStringExtra(name: String, validator: (String) -> Boolean): String? =
        intent.getStringExtra(name)?.trim()?.trimEnd('/')?.takeIf(validator)

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_STREAM_BASE_URL = "dev.hacompanion.panel.DOORBELL_BASE_URL"
        const val EXTRA_STREAM_NAME = "dev.hacompanion.panel.DOORBELL_STREAM"
        const val EXTRA_START_TALKING = "dev.hacompanion.panel.DOORBELL_START_TALKING"
        const val EXTRA_USE_WEBVIEW = "dev.hacompanion.panel.DOORBELL_USE_WEBVIEW"
        const val EXTRA_AUTO_CLOSE_MS = "dev.hacompanion.panel.DOORBELL_AUTO_CLOSE_MS"
        const val EXTRA_QUIET_MODE = "dev.hacompanion.panel.DOORBELL_QUIET_MODE"
        const val EXTRA_TALKBACK_TEST_URL = "dev.hacompanion.panel.DOORBELL_TALKBACK_TEST_URL"
        const val EXTRA_TALKBACK_URL = "dev.hacompanion.panel.DOORBELL_TALKBACK_URL"
        const val EXTRA_TALKBACK_KEY = "dev.hacompanion.panel.DOORBELL_TALKBACK_KEY"
        internal const val DEFAULT_STREAM_BASE_URL = "http://192.0.2.76:1984"
        internal const val DEFAULT_STREAM_NAME = "doorbell_sub"
        private const val COLOR_ACCENT = 0xFFF17832.toInt()
        private const val COLOR_ANSWER = 0xFF32965A.toInt()
        private const val COLOR_DANGER = 0xFFC94B45.toInt()
        private const val COLOR_NEUTRAL = 0xFF4C514E.toInt()
        private const val DEFAULT_AUTO_CLOSE_MS = 60_000L
        private const val MICROPHONE_PERMISSION_REQUEST = 21
    }
}
