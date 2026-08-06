package dev.hacompanion.panel

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

/** Temporary comparison fallback. Production doorbell rendering is native WebRTC. */
class WebViewDoorbellActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var talkButton: Button
    private var talking = false
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DoorbellProcessLifecycle.activityStarted()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(createContent())
        enterImmersiveMode()
        loadStream()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        webView.onResume()
    }

    override fun onPause() {
        setTalking(false)
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
        DoorbellProcessLifecycle.activityFinished()
    }

    private fun createContent(): View = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
        webView = WebView(this@WebViewDoorbellActivity).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) = syncMicrophone()
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (
                            request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                            syncMicrophone()
                        } else {
                            request.deny()
                            status.text = "Live · microphone unavailable · WebView"
                        }
                    }
                }
            }
        }
        addView(webView, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this@WebViewDoorbellActivity).apply {
            text = "Connecting · WebView fallback"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        addView(
            status,
            FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            },
        )
        addView(
            button("×") { finish() },
            FrameLayout.LayoutParams(dp(56), dp(48)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(6)
                rightMargin = dp(6)
            },
        )
        addView(
            button("Hold to talk").apply {
                talkButton = this
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> setTalking(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setTalking(false)
                    }
                    true
                }
            },
            FrameLayout.LayoutParams(dp(180), dp(58)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            },
        )
    }

    private fun loadStream() {
        val base = if (BuildConfig.DEBUG) {
            intent.getStringExtra(DoorbellActivity.EXTRA_STREAM_BASE_URL)
        } else {
            null
        } ?: DoorbellActivity.DEFAULT_STREAM_BASE_URL
        val stream = if (BuildConfig.DEBUG) {
            intent.getStringExtra(DoorbellActivity.EXTRA_STREAM_NAME)
        } else {
            null
        } ?: DoorbellActivity.DEFAULT_STREAM_NAME
        val uri = android.net.Uri.parse(base)
        val signal = uri.buildUpon()
            .scheme(if (uri.scheme == "https") "wss" else "ws")
            .path("/api/ws")
            .clearQuery()
            .appendQueryParameter("src", stream)
            .build()
        webView.loadUrl(
            "file:///android_asset/doorbell.html?signal=${android.net.Uri.encode(signal.toString())}",
        )
    }

    private fun setTalking(enabled: Boolean) {
        talking = enabled
        if (::talkButton.isInitialized) {
            talkButton.text = if (enabled) "Release to stop" else "Hold to talk"
        }
        syncMicrophone()
    }

    private fun syncMicrophone(attempt: Int = 0) {
        if (destroyed) return
        webView.evaluateJavascript("window.setTalkEnabled?.($talking) === true") { result ->
            if (result == "true") {
                status.text = if (talking) "Live · talking · WebView" else
                    "Live · microphone muted · WebView"
            } else if (attempt < 40) {
                webView.postDelayed({ syncMicrophone(attempt + 1) }, 250)
            }
        }
    }

    private fun button(label: String, action: () -> Unit = {}): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

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
}
