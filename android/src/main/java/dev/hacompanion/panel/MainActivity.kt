package dev.hacompanion.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import dev.hacompanion.panel.ui.PanelDialogAction
import dev.hacompanion.panel.ui.PanelDialogHeader
import dev.hacompanion.panel.ui.showPanelDialog
import dev.hacompanion.panel.ui.installComposeHost
import dev.hacompanion.panel.ui.slab.AdminAction
import dev.hacompanion.panel.ui.slab.AdminScreen
import dev.hacompanion.panel.ui.slab.showPanelScreen

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType

class MainActivity : Activity() {
    private lateinit var reportView: TextView
    private lateinit var connectionDot: TextView
    private lateinit var connectionTitle: TextView
    private lateinit var connectionDetail: TextView
    private lateinit var settingsStore: SecureSettingsStore
    private lateinit var layoutStore: DashboardLayoutStore
    private var navBarMode: NavBarMode = NavBarMode.LISTENER
    private val proximityWake by lazy { ProximityWake(this) }
    private lateinit var weatherCacheStore: WeatherCacheStore
    private lateinit var dashboardView: PanelDashboardView
    private lateinit var rootView: FrameLayout
    private lateinit var healthJournal: HealthJournal
    private lateinit var deviceDiagnosticSnapshot: String
    private var onboardingView: View? = null
    private var haClient: HomeAssistantClient? = null
    private var panelApiClient: PanelApiClient? = null
    private var panelSyncClient: PanelSyncClient? = null
    private var pairingAdvertiser: PanelPairingAdvertiser? = null
    private var demoMode = false
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var connectionPhase = ConnectionPhase.NOT_CONFIGURED
    private var offlineSinceMs = 0L
    private var lastWatchdogRecoveryMs = 0L
    private var serverTimeMs = System.currentTimeMillis()
    private var serverTimezone = java.util.TimeZone.getDefault().id
    private val watchdog = object : Runnable {
        override fun run() {
            checkWatchdog()
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SecureSettingsStore(this)
        layoutStore = DashboardLayoutStore(this)
        weatherCacheStore = WeatherCacheStore(filesDir)
        healthJournal = HealthJournal(this)
        healthJournal.record("app", "Application started")
        deviceDiagnosticSnapshot = DeviceDiagnostics.createReport(this)
        applyDebugProvisioning(intent)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(createContent())
        // SPIKE: Compose resolves its recomposer's lifecycle owner from the window root.
        installComposeHost(window.decorView)
        startPairingAdvertisement()
        applyBarVisibility()
        keepBarsHidden()
        refreshReport()
        connectWithSavedSettings()
        startPanelSync()
        watchdogHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        openDebugDoorbell(intent)
    }

    override fun onResume() {
        super.onResume()
        applyBarVisibility()
        if (::dashboardView.isInitialized) dashboardView.setDashboardActive(true)
    }

    override fun onPause() {
        if (::dashboardView.isInitialized) dashboardView.setDashboardActive(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDebugProvisioning(intent)
        applyBarVisibility()
        refreshReport()
        connectWithSavedSettings()
        openDebugDoorbell(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyBarVisibility()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MICROPHONE_REQUEST) refreshReport()
    }

    override fun onDestroy() {
        proximityWake.setEnabled(false)
        haClient?.stop()
        haClient = null
        panelApiClient?.stop()
        panelApiClient = null
        panelSyncClient?.stop()
        panelSyncClient = null
        pairingAdvertiser?.stop()
        pairingAdvertiser = null
        watchdogHandler.removeCallbacks(watchdog)
        healthJournal.record("app", "Application stopped")
        super.onDestroy()
    }

    private fun createContent(): View {
        val activeLayout = layoutStore.loadOrNull()
        val credentials = PanelProvisioningStore(this).load()
        val previewUnconfigured = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_PREVIEW_UNCONFIGURED, false)
        val previewTheme = if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_PREVIEW_THEME) else null
        if (activeLayout != null) PanelTheme.apply(activeLayout.themeMode, activeLayout.themeDark)
        else if (previewTheme != null) PanelTheme.apply(previewTheme, previewTheme == "dark")
        val root = FrameLayout(this).apply {
            setBackgroundColor(PanelTheme.canvas)
        }
        rootView = root
        val dashboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PanelTheme.canvas)
            // No inset: Slab runs every band to the screen edge, and four
            // pixels of canvas around the outside reads as a frame the design
            // does not have.
        }
        connectionDot = TextView(this)
        connectionTitle = TextView(this)
        connectionDetail = TextView(this)

        reportView = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            setTextColor(FOREGROUND)
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.05f)
        }
        dashboardView = PanelDashboardView(
            this,
            { domain, service, entityId, data ->
                panelApiClient?.callService(domain, service, entityId, data)
                    ?: (haClient?.callService(domain, service, entityId, data) == true)
            },
            ::showAdminDialog,
            { schedule -> panelApiClient?.upsertSchedule(schedule) == true },
            { scheduleId -> panelApiClient?.deleteSchedule(scheduleId) == true },
        )
        val demo = DemoHarness.apply(intent, dashboardView)
        demoMode = demo
        if (demo) {
            // Nothing to connect to and nothing to keep awake: the harness has
            // already put a layout and a set of states in front of the view.
            applyKeepScreenOn(false)
        } else if (previewUnconfigured) {
            applyKeepScreenOn(false)
            val deviceId = PanelIdentityStore(this).deviceId
            dashboardView.showUnconfigured("Living Room NSPanel", deviceId)
        } else if (activeLayout != null) {
            applyKeepScreenOn(activeLayout.keepScreenOn)
            // A panel that boots before Home Assistant answers still has its
            // saved layout, and should not spend that time in the wrong mode.
            applySystemUi(activeLayout)
            applyProximityWake(activeLayout)
            dashboardView.setLayout(activeLayout)
            dashboardView.setCachedWeather(weatherCacheStore.load(activeLayout.weatherCacheMaxAgeMinutes))
        } else if (credentials != null) {
            applyKeepScreenOn(false)
            val defaultName = "NSPanel ${credentials.panelId.takeLast(4).uppercase()}"
            dashboardView.showUnconfigured(defaultName, credentials.panelId)
        }
        dashboardRoot.addView(
            dashboardView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(dashboardRoot, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        if (credentials == null && !previewUnconfigured && !demo) {
            onboardingView = createPairingOnboarding().also {
                root.addView(it, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
            }
        }
        return root
    }

    private fun createPairingOnboarding(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
            setBackgroundColor(PanelTheme.canvas)
            addView(TextView(this@MainActivity).apply {
                text = "Set up this panel"
                textSize = 28f
                setTextColor(PanelTheme.ink)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@MainActivity).apply {
                text = "Open NSPanel Companion in Home Assistant, choose Find panels, select this panel, and enter its six-digit code."
                textSize = 15f
                setTextColor(PanelTheme.muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(22))
            })
            addView(Button(this@MainActivity).apply {
                text = "Enter Home Assistant address manually"
                textSize = 16f
                isAllCaps = false
                setOnClickListener { showManualPairingUrl() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)))
            addView(TextView(this@MainActivity).apply {
                val deviceId = PanelIdentityStore(this@MainActivity).deviceId
                text = "${panelDisplayName(deviceId)}\n$deviceId"
                textSize = 11f
                setTextColor(PanelTheme.muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(22), 0, 0)
            })
        }

    private fun dismissPairingOnboarding() {
        onboardingView?.visibility = View.GONE
        onboardingView = null
    }

    private fun createConnectionCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(27, 34, 36))

            connectionDot = TextView(this@MainActivity).apply {
                text = "●"
                textSize = 18f
                setTextColor(MUTED)
                setPadding(0, 0, dp(12), 0)
            }
            addView(connectionDot)

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    connectionTitle = TextView(this@MainActivity).apply {
                        text = "Home Assistant not configured"
                        textSize = 16f
                        setTextColor(Color.WHITE)
                    }
                    connectionDetail = TextView(this@MainActivity).apply {
                        text = "Use Configure HA to add a local server"
                        textSize = 12f
                        setTextColor(MUTED)
                    }
                    addView(connectionTitle)
                    addView(connectionDetail)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun refreshReport() {
        reportView.text = buildDiagnosticReport()
    }

    private fun buildDiagnosticReport(): String = buildString {
        append(deviceDiagnosticSnapshot)
        append("\n\nHEALTH JOURNAL\n")
        append(healthJournal.report().ifBlank { "No health events recorded" })
    }.take(16_384)

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NSPanel diagnostic", reportView.text))
        Toast.makeText(this, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
    }

    private fun showAdminDialog() {
        val paired = PanelProvisioningStore(this).load()
        val manual = settingsStore.load()
        val actions = listOfNotNull(
            // What the panel is connected to, said rather than asked. A
            // paired panel already holds a token the integration minted for
            // it; offering a blank token box made it look as though the
            // connection had been forgotten.
            AdminAction(
                label = if (paired != null) "Home Assistant" else getString(R.string.configure_ha),
                detail = when {
                    paired != null -> "Paired \u00b7 ${paired.baseUrl}"
                    manual != null -> "Manual \u00b7 ${manual.baseUrl}"
                    else -> "Not connected"
                },
            ) { if (paired != null) showConnectionInfo(paired) else showConnectionDialog() },
            AdminAction(getString(R.string.request_microphone)) { requestMicrophone() },
            AdminAction(getString(R.string.open_home_settings)) { requestHomeRole() },
            AdminAction("Android settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) },
            AdminAction("Diagnostics") { showDiagnostics() },
            AdminAction("Panel identity", PanelIdentityStore(this).deviceId.take(20) + "\u2026") {
                showPanelIdentity()
            },
            AdminAction("Pair with Home Assistant") { discoverForPairing() }
                .takeIf { paired == null },
            AdminAction(getString(R.string.copy_report)) { copyReport() },
            AdminAction("Test doorbell") { showDoorbell() },
            AdminAction("Test doorbell (quiet)") { showQuietDoorbell() },
            AdminAction("Restart panel", "Relaunches the app") { restartPanel() },
            AdminAction("Exit immersive mode") { exitImmersiveMode() },
            AdminAction("Clear HA connection", destructive = true) { clearConnection() },
        )
        showPanelScreen(this, PanelTheme.isDark) { dismiss -> AdminScreen(actions, dismiss) }
    }

    /**
     * What the panel is paired to, read-only.
     *
     * The token is the integration's to issue and the keystore's to hold; it
     * is never shown, and there is nothing here for anyone to retype.
     */
    private fun showConnectionInfo(credentials: PanelCredentials) {
        AlertDialog.Builder(this)
            .setTitle("Home Assistant")
            .setMessage(
                "Paired.\n\nAddress\n${credentials.baseUrl}\n\nPanel\n${credentials.panelId}",
            )
            .setNegativeButton("Close", null)
            .setPositiveButton("Connect manually") { _, _ -> showConnectionDialog() }
            .show()
    }

    /**
     * Relaunch the app.
     *
     * A panel that has lost Home Assistant, or wedged its dashboard, is
     * otherwise fixed by walking to the wall — which is the one thing a wall
     * panel should not need. Android gives an app no way to reboot the
     * device, so this restarts the process: the alarm relaunches it a moment
     * after this one exits, because a process cannot start itself.
     */
    private fun restartPanel() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = android.app.PendingIntent.getActivity(
            this, RESTART_REQUEST, intent, android.app.PendingIntent.FLAG_CANCEL_CURRENT,
        )
        (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 400,
            pending,
        )
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private fun showPanelIdentity() {
        val deviceId = PanelIdentityStore(this).deviceId
        AlertDialog.Builder(this)
            .setTitle("Panel identity")
            .setMessage("Use this stable device ID when pairing:\n\n$deviceId")
            .setNegativeButton("Close", null)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Panel device ID", deviceId))
            }
            .show()
    }

    private fun discoverForPairing() {
        val results = linkedMapOf<String, DiscoveredHomeAssistant>()
        lateinit var discovery: HomeAssistantDiscovery
        val searching = AlertDialog.Builder(this)
            .setTitle("Find Home Assistant")
            .setMessage("Searching the local network…")
            .setNegativeButton("Cancel") { _, _ -> discovery.stop() }
            .setPositiveButton("Enter URL", null)
            .create()
        discovery = HomeAssistantDiscovery(this) { result ->
            runOnUiThread { results[result.baseUrl] = result }
        }
        searching.setOnShowListener {
            searching.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                discovery.stop()
                searching.dismiss()
                showManualPairingUrl()
            }
        }
        searching.show()
        discovery.start()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!searching.isShowing) return@postDelayed
            discovery.stop()
            searching.dismiss()
            when (results.size) {
                0 -> showManualPairingUrl("No Home Assistant instance was discovered.")
                1 -> beginPairing(results.values.single().baseUrl)
                else -> {
                    val values = results.values.toList()
                    AlertDialog.Builder(this).setTitle("Choose Home Assistant")
                        .setItems(values.map { "${it.name}\n${it.baseUrl}" }.toTypedArray()) { _, index ->
                            beginPairing(values[index].baseUrl)
                        }.setNegativeButton("Cancel", null).show()
                }
            }
        }, 4_000)
    }

    private fun showManualPairingUrl(message: String? = null) {
        val input = EditText(this).apply {
            hint = "http://homeassistant.local:8123"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(settingsStore.load()?.baseUrl.orEmpty())
        }
        AlertDialog.Builder(this).setTitle("Home Assistant address").setMessage(message)
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ -> beginPairing(input.text.toString()) }.show()
    }

    private fun beginPairing(baseUrl: String) {
        val deviceId = PanelIdentityStore(this).deviceId
        val panelName = panelDisplayName(deviceId)
        val modal = createPairingModal(panelName, deviceId)
        modal.dialog.show()
        modal.dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.7f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(
                (resources.displayMetrics.widthPixels - dp(36)).coerceAtMost(dp(620)),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
        PanelPairingClient().start(baseUrl.trim(), deviceId, panelDisplayName(deviceId)) { update ->
            if (!modal.dialog.isShowing) return@start
            when (update) {
                is PairingUpdate.Code -> {
                    modal.badge.text = "WAITING FOR HOME ASSISTANT"
                    modal.badge.setTextColor(PanelTheme.accent)
                    modal.title.text = "Enter this code in Home Assistant"
                    modal.message.text = "Select $panelName in Find panels, then enter the code shown below."
                    modal.code.text = update.value.chunked(3).joinToString(" ")
                    modal.code.visibility = View.VISIBLE
                    modal.detail.text = "Expires in ${formatPairingExpiry(update.expiresIn)}"
                    modal.detail.visibility = View.VISIBLE
                }
                is PairingUpdate.Approved -> {
                    pairingAdvertiser?.stop()
                    pairingAdvertiser = null
                    PanelProvisioningStore(this).save(update.credentials)
                    settingsStore.clear()
                    dismissPairingOnboarding()
                    startPanelSync()
                    connectWithSavedSettings()
                    modal.badge.text = "✓  PAIRED"
                    modal.badge.setTextColor(Color.rgb(32, 137, 88))
                    modal.title.text = "Panel connected"
                    modal.message.text = "$panelName is ready to receive its dashboard from Home Assistant."
                    modal.code.visibility = View.GONE
                    modal.detail.text = "Opening dashboard…"
                    modal.close.text = "Done"
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (modal.dialog.isShowing) modal.dialog.dismiss()
                    }, 1_600)
                }
                is PairingUpdate.Error -> {
                    modal.badge.text = "PAIRING FAILED"
                    modal.badge.setTextColor(Color.rgb(184, 54, 45))
                    modal.title.text = "Couldn’t connect this panel"
                    modal.message.text = update.message
                    modal.code.visibility = View.GONE
                    modal.detail.text = "Close this window and try Find panels again."
                    modal.detail.visibility = View.VISIBLE
                    modal.close.text = "Close"
                }
            }
        }
    }

    private data class PairingModal(
        val dialog: Dialog,
        val badge: TextView,
        val title: TextView,
        val message: TextView,
        val code: TextView,
        val detail: TextView,
        val close: Button,
    )

    private fun createPairingModal(panelName: String, deviceId: String): PairingModal {
        val dialog = Dialog(this)
        val badge = TextView(this).apply {
            text = "CONNECTING"
            textSize = 11f
            letterSpacing = .16f
            setTextColor(PanelTheme.accent)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val title = TextView(this).apply {
            text = "Requesting a pairing code…"
            textSize = 25f
            setTextColor(PanelTheme.ink)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        }
        val message = TextView(this).apply {
            text = "Connecting $panelName to Home Assistant."
            textSize = 15f
            setTextColor(PanelTheme.muted)
        }
        val code = TextView(this).apply {
            visibility = View.GONE
            textSize = 38f
            letterSpacing = .14f
            gravity = Gravity.CENTER
            setTextColor(PanelTheme.ink)
            typeface = android.graphics.Typeface.MONOSPACE
            background = PanelTheme.rounded(this@MainActivity, Color.WHITE, 18, PanelTheme.line, 1)
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val detail = TextView(this).apply {
            text = "$panelName  ·  ${deviceId.takeLast(12)}"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(PanelTheme.muted)
            setPadding(0, dp(10), 0, 0)
        }
        val close = Button(this).apply {
            text = "Cancel"
            textSize = 15f
            isAllCaps = false
            setTextColor(PanelTheme.ink)
            background = PanelTheme.rounded(this@MainActivity, Color.WHITE, 16, PanelTheme.line, 1)
            setOnClickListener { dialog.dismiss() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(22))
            background = PanelTheme.rounded(this@MainActivity, PanelTheme.panel, 26, Color.TRANSPARENT, 0)
            addView(badge)
            addView(title)
            addView(message)
            addView(code, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) })
            addView(detail)
            addView(close, LinearLayout.LayoutParams(dp(132), dp(52)).apply {
                gravity = Gravity.END
                topMargin = dp(18)
            })
        }
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(false)
        return PairingModal(dialog, badge, title, message, code, detail, close)
    }

    private fun formatPairingExpiry(seconds: Int): String {
        val minutes = (seconds.coerceAtLeast(1) + 59) / 60
        return if (minutes == 1) "1 minute" else "$minutes minutes"
    }

    private fun panelDisplayName(deviceId: String): String =
        "NSPanel ${deviceId.takeLast(4).uppercase()}"

    private fun startPairingAdvertisement() {
        if (PanelProvisioningStore(this).load() != null || pairingAdvertiser != null) return
        val deviceId = PanelIdentityStore(this).deviceId
        pairingAdvertiser = PanelPairingAdvertiser(this, deviceId, panelDisplayName(deviceId)) { baseUrl ->
            runOnUiThread { beginPairing(baseUrl) }
        }.also { it.start() }
    }

    private fun showDiagnostics() {
        refreshReport()
        val content = TextView(this).apply {
            text = reportView.text
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.diagnostic_title))
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showConnectionDialog() {
        val current = settingsStore.load()
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val url = EditText(this).apply {
            hint = "http://homeassistant.local:8123"
            setText(current?.baseUrl.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val token = EditText(this).apply {
            hint = if (current == null) "Long-lived access token" else "Leave blank to keep token"
            inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        fields.addView(url)
        fields.addView(token)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Home Assistant connection")
            .setMessage(
                "Development setup: enter your local HA URL and a long-lived access token. " +
                    "The token is encrypted with Android Keystore.",
            )
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val accessToken = token.text.toString().ifBlank { current?.accessToken.orEmpty() }
                val settings = ConnectionSettings(url.text.toString(), accessToken)
                try {
                    settingsStore.save(settings)
                    dialog.dismiss()
                    connectWithSavedSettings()
                } catch (error: Exception) {
                    token.error = error.message ?: "Invalid connection settings"
                }
            }
        }
        dialog.show()
    }

    private fun connectWithSavedSettings() {
        haClient?.stop()
        haClient = null
        panelApiClient?.stop()
        panelApiClient = null
        PanelProvisioningStore(this).load()?.let { credentials ->
            panelApiClient = PanelApiClient(
                credentials,
                onStatus = { status ->
                    updateConnectionStatus(status)
                    if (status.phase == ConnectionPhase.AUTH_FAILED) handlePairingRevoked()
                },
                onInitialStates = ::activateInitialStates,
                onEntityChanged = ::activateEntityState,
                onDoorbellEvent = ::showDoorbellEvent,
                onRestart = ::restartPanel,
                onWeatherForecast = dashboardView::updateWeatherForecast,
                onSchedules = dashboardView::setSchedules,
                onServerTime = { millis, timezone ->
                    serverTimeMs = millis
                    serverTimezone = timezone
                    dashboardView.synchronizeServerTime(millis, timezone)
                },
            ).also { it.start() }
            return
        }
        val settings = settingsStore.load()
        if (settings == null) {
            updateConnectionStatus(
                ConnectionStatus(
                    ConnectionPhase.NOT_CONFIGURED,
                    "Use Configure HA to add a local server",
                ),
            )
            return
        }
        haClient = HomeAssistantClient(
            settings = settings,
            onStatus = ::updateConnectionStatus,
            onInitialStates = ::activateInitialStates,
            onEntityChanged = ::activateEntityState,
            onDoorbellEvent = ::showDoorbellEvent,
            onDashboardLayout = ::activateDashboardLayout,
            onWeatherForecast = dashboardView::updateWeatherForecast,
        ).also { it.start() }
    }

    private fun activateInitialStates(values: List<EntityState>) {
        weatherCacheStore.update(values)
        dashboardView.setInitialStates(values)
    }

    private fun activateEntityState(value: EntityState) {
        if (value.domain == "weather") weatherCacheStore.update(listOf(value))
        dashboardView.updateState(value)
    }

    private fun activateDashboardLayout(layout: DashboardLayout) {
        try {
            val previous = layoutStore.loadOrNull()
            layoutStore.save(layout)
            if (previous == null || previous.themeMode != layout.themeMode || previous.themeDark != layout.themeDark) {
                recreate()
                return
            }
            PanelTheme.apply(layout.themeMode, layout.themeDark)
            rootView.setBackgroundColor(PanelTheme.canvas)
            (dashboardView.parent as? View)?.setBackgroundColor(PanelTheme.canvas)
            applyKeepScreenOn(layout.keepScreenOn)
            applySystemUi(layout)
            applyProximityWake(layout)
            dashboardView.setLayout(layout)
            if (PanelProvisioningStore(this).load() != null) connectWithSavedSettings()
            Toast.makeText(this, "Layout updated", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "Layout update failed", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Put the two Android system-UI settings into the state the layout asks
     * for.
     *
     * Both writes need WRITE_SECURE_SETTINGS, which a normal install cannot
     * request; the updater add-on grants it over ADB. Without it the settings
     * are inert, which is a panel that behaves exactly as it did before they
     * existed — so this logs and moves on rather than failing the layout.
     */
    private fun applySystemUi(layout: DashboardLayout) {
        navBarMode = layout.navBarMode
        applyBarVisibility()
        if (checkSelfPermission(SystemUiPolicy.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "System UI settings ignored: ${SystemUiPolicy.PERMISSION} not granted")
            return
        }
        try {
            Settings.Global.putString(
                contentResolver,
                SystemUiPolicy.POLICY_CONTROL,
                SystemUiPolicy.policyControlValue(navBarMode),
            )
            applyAccessibilityButton(layout.hideAccessibilityButton)
        } catch (error: SecurityException) {
            Log.w(TAG, "System UI settings refused by Android", error)
        }
    }

    /**
     * The vendor's floating back button is an accessibility service rather
     * than part of the navigation bar, so hiding it means disabling that
     * service — and restoring it means having remembered which one it was.
     */
    private fun applyAccessibilityButton(hide: Boolean) {
        val store = getSharedPreferences(SYSTEM_UI_STORE, Context.MODE_PRIVATE)
        val change = SystemUiPolicy.accessibilityChange(
            hide = hide,
            current = Settings.Secure.getString(
                contentResolver, SystemUiPolicy.ACCESSIBILITY_SERVICES,
            ).orEmpty(),
            remembered = store.getString(REMEMBERED_ACCESSIBILITY, null),
        )
        // Android maintains accessibility_enabled itself, in both
        // directions — measured on the panel, writing the list alone brings
        // a restored service back to life.
        change.write?.let {
            Settings.Secure.putString(contentResolver, SystemUiPolicy.ACCESSIBILITY_SERVICES, it)
        }
        store.edit().putString(REMEMBERED_ACCESSIBILITY, change.remember).apply()
    }

    private fun applyBarVisibility() {
        if (SystemUiPolicy.barsHidden(navBarMode)) enterImmersiveMode() else exitImmersiveMode()
    }

    /**
     * Listen for someone approaching, when asked to.
     *
     * Pointless while the display is held on — there is nothing to wake —
     * so the two settings are read together rather than the sensor running
     * to no purpose.
     */
    private fun applyProximityWake(layout: DashboardLayout) {
        proximityWake.setEnabled(layout.wakeOnApproach && !layout.keepScreenOn)
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun startPanelSync() {
        panelSyncClient?.stop()
        panelSyncClient = null
        val credentials = PanelProvisioningStore(this).load() ?: return
        panelSyncClient = PanelSyncClient(
            credentials,
            currentRevision = { layoutStore.loadOrNull()?.revision.orEmpty() },
            diagnostics = ::buildDiagnosticReport,
            onLayout = ::activateDashboardLayout,
            onPanelIdentity = { name -> dashboardView.setPanelIdentity(name, credentials.panelId) },
            onAuthenticationFailed = {
                handlePairingRevoked()
            },
            onHealth = { message ->
                if (!message.endsWith("online") || connectionPhase != ConnectionPhase.ONLINE) {
                    healthJournal.record("sync", message)
                }
            },
        ).also { it.start() }
    }

    private fun handlePairingRevoked() {
        if (PanelProvisioningStore(this).load() == null) return
        PanelProvisioningStore(this).clear()
        panelApiClient?.stop()
        panelApiClient = null
        panelSyncClient?.stop()
        panelSyncClient = null
        updateConnectionStatus(ConnectionStatus(ConnectionPhase.NOT_CONFIGURED, "Pair this panel again"))
        if (onboardingView == null && ::rootView.isInitialized) {
            onboardingView = createPairingOnboarding().also {
                rootView.addView(it, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
            }
        }
        startPairingAdvertisement()
        Toast.makeText(this, "Panel was unpaired from Home Assistant", Toast.LENGTH_LONG).show()
    }

    private fun showDoorbell() {
        startActivity(doorbellIntent())
    }

    private fun showDoorbellEvent(event: DoorbellEvent) {
        val intent = rtspDoorbellIntent()
            .putExtra(DoorbellActivity.EXTRA_QUIET_MODE, event.quietMode)
        event.streamBaseUrl?.let {
            intent.putExtra(DoorbellActivity.EXTRA_STREAM_BASE_URL, it)
        }
        event.streamName?.let {
            intent.putExtra(DoorbellActivity.EXTRA_STREAM_NAME, it)
        }
        event.talkbackUrl?.let {
            intent.putExtra(DoorbellActivity.EXTRA_TALKBACK_URL, it)
        }
        event.talkbackKey?.let {
            intent.putExtra(DoorbellActivity.EXTRA_TALKBACK_KEY, it)
        }
        event.talkbackTestUrl?.let {
            intent.putExtra(DoorbellActivity.EXTRA_TALKBACK_TEST_URL, it)
        }
        event.autoCloseMs?.let {
            intent.putExtra(DoorbellActivity.EXTRA_AUTO_CLOSE_MS, it)
        }
        intent.putExtra(DoorbellActivity.EXTRA_TALK_EXTEND_MS, event.talkExtendMs)
        startActivity(intent)
    }

    private fun showQuietDoorbell() {
        startActivity(
            doorbellIntent().putExtra(DoorbellActivity.EXTRA_QUIET_MODE, true),
        )
    }

    private fun openDebugDoorbell(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val baseUrl = intent.getStringExtra(DoorbellActivity.EXTRA_STREAM_BASE_URL) ?: return
        val streamName = intent.getStringExtra(DoorbellActivity.EXTRA_STREAM_NAME) ?: return
        intent.removeExtra(DoorbellActivity.EXTRA_STREAM_BASE_URL)
        intent.removeExtra(DoorbellActivity.EXTRA_STREAM_NAME)
        startActivity(
            rtspDoorbellIntent()
                .putExtra(DoorbellActivity.EXTRA_STREAM_BASE_URL, baseUrl)
                .putExtra(DoorbellActivity.EXTRA_STREAM_NAME, streamName)
                .putExtra(
                    DoorbellActivity.EXTRA_START_TALKING,
                    intent.getBooleanExtra(DoorbellActivity.EXTRA_START_TALKING, false),
                )
                .putExtra(
                    DoorbellActivity.EXTRA_USE_WEBVIEW,
                    intent.getBooleanExtra(DoorbellActivity.EXTRA_USE_WEBVIEW, false),
                )
                .putExtra(
                    DoorbellActivity.EXTRA_AUTO_CLOSE_MS,
                    intent.getLongExtra(DoorbellActivity.EXTRA_AUTO_CLOSE_MS, 60_000L),
                )
                .putExtra(
                    DoorbellActivity.EXTRA_TALK_EXTEND_MS,
                    intent.getLongExtra(DoorbellActivity.EXTRA_TALK_EXTEND_MS, 15_000L),
                )
                .putExtra(
                    DoorbellActivity.EXTRA_QUIET_MODE,
                    intent.getBooleanExtra(DoorbellActivity.EXTRA_QUIET_MODE, false),
                ),
        )
    }

    private fun doorbellIntent(): Intent =
        Intent(this, DoorbellActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )

    private fun rtspDoorbellIntent(): Intent =
        Intent(this, RtspDoorbellActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ).also { intent ->
            val layout = layoutStore.loadOrNull() ?: DashboardLayout.default()
            intent.putExtra(RtspDoorbellActivity.EXTRA_SHOW_CLOCK, layout.showClock)
            intent.putExtra(RtspDoorbellActivity.EXTRA_SHOW_MIC_INDICATOR, layout.showMicIndicator)
            intent.putExtra(RtspDoorbellActivity.EXTRA_MIC_LINGER_SECONDS, layout.micIndicatorLingerSeconds)
            intent.putExtra(RtspDoorbellActivity.EXTRA_SERVER_TIME_MS, serverTimeMs)
            intent.putExtra(RtspDoorbellActivity.EXTRA_SERVER_TIMEZONE, serverTimezone)
        }

    /**
     * Lets developers configure a sideloaded debug APK over ADB without typing a
     * token on the 480 px panel. Release builds ignore these extras completely.
     * The values are immediately moved into Android Keystore encrypted storage.
     */
    private fun applyDebugProvisioning(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val url = intent.getStringExtra(EXTRA_HA_URL) ?: return
        val token = intent.getStringExtra(EXTRA_HA_TOKEN) ?: return
        try {
            settingsStore.save(ConnectionSettings(url, token))
            intent.removeExtra(EXTRA_HA_URL)
            intent.removeExtra(EXTRA_HA_TOKEN)
        } catch (_: Exception) {
            // The normal Configure HA form remains available for invalid values.
        }
    }

    private fun clearConnection() {
        haClient?.stop()
        haClient = null
        settingsStore.clear()
        updateConnectionStatus(
            ConnectionStatus(ConnectionPhase.NOT_CONFIGURED, "Connection settings cleared"),
        )
    }

    private fun updateConnectionStatus(status: ConnectionStatus) {
        if (status.phase != connectionPhase) {
            healthJournal.record("connection", "${connectionPhase.name} -> ${status.phase.name}: ${status.detail}")
            connectionPhase = status.phase
            if (status.phase == ConnectionPhase.ONLINE || status.phase == ConnectionPhase.NOT_CONFIGURED) {
                offlineSinceMs = 0L
            } else if (offlineSinceMs == 0L) {
                offlineSinceMs = android.os.SystemClock.elapsedRealtime()
            }
        }
        // The harness has no connection to report on, and letting a failed
        // one mark the demo offline greys out every control on it.
        if (::dashboardView.isInitialized && !demoMode) {
            dashboardView.setOnline(status.phase == ConnectionPhase.ONLINE)
        }
        connectionTitle.text = when (status.phase) {
            ConnectionPhase.NOT_CONFIGURED -> "Not configured"
            ConnectionPhase.CONNECTING -> "Connecting"
            ConnectionPhase.AUTHENTICATING -> "Authenticating"
            ConnectionPhase.ONLINE -> "Online"
            ConnectionPhase.RETRYING -> "Offline"
            ConnectionPhase.AUTH_FAILED -> "Auth failed"
            ConnectionPhase.STOPPED -> "Stopped"
        }
        connectionDetail.text = status.detail
        connectionDot.setTextColor(
            when (status.phase) {
                ConnectionPhase.ONLINE -> PanelTheme.accent
                ConnectionPhase.AUTH_FAILED -> Color.rgb(239, 111, 108)
                ConnectionPhase.CONNECTING,
                ConnectionPhase.AUTHENTICATING,
                ConnectionPhase.RETRYING,
                -> Color.rgb(245, 190, 86)
                else -> PanelTheme.muted
            },
        )
    }

    private fun checkWatchdog() {
        if (PanelProvisioningStore(this).load() == null || connectionPhase == ConnectionPhase.ONLINE) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (offlineSinceMs == 0L) offlineSinceMs = now
        if (now - offlineSinceMs < WATCHDOG_OFFLINE_MS || now - lastWatchdogRecoveryMs < WATCHDOG_COOLDOWN_MS) return
        lastWatchdogRecoveryMs = now
        healthJournal.record("watchdog", "Rebuilding HA clients after prolonged ${connectionPhase.name}")
        connectWithSavedSettings()
        startPanelSync()
    }

    private fun requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
        } else {
            Toast.makeText(this, "Microphone permission is already granted", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                    HOME_ROLE_REQUEST,
                )
                return
            }
        }

        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    @Suppress("DEPRECATION")
    /**
     * Sticky immersive hands the bars back on anything the platform reads as
     * an edge gesture, and on this hardware a long press is enough. Nothing
     * in the app wants them, so they go away again as soon as they appear.
     */
    private fun keepBarsHidden() {
        window.decorView.setOnSystemUiVisibilityChangeListener { flags ->
            if (SystemUiPolicy.usesListener(navBarMode) &&
                flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
            ) {
                window.decorView.postDelayed({ enterImmersiveMode() }, 1_200)
            }
        }
    }

    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    @Suppress("DEPRECATION")
    private fun exitImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars(),
            )
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "NSPanelMain"
        private const val SYSTEM_UI_STORE = "panel_system_ui"
        private const val REMEMBERED_ACCESSIBILITY = "remembered_accessibility_services"
        private const val EXTRA_PREVIEW_UNCONFIGURED = "dev.hacompanion.panel.PREVIEW_UNCONFIGURED"
        private const val EXTRA_PREVIEW_THEME = "dev.hacompanion.panel.PREVIEW_THEME"
        internal const val MICROPHONE_REQUEST = 10
        private const val RESTART_REQUEST = 91
        private const val HOME_ROLE_REQUEST = 11
        private const val EXTRA_HA_URL = "dev.hacompanion.panel.HA_URL"
        private const val EXTRA_HA_TOKEN = "dev.hacompanion.panel.HA_TOKEN"
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val WATCHDOG_OFFLINE_MS = 120_000L
        private const val WATCHDOG_COOLDOWN_MS = 300_000L
        private val BACKGROUND = Color.rgb(16, 20, 22)
        private val FOREGROUND = Color.rgb(225, 232, 230)
        private val MUTED = Color.rgb(145, 157, 154)
        private val ACCENT = Color.rgb(101, 214, 173)
    }
}
