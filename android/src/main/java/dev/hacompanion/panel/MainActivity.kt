package dev.hacompanion.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import dev.hacompanion.panel.ui.PanelDialogAction
import dev.hacompanion.panel.ui.PanelDialogHeader
import dev.hacompanion.panel.ui.showPanelDialog
import dev.hacompanion.panel.ui.installComposeHost
import dev.hacompanion.panel.ui.model.CallPhase
import dev.hacompanion.panel.ui.slab.PairingScreenState
import dev.hacompanion.panel.ui.slab.pairingOnboardingView
import dev.hacompanion.panel.ui.slab.showPairingScreen
import dev.hacompanion.panel.ui.slab.AdminAction
import dev.hacompanion.panel.ui.slab.AdminScreen
import dev.hacompanion.panel.ui.slab.EntryField
import dev.hacompanion.panel.ui.slab.ScreenAction
import dev.hacompanion.panel.ui.slab.showEntryScreen
import dev.hacompanion.panel.ui.slab.showNoticeScreen
import dev.hacompanion.panel.ui.slab.showReportScreen
import dev.hacompanion.panel.ui.slab.showPanelScreen

import android.Manifest
import android.app.Activity
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var reportView: TextView
    private lateinit var connectionDot: TextView
    private lateinit var connectionTitle: TextView
    private lateinit var connectionDetail: TextView
    private lateinit var settingsStore: SecureSettingsStore
    private lateinit var layoutStore: DashboardLayoutStore
    private var navBarMode: NavBarMode = NavBarMode.LISTENER
    private val proximityWake by lazy { ProximityWake(this) }

    /** The call in progress: its session, its id, and how long it has run. */
    private var intercomSession: IntercomSession? = null
    private var intercomCallId: String? = null
    private var intercomStartedAt = 0L
    private val intercomTick = object : Runnable {
        override fun run() {
            if (intercomSession == null) return
            dashboardView.setCallSeconds(
                ((android.os.SystemClock.elapsedRealtime() - intercomStartedAt) / 1000).toInt(),
            )
            watchdogHandler.postDelayed(this, 1_000)
        }
    }

    /**
     * Watches the accessibility setting and puts it back.
     *
     * The vendor's launcher re-enables its own back button whenever it runs,
     * and on this panel it runs whenever our app is not Home — so setting
     * the value once at startup loses a race nobody can win by being early.
     * Re-asserting is idempotent: with nothing of the vendor's enabled there
     * is nothing to write.
     */
    private var accessibilityGuard: android.database.ContentObserver? = null
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
        // One panel, one dashboard.
        //
        // singleTask is supposed to guarantee that, and does — within one
        // task. It says nothing about two tasks, and the panel produced
        // exactly that: two live MainActivity instances in the same process,
        // both started by the vendor launcher, each with its own socket to
        // Home Assistant and its own idea of what is on screen. Restarting
        // the app races the launcher relaunching us as Home, and a task
        // still finishing does not absorb the launch that arrives during it.
        //
        // The newest launch is the one someone asked for, so it wins and the
        // older instance is retired.
        live?.takeIf { it !== this && !it.isFinishing }?.let { stale ->
            Log.w("PanelActivity", "A second dashboard was started; retiring the first")
            stale.finish()
        }
        live = this
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
        if (live === this) live = null
        proximityWake.setEnabled(false)
        watchAccessibilityButton(false)
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
            { entityId, range -> panelApiClient?.requestHistory(entityId, range) },
            ::runIntercom,
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

    private fun createPairingOnboarding(): View {
        val deviceId = PanelIdentityStore(this).deviceId
        return pairingOnboardingView(
            this, PanelTheme.isDark, panelDisplayName(deviceId), deviceId,
        ) { showManualPairingUrl() }
    }


    private fun dismissPairingOnboarding() {
        onboardingView?.visibility = View.GONE
        onboardingView = null
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
                closes = false,
            ) { if (paired != null) showConnectionInfo(paired) else showConnectionDialog() },
            AdminAction(getString(R.string.request_microphone)) { requestMicrophone() },
            AdminAction(getString(R.string.open_home_settings)) { requestHomeRole() },
            AdminAction("Android settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) },
            AdminAction("Diagnostics", closes = false) { showDiagnostics() },
            AdminAction(
                "Panel identity",
                PanelIdentityStore(this).deviceId.take(20) + "\u2026",
                closes = false,
            ) { showPanelIdentity() },
            AdminAction("Pair with Home Assistant", closes = false) { discoverForPairing() }
                .takeIf { paired == null },
            AdminAction(getString(R.string.copy_report)) { copyReport() },
            AdminAction("Test doorbell") { showDoorbell() },
            AdminAction("Test doorbell (quiet)") { showQuietDoorbell() },
            AdminAction("Restart panel", "Relaunches the app") { restartPanel() },
            AdminAction("Exit immersive mode") { exitImmersiveMode() },
            AdminAction("Clear HA connection", destructive = true) { clearConnection() },
        )
        Log.i("PanelAdmin", "admin menu opened", Throwable("opened here"))
        showPanelScreen(this, PanelTheme.isDark) { dismiss -> AdminScreen(actions, dismiss) }
    }

    /**
     * What the panel is paired to, read-only.
     *
     * The token is the integration's to issue and the keystore's to hold; it
     * is never shown, and there is nothing here for anyone to retype.
     */
    private fun showConnectionInfo(credentials: PanelCredentials) {
        showNoticeScreen(
            this, PanelTheme.isDark,
            badge = "PAIRED",
            title = "Home Assistant",
            message = "This panel is paired and holds its own token.",
            detail = "${credentials.baseUrl}\n${credentials.panelId}",
            actions = listOf(
                ScreenAction("CLOSE"),
                ScreenAction("CONNECT MANUALLY", closes = false) { showConnectionDialog() },
            ),
        )
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
        showNoticeScreen(
            this, PanelTheme.isDark,
            badge = "PANEL IDENTITY",
            title = "This panel's device ID",
            message = "It stays the same across reinstalls. Use it when pairing.",
            detail = deviceId,
            actions = listOf(
                ScreenAction("CLOSE"),
                ScreenAction("COPY") {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Panel device ID", deviceId))
                },
            ),
        )
    }

    private fun discoverForPairing() {
        val results = linkedMapOf<String, DiscoveredHomeAssistant>()
        lateinit var discovery: HomeAssistantDiscovery
        discovery = HomeAssistantDiscovery(this) { result ->
            runOnUiThread { results[result.baseUrl] = result }
        }
        val searching = showNoticeScreen(
            this, PanelTheme.isDark,
            badge = "SEARCHING",
            title = "Find Home Assistant",
            message = "Looking for Home Assistant on this network.",
            actions = listOf(
                ScreenAction("CANCEL") { discovery.stop() },
                ScreenAction("ENTER ADDRESS") {
                    discovery.stop()
                    showManualPairingUrl()
                },
            ),
        )
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
                    // More than one answered, so the address is the thing
                    // that tells them apart and belongs under each name.
                    showPanelScreen(this, PanelTheme.isDark) { dismiss ->
                        AdminScreen(
                            values.map { found ->
                                AdminAction(found.name, found.baseUrl) {
                                    beginPairing(found.baseUrl)
                                }
                            },
                            dismiss,
                            title = "Choose Home Assistant",
                            closeLabel = "CANCEL",
                        )
                    }
                }
            }
        }, 4_000)
    }

    private fun showManualPairingUrl(message: String? = null) {
        showEntryScreen(
            this, PanelTheme.isDark,
            title = "Home Assistant address",
            message = message ?: "",
            fields = listOf(
                EntryField(
                    "ADDRESS",
                    hint = "http://homeassistant.local:8123",
                    initial = settingsStore.load()?.baseUrl.orEmpty(),
                ),
            ),
            submitLabel = "CONTINUE",
        ) { values ->
            val address = values.first().trim()
            if (address.isBlank()) "Enter an address to continue." else {
                beginPairing(address)
                null
            }
        }
    }

    private fun beginPairing(baseUrl: String) {
        val deviceId = PanelIdentityStore(this).deviceId
        val panelName = panelDisplayName(deviceId)
        val state = PairingScreenState().apply {
            message = "Connecting $panelName to Home Assistant."
            detail = "$panelName · ${deviceId.takeLast(12)}"
        }
        var open = true
        val screen = showPairingScreen(this, PanelTheme.isDark, state) { open = false }
        screen.setOnDismissListener { open = false }

        PanelPairingClient().start(baseUrl.trim(), deviceId, panelName) { update ->
            if (!open) return@start
            when (update) {
                is PairingUpdate.Code -> {
                    state.badge = "WAITING FOR HOME ASSISTANT"
                    state.title = "Enter this code in Home Assistant"
                    state.message = "Select $panelName in Find panels, then enter the code below."
                    state.code = update.value
                    state.detail = "Expires in ${formatPairingExpiry(update.expiresIn)}"
                }
                is PairingUpdate.Approved -> {
                    pairingAdvertiser?.stop()
                    pairingAdvertiser = null
                    PanelProvisioningStore(this).save(update.credentials)
                    settingsStore.clear()
                    dismissPairingOnboarding()
                    startPanelSync()
                    connectWithSavedSettings()
                    state.badge = "PAIRED"
                    state.settled = true
                    state.title = "Panel connected"
                    state.message = "$panelName is ready for its dashboard."
                    state.code = null
                    state.detail = "Opening dashboard…"
                    state.closeLabel = "DONE"
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (screen.isShowing) screen.dismiss()
                    }, 1_600)
                }
                is PairingUpdate.Error -> {
                    state.badge = "PAIRING FAILED"
                    state.failed = true
                    state.title = "Couldn't connect this panel"
                    state.message = update.message
                    state.code = null
                    state.detail = "Close this and try Find panels again."
                    state.closeLabel = "CLOSE"
                }
            }
        }
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
        val report = reportView.text.toString()
        showReportScreen(
            this, PanelTheme.isDark,
            title = getString(R.string.diagnostic_title),
            report = report,
            actions = listOf(
                ScreenAction("CLOSE"),
                ScreenAction("COPY") {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("NSPanel diagnostic", report))
                },
            ),
        )
    }

    private fun showConnectionDialog() {
        val current = settingsStore.load()
        showEntryScreen(
            this, PanelTheme.isDark,
            title = "Home Assistant connection",
            message = "The token is encrypted with Android Keystore.",
            fields = listOf(
                EntryField(
                    "ADDRESS",
                    hint = "http://homeassistant.local:8123",
                    initial = current?.baseUrl.orEmpty(),
                ),
                EntryField(
                    "TOKEN",
                    hint = if (current == null) "Long-lived access token"
                    else "Leave blank to keep the current token",
                    secret = true,
                ),
            ),
            submitLabel = "SAVE",
        ) { values ->
            val accessToken = values[1].ifBlank { current?.accessToken.orEmpty() }
            try {
                settingsStore.save(ConnectionSettings(values[0], accessToken))
                connectWithSavedSettings()
                null
            } catch (error: Exception) {
                error.message ?: "Invalid connection settings"
            }
        }
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
                onRevoked = ::handlePairingRevoked,
                onRoster = dashboardView::setRoster,
                onRing = { callId, name ->
                    intercomCallId = callId
                    dashboardView.setCall(CallPhase.RINGING, peer = name)
                },
                onCalling = { callId -> intercomCallId = callId },
                onCallAnswered = {
                    // They picked up, so we make the offer: the caller
                    // offers, which keeps one side of the negotiation
                    // definite rather than both racing to start it.
                    openIntercomSession().call()
                },
                onCallSignal = { _, signal ->
                    val session = intercomSession
                    val sdp = org.json.JSONObject(signal).optString("sdp")
                    if (session != null) {
                        session.receive(signal)
                    } else if (sdp.isNotBlank()) {
                        // No session yet, so this is the caller's offer. It
                        // is accepted the moment both it and the answer are
                        // in hand, in whichever order they arrived.
                        intercomHandshake.offered(sdp)?.let { openIntercomSession().accept(it) }
                    }
                },
                onCallEnded = ::closeIntercom,
                onCallBusy = ::closeIntercom,
                onHistory = dashboardView::setHistory,
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
            watchAccessibilityButton(layout.hideAccessibilityButton)
        } catch (error: SecurityException) {
            Log.w(TAG, "System UI settings refused by Android", error)
        }
    }

    /**
     * The vendor's floating back button is an accessibility service rather
     * than part of the navigation bar, so hiding it means disabling that
     * service — and restoring it means having remembered which one it was.
     */
    private fun watchAccessibilityButton(hide: Boolean) {
        val watching = accessibilityGuard != null
        if (hide == watching) return
        if (!hide) {
            accessibilityGuard?.let { contentResolver.unregisterContentObserver(it) }
            accessibilityGuard = null
            return
        }
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = applyAccessibilityButton(true)
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(SystemUiPolicy.ACCESSIBILITY_SERVICES), false, observer,
        )
        accessibilityGuard = observer
    }

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
        proximityWake.setEnabled(
            layout.wakeOnApproach && !layout.keepScreenOn,
            layout.wakeSensitivity,
        )
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

    /**
     * Everything a call needs, in the one place the session and the socket
     * already live together.
     *
     * The page holds neither, so it passes intent and this decides what
     * that means.
     */
    private fun runIntercom(command: IntercomCommand) {
        when (command) {
            is IntercomCommand.Call -> {
                openIntercomSession()
                panelApiClient?.callPanel(command.panelId)
            }
            is IntercomCommand.Answer -> {
                val callId = intercomCallId ?: return
                panelApiClient?.answerCall(callId)
                // Say so on the screen now. The offer still has to cross
                // Home Assistant and be negotiated, and a button that stays
                // lit meanwhile reads as one that was not pressed.
                dashboardView.setCall(CallPhase.CONNECTING)
                intercomHandshake.answered()?.let { openIntercomSession().accept(it) }
            }
            is IntercomCommand.Decline -> {
                intercomCallId?.let { panelApiClient?.declineCall(it) }
                closeIntercom()
            }
            is IntercomCommand.Mute -> intercomSession?.setMuted(command.muted)
            is IntercomCommand.End -> {
                intercomCallId?.let { panelApiClient?.endCall(it) }
                closeIntercom()
            }
        }
    }

    private val intercomHandshake = IntercomHandshake()

    private fun openIntercomSession(): IntercomSession {
        intercomSession?.let { return it }
        val session = IntercomSession(
            this,
            onSignal = { signal -> intercomCallId?.let { panelApiClient?.sendCallSignal(it, signal) } },
            onPhase = { phase ->
                dashboardView.setCall(phase)
                if (phase == CallPhase.CONNECTED) {
                    // Once per call, not once per recovery: a connection
                    // that blips and comes back is the same conversation,
                    // and a timer that restarted would say otherwise.
                    if (intercomStartedAt == 0L) {
                        intercomStartedAt = android.os.SystemClock.elapsedRealtime()
                    }
                    watchdogHandler.post(intercomTick)
                }
                if (phase == CallPhase.IDLE) closeIntercom()
            },
            onLevel = dashboardView::setCallLevel,
        )
        intercomSession = session
        MicUsageTracker.setActive(this, true)
        return session
    }

    private fun closeIntercom() {
        watchdogHandler.removeCallbacks(intercomTick)
        intercomSession?.stop()
        intercomSession = null
        intercomCallId = null
        intercomStartedAt = 0L
        intercomHandshake.reset()
        MicUsageTracker.setActive(this, false)
        dashboardView.setCall(CallPhase.IDLE, peer = "")
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

    private fun showDoorbell(quiet: Boolean = false) {
        val camera = layoutStore.loadOrNull()?.pages
            ?.flatMap { it.widgets }
            ?.firstOrNull { it.type == "camera" && !it.streamBaseUrl.isNullOrBlank() }
        val intent = rtspDoorbellIntent()
            .putExtra(DoorbellIntent.EXTRA_QUIET_MODE, quiet)
            // A test ring should not wander off on a timer while it is being
            // looked at; a real one still closes itself.
            .putExtra(DoorbellIntent.EXTRA_AUTO_CLOSE_MS, 0L)
        camera?.streamBaseUrl?.let { intent.putExtra(DoorbellIntent.EXTRA_STREAM_BASE_URL, it) }
        camera?.streamName?.let { intent.putExtra(DoorbellIntent.EXTRA_STREAM_NAME, it) }
        camera?.talkbackUrl?.let { intent.putExtra(DoorbellIntent.EXTRA_TALKBACK_URL, it) }
        camera?.talkbackKey?.let { intent.putExtra(DoorbellIntent.EXTRA_TALKBACK_KEY, it) }
        startActivity(intent)
    }

    private fun showDoorbellEvent(event: DoorbellEvent) {
        val intent = rtspDoorbellIntent()
            .putExtra(DoorbellIntent.EXTRA_QUIET_MODE, event.quietMode)
        event.streamBaseUrl?.let {
            intent.putExtra(DoorbellIntent.EXTRA_STREAM_BASE_URL, it)
        }
        event.streamName?.let {
            intent.putExtra(DoorbellIntent.EXTRA_STREAM_NAME, it)
        }
        event.talkbackUrl?.let {
            intent.putExtra(DoorbellIntent.EXTRA_TALKBACK_URL, it)
        }
        event.talkbackKey?.let {
            intent.putExtra(DoorbellIntent.EXTRA_TALKBACK_KEY, it)
        }
        event.talkbackTestUrl?.let {
            intent.putExtra(DoorbellIntent.EXTRA_TALKBACK_TEST_URL, it)
        }
        event.autoCloseMs?.let {
            intent.putExtra(DoorbellIntent.EXTRA_AUTO_CLOSE_MS, it)
        }
        intent.putExtra(DoorbellIntent.EXTRA_TALK_EXTEND_MS, event.talkExtendMs)
        startActivity(intent)
    }

    private fun showQuietDoorbell() = showDoorbell(quiet = true)

    private fun openDebugDoorbell(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val baseUrl = intent.getStringExtra(DoorbellIntent.EXTRA_STREAM_BASE_URL) ?: return
        val streamName = intent.getStringExtra(DoorbellIntent.EXTRA_STREAM_NAME) ?: return
        intent.removeExtra(DoorbellIntent.EXTRA_STREAM_BASE_URL)
        intent.removeExtra(DoorbellIntent.EXTRA_STREAM_NAME)
        startActivity(
            rtspDoorbellIntent()
                .putExtra(DoorbellIntent.EXTRA_STREAM_BASE_URL, baseUrl)
                .putExtra(DoorbellIntent.EXTRA_STREAM_NAME, streamName)
                .putExtra(
                    DoorbellIntent.EXTRA_START_TALKING,
                    intent.getBooleanExtra(DoorbellIntent.EXTRA_START_TALKING, false),
                )
                .putExtra(
                    DoorbellIntent.EXTRA_AUTO_CLOSE_MS,
                    intent.getLongExtra(DoorbellIntent.EXTRA_AUTO_CLOSE_MS, 60_000L),
                )
                .putExtra(
                    DoorbellIntent.EXTRA_TALK_EXTEND_MS,
                    intent.getLongExtra(DoorbellIntent.EXTRA_TALK_EXTEND_MS, 15_000L),
                )
                .putExtra(
                    DoorbellIntent.EXTRA_QUIET_MODE,
                    intent.getBooleanExtra(DoorbellIntent.EXTRA_QUIET_MODE, false),
                ),
        )
    }

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

        /**
         * The dashboard currently alive, so a second one can retire it.
         *
         * A plain reference rather than anything weaker: it is cleared in
         * onDestroy, and an Activity that outlives its own onDestroy is a
         * bigger problem than this leak would be.
         */
        private var live: MainActivity? = null
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
