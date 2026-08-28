package dev.hacompanion.panel

import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.ui.PanelDialogAction
import dev.hacompanion.panel.ui.PanelDialogButton
import dev.hacompanion.panel.ui.PanelDialogChoices
import dev.hacompanion.panel.ui.PanelDialogHeader
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.showPanelDialog
import dev.hacompanion.panel.ui.theme.LocalPanelRadius
import dev.hacompanion.panel.ui.theme.LocalPanelSize
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.DashboardActions
import dev.hacompanion.panel.ui.DashboardRoot
import dev.hacompanion.panel.ui.DashboardUiState
import dev.hacompanion.panel.ui.state.DashboardState
import dev.hacompanion.panel.ui.model.deviceTypeLabel
import kotlin.math.absoluteValue

import android.content.Context
import android.util.Log
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONArray
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class PanelDashboardView(
    context: Context,
    private val callService: (
        domain: String,
        service: String,
        entityId: String,
        data: JSONObject,
    ) -> Boolean,
    private val openAdmin: () -> Unit = {},
    private val openCamera: (DashboardWidget) -> Unit = {},
    private val upsertSchedule: (ControlSchedule) -> Boolean = { false },
    private val deleteSchedule: (String) -> Boolean = { false },
) : LinearLayout(context) {
    // Snapshot-backed so Compose pages recompose from it directly. The pages
    // still built as views keep using the binding registry below.
    private val dashboardState = DashboardState()
    private val states get() = dashboardState.entities
    private val weatherUpdatedAt = mutableMapOf<String, Long>()
    private val pageHost = FrameLayout(context)
    private val ui = DashboardUiState()
    private val streamWarmer = CameraStreamWarmer()
    private var warmedForPage = -1
    private val composeRoot = ComposeView(context)
    private var layout = DashboardLayout.default()
    private var configured = true
    private var panelName = "NSPanel Pro"
    private var panelId = ""
    private var pageIndex = 0
    private var online = false
    private var renderPending = false
    private var gestureStartX = 0f
    private var interactionActive = false
    private var dashboardActive = true
    private var fullRenderCount = 0
    private val timerMinutes = mutableMapOf<String, Int>()
    private val timerCallbacks = mutableMapOf<String, Runnable>()
    private val selectedClimateTarget = mutableMapOf<String, String>()
    private var schedules = emptyList<ControlSchedule>()
    private val returnToDefault = Runnable {
        if (!interactionActive && dashboardActive) {
            val defaultIndex = layout.pages.indexOfFirst { it.id == layout.defaultPageId }.coerceAtLeast(0)
            if (pageIndex != defaultIndex) setPage(defaultIndex)
        }
    }

    /**
     * What the Compose pages call back into. Declared before init, because
     * properties initialise in declaration order and init installs the root
     * that reads this.
     */
    private val dashboardActions = object : DashboardActions {
        override fun openAdmin() = this@PanelDashboardView.openAdmin()

        override fun openCamera(widget: DashboardWidget) =
            this@PanelDashboardView.openCamera(widget)

        override fun claimWarmedStream(widget: DashboardWidget): String? =
            streamWarmer.claim(widget)

        override fun selectedClimateTarget(entityId: String): String =
            selectedClimateTarget.getOrPut(entityId) {
                if (states[entityId]?.state == "cool") "cool" else "heat"
            }

        override fun selectClimateTarget(entityId: String, target: String) {
            selectedClimateTarget[entityId] = target
            ui.sidecarRevision += 1
        }

        override fun stepThermostat(entityId: String, up: Boolean) {
            states[entityId]?.let { this@PanelDashboardView.stepThermostat(it, up) }
        }

        override fun setHvacMode(entityId: String, mode: String) {
            callService("climate", "set_hvac_mode", entityId, JSONObject().put("hvac_mode", mode))
        }

        override fun toggle(entityId: String) {
            states[entityId]?.let(::toggleEntity)
        }

        override fun setBrightness(entityId: String, percent: Int) {
            callService("light", "turn_on", entityId, JSONObject().put("brightness_pct", percent))
        }

        override fun openFanSpeed(entityId: String) {
            states[entityId]?.let(::showFanSpeedDialog)
        }

        override fun openCover(entityId: String) {
            states[entityId]?.let { showCoverDialog(it, widgetFor(entityId)) }
        }

        override fun openSchedule(entityId: String) {
            states[entityId]?.let { showScheduleListDialog(it, widgetFor(entityId)) }
        }

        override fun openTimer(entityId: String) {
            states[entityId]?.let {
                showTimerDialog(it, widgetFor(entityId)?.timerPresets ?: listOf(5, 15, 30, 60))
            }
        }

        override fun timerMinutes(entityId: String): Int? =
            timerMinutes[entityId]?.takeIf { it > 0 }

        override fun scheduleCount(entityId: String): Int =
            schedules.count { it.entityId == entityId }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(BACKGROUND)
        setPadding(dp(4), dp(4), dp(4), 0)
        composeRoot.setContent { DashboardRoot(ui, dashboardState.entities, dashboardActions) }
        pageHost.addView(
            composeRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            pageHost,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )
        scheduleRender()
    }

    fun setInitialStates(values: List<EntityState>) {
        states.clear()
        values.associateByTo(states, EntityState::entityId)
        val observedAt = System.currentTimeMillis()
        values.filter { it.domain == "weather" }.forEach { weatherUpdatedAt[it.entityId] = observedAt }
        scheduleRender()
    }

    fun setCachedWeather(values: List<CachedWeather>) {
        values.forEach {
            states[it.state.entityId] = it.state
            weatherUpdatedAt[it.state.entityId] = it.updatedAtMillis
        }
        if (values.isNotEmpty()) scheduleRender()
    }

    fun updateState(value: EntityState) {
        states[value.entityId] = value
        if (value.domain == "weather") weatherUpdatedAt[value.entityId] = System.currentTimeMillis()
    }

    fun updateWeatherForecast(entityId: String, forecastType: String, forecast: JSONArray) {
        val existing = states[entityId] ?: return
        val attributes = JSONObject(existing.attributes.toString())
        attributes.put(if (forecastType == "hourly") "hourly_forecast" else "forecast", forecast)
        val updated = existing.copy(attributes = attributes)
        states[entityId] = updated
        weatherUpdatedAt[entityId] = System.currentTimeMillis()
    }

    fun setSchedules(values: List<ControlSchedule>) {
        schedules = values
        scheduleRender()
    }

    fun setLayout(value: DashboardLayout) {
        removeCallbacks(returnToDefault)
        PanelTheme.apply(value.themeMode, value.themeDark)
        setBackgroundColor(PanelTheme.canvas)
        configured = true
        layout = value
        pageIndex = value.pages.indexOfFirst { it.id == value.defaultPageId }.coerceAtLeast(0)
        warmNeighbouringCameras()
        scheduleRender()
    }

    fun synchronizeServerTime(serverTimeMs: Long, serverTimezone: String) {
        if (serverTimeMs <= 0) return
        ui.serverTimeMs = serverTimeMs
        ui.syncedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        ui.timezone = java.util.TimeZone.getTimeZone(serverTimezone)
    }

    fun showUnconfigured(name: String, deviceId: String) {
        removeCallbacks(returnToDefault)
        configured = false
        panelName = name
        panelId = deviceId
        pageIndex = 0
        scheduleRender()
    }

    fun setPanelIdentity(name: String, deviceId: String) {
        panelName = name
        panelId = deviceId
        if (!configured) scheduleRender()
    }

    fun setOnline(value: Boolean) {
        if (online == value) return
        online = value
        scheduleRender()
    }

    fun setPage(index: Int) {
        if (!configured) return
        pageIndex = index.coerceIn(0, layout.pages.lastIndex)
        warmNeighbouringCameras()
        scheduleRender()
        scheduleDefaultPageReturn()
    }

    fun setDashboardActive(value: Boolean) {
        dashboardActive = value
        if (value) scheduleDefaultPageReturn() else removeCallbacks(returnToDefault)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                interactionActive = true
                removeCallbacks(returnToDefault)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                interactionActive = false
                scheduleDefaultPageReturn()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> gestureStartX = event.x
            MotionEvent.ACTION_MOVE ->
                if (kotlin.math.abs(event.x - gestureStartX) > dp(48)) return true
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val distance = event.x - gestureStartX
            when {
                distance < -dp(48) -> setPage(pageIndex + 1)
                distance > dp(48) -> setPage(pageIndex - 1)
            }
        }
        return true
    }

    /**
     * Ask the bridge for the stream URLs of any camera one swipe away.
     *
     * Driven by an actual change of page rather than by render or by every
     * setPage call. Swiping at either end of the layout, and the return to the
     * default page, both re-select the page already shown; warming on those
     * had the panel mint a session it never played every fifteen seconds.
     */
    private fun warmNeighbouringCameras() {
        if (!configured || pageIndex == warmedForPage) return
        warmedForPage = pageIndex
        streamWarmer.warm(camerasToWarm(layout.pages, pageIndex))
    }

    private fun scheduleRender() {
        if (renderPending) return
        renderPending = true
        postDelayed({
            renderPending = false
            render()
        }, 120)
    }

    private fun scheduleDefaultPageReturn() {
        removeCallbacks(returnToDefault)
        if (!configured || !dashboardActive || interactionActive || layout.defaultPageReturnSeconds == 0) return
        val defaultIndex = layout.pages.indexOfFirst { it.id == layout.defaultPageId }.coerceAtLeast(0)
        if (pageIndex == defaultIndex) return
        postDelayed(returnToDefault, layout.defaultPageReturnSeconds * 1_000L)
    }

    /**
     * Publishes the current inputs. Nothing is inflated here any more: the
     * Compose root recomposes from the state it reads.
     */
    private fun render() {
        fullRenderCount += 1
        if (BuildConfig.DEBUG) Log.d(RENDER_LOG_TAG, "full=$fullRenderCount")
        ui.configured = configured
        ui.layout = layout
        ui.online = online
        ui.panelName = panelName
        ui.panelId = panelId
        ui.dark = PanelTheme.isDark
        ui.pageIndex = pageIndex
        ui.sidecarRevision += 1
        ui.showClock = layout.showClock
        ui.showMic = layout.showMicIndicator
        ui.micLingerSeconds = layout.micIndicatorLingerSeconds
    }

    private fun stepThermostat(climate: EntityState, up: Boolean) {
        val current = climate.numberAttribute("current_temperature")
        val target = climate.numberAttribute("temperature") ?: current ?: 20.0
        val low = climate.numberAttribute("target_temp_low") ?: target
        val high = climate.numberAttribute("target_temp_high") ?: target
        val step = temperatureStep(climate).let { if (up) it else -it }
        if (climate.state != "heat_cool") {
            changeTemperature(climate, target + step)
            return
        }
        val selected = selectedClimateTarget[climate.entityId] ?: "heat"
        // The two targets may not cross, so each is clamped against the other.
        if (selected == "heat") {
            changeTemperatureRange(climate, (low + step).coerceAtMost(high - step.absoluteValue), high)
        } else {
            changeTemperatureRange(climate, low, (high + step).coerceAtLeast(low + step.absoluteValue))
        }
    }

    /** The widget that configured an entity, wherever it sits in the layout. */
    private fun widgetFor(entityId: String): DashboardWidget? =
        layout.pages.flatMap { it.widgets }.firstOrNull { it.entityId == entityId }

    private fun primaryText(value: String, size: Float): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(PanelTheme.ink)
        }

    private fun secondaryText(value: String): TextView =
        TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(MUTED)
        }

    /** The flat button the modal dialogs are built from. */
    private fun modalAction(label: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(PanelTheme.ink)
        background = cardBackground(PanelTheme.panel, PanelTheme.line, 13)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun deviceSeekBar(value: Int, onChanged: (Int) -> Unit): View =
        PanelSliderView(context, value, onChanged).apply { isEnabled = online }

    private fun showScheduleListDialog(entity: EntityState, widget: DashboardWidget?) {
        val values = schedules.filter { it.entityId == entity.entityId }.sortedBy { it.time }
        if (values.isEmpty()) {
            showScheduleDialog(entity, widget, null)
            return
        }
        showPanelDialog(context, PanelTheme.isDark) { dismiss ->
            PanelDialogHeader("Schedules", entity.friendlyName)
            values.forEach { schedule ->
                PanelDialogAction("${schedule.time}  \u00b7  ${schedule.action.replace('_', ' ')}") {
                    dismiss()
                    showScheduleDialog(entity, widget, schedule)
                }
            }
            PanelDialogAction("\uff0b Add schedule") {
                dismiss()
                showScheduleDialog(entity, widget, null)
            }
        }
    }

    private fun showScheduleDialog(entity: EntityState, widget: DashboardWidget?, existing: ControlSchedule?) {
        val actions = when (entity.domain) {
            "cover" -> buildList {
                add("open" to "Open")
                add("close" to "Close")
                add("set_position" to "Set position")
                if (widget?.gradualOpenScript != null) add("gradual_open" to "Gradual open")
                if (widget?.gradualCloseScript != null) add("gradual_close" to "Gradual close")
            }
            else -> listOf("turn_on" to "Turn on", "turn_off" to "Turn off", "toggle" to "Toggle")
        }
        val time = EditText(context).apply {
            hint = "07:00"
            setText(existing?.time ?: "07:00")
            textSize = 20f
            setTextColor(PanelTheme.ink)
            background = cardBackground(PanelTheme.panel, PanelTheme.line, 14)
            setPadding(dp(14), 0, dp(14), 0)
        }
        val action = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, actions.map { it.second })
            setSelection(actions.indexOfFirst { it.first == existing?.action }.coerceAtLeast(0))
            background = cardBackground(PanelTheme.panel, PanelTheme.line, 14)
        }
        val position = EditText(context).apply {
            hint = "Position 0–100"
            setText((existing?.position ?: 100).toString())
            textSize = 16f
            setTextColor(PanelTheme.ink)
            background = cardBackground(PanelTheme.panel, PanelTheme.line, 14)
            setPadding(dp(14), 0, dp(14), 0)
            visibility = if (entity.domain == "cover") View.VISIBLE else View.GONE
        }
        val enabled = CheckBox(context).apply {
            text = "Enabled"
            textSize = 15f
            setTextColor(PanelTheme.ink)
            isChecked = existing?.enabled ?: true
        }
        val selectedDays = (existing?.weekdays ?: WEEKDAY_IDS).toMutableSet()
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = cardBackground(CARD, CARD_EDGE, 24)
            addView(primaryText("Schedule", 23f).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(secondaryText(entity.friendlyName).apply { textSize = 12f; setPadding(0, 0, 0, dp(8)) })
            addView(LinearLayout(context).apply {
                addView(time, LayoutParams(0, dp(50), .8f).apply { rightMargin = dp(4) })
                addView(action, LayoutParams(0, dp(50), 1.2f).apply { leftMargin = dp(4) })
            })
            if (entity.domain == "cover") addView(position, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(7) })
            addView(LinearLayout(context).apply {
                WEEKDAY_IDS.forEach { day ->
                    addView(CheckBox(context).apply {
                        text = day.replaceFirstChar { it.uppercase() }
                        textSize = 10f
                        setTextColor(PanelTheme.ink)
                        isChecked = day in selectedDays
                        setOnCheckedChangeListener { _, checked -> if (checked) selectedDays.add(day) else selectedDays.remove(day) }
                    }, LayoutParams(0, dp(42), 1f))
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(5) })
            addView(enabled)
            addView(LinearLayout(context).apply {
                if (existing?.id != null) addView(modalAction("Delete") {
                    deleteSchedule(existing.id)
                    dialog.dismiss()
                }, LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
                addView(modalAction("Save") {
                    val clock = time.text.toString().trim()
                    if (!Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(clock)) {
                        time.error = "Use HH:MM"
                        return@modalAction
                    }
                    if (selectedDays.isEmpty()) return@modalAction
                    val selectedAction = actions[action.selectedItemPosition].first
                    val script = when (selectedAction) {
                        "gradual_open" -> widget?.gradualOpenScript
                        "gradual_close" -> widget?.gradualCloseScript
                        else -> null
                    }
                    upsertSchedule(ControlSchedule(existing?.id, entity.entityId, clock, WEEKDAY_IDS.filter(selectedDays::contains),
                        selectedAction, position.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 100,
                        script, enabled.isChecked))
                    dialog.dismiss()
                }, LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4) })
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(7) })
        })
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(.65f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout((resources.displayMetrics.widthPixels * .94f).roundToInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun showFanSpeedDialog(entity: EntityState) {
        val percent = entity.numberAttribute("percentage")?.roundToInt() ?: 0
        showPanelDialog(context, PanelTheme.isDark) { dismiss ->
            PanelDialogHeader("Fan speed", entity.friendlyName)
            PanelText(
                "$percent%",
                LocalPanelType.current.reading,
                Modifier.fillMaxWidth(),
                bold = true,
                align = TextAlign.Center,
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(LocalPanelSize.current.levelBand),
                factory = { host ->
                    PanelSliderView(host, percent) { value ->
                        callService("fan", "set_percentage", entity.entityId, JSONObject().put("percentage", value))
                    }
                },
            )
            Row(Modifier.fillMaxWidth()) {
                listOf(25 to "Low", 50 to "Medium", 100 to "High").forEach { (value, label) ->
                    Box(Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 4.dp)) {
                        PanelDialogButton(
                            label = label,
                            height = LocalPanelSize.current.presetCell,
                            active = false,
                            radius = LocalPanelRadius.current.card,
                        ) {
                            callService("fan", "set_percentage", entity.entityId, JSONObject().put("percentage", value))
                            dismiss()
                        }
                    }
                }
            }
        }
    }

    private fun showCoverDialog(entity: EntityState, widget: DashboardWidget?) {
        val position = entity.numberAttribute("current_position")?.roundToInt() ?: 0
        // SUPPORT_SET_POSITION. Without it the cover only opens and closes.
        val canPosition = entity.attributes.optInt("supported_features", 0) and 4 != 0
        showPanelDialog(context, PanelTheme.isDark) { dismiss ->
            PanelDialogHeader("Curtains", entity.friendlyName)
            if (canPosition) {
                PanelText(
                    "Position · $position%",
                    LocalPanelType.current.reading,
                    Modifier.fillMaxWidth(),
                    bold = true,
                    align = TextAlign.Center,
                )
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(LocalPanelSize.current.levelBand),
                    factory = { host ->
                        PanelSliderView(host, position) { value ->
                            callService("cover", "set_cover_position", entity.entityId, JSONObject().put("position", value))
                        }
                    },
                )
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Open" to "open_cover", "Stop" to "stop_cover", "Close" to "close_cover")
                    .forEach { (label, service) ->
                        Box(Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 4.dp)) {
                            PanelDialogButton(
                                label = label,
                                height = LocalPanelSize.current.presetCell,
                                active = false,
                                radius = LocalPanelRadius.current.card,
                            ) {
                                callService("cover", service, entity.entityId, JSONObject())
                                // Stop also has to end a gradual run, or the
                                // script keeps driving the cover afterwards.
                                if (service == "stop_cover") {
                                    widget?.gradualOpenScript?.let { callService("script", "turn_off", it, JSONObject()) }
                                    widget?.gradualCloseScript?.let { callService("script", "turn_off", it, JSONObject()) }
                                } else {
                                    dismiss()
                                }
                            }
                        }
                    }
            }
            listOfNotNull(
                widget?.gradualOpenScript?.let { "Gradually open" to it },
                widget?.gradualCloseScript?.let { "Gradually close" to it },
            ).forEach { (label, script) ->
                PanelDialogAction(label) {
                    callService("script", "turn_on", script, JSONObject())
                    dismiss()
                }
            }
        }
    }

    private fun showTimerDialog(entity: EntityState, presets: List<Int>) {
        val running = timerMinutes[entity.entityId]?.takeIf { it > 0 }
        showPanelDialog(context, PanelTheme.isDark, widthFraction = .86f) { dismiss ->
            PanelDialogHeader("Timer", entity.friendlyName)
            PanelDialogChoices(
                labels = presets.distinct().map { "$it min" },
                selected = { it == "$running min" },
            ) { label ->
                setTimer(entity, label.substringBefore(" ").toInt())
                dismiss()
            }
            if (running != null) {
                PanelDialogAction("Cancel timer", destructive = true) {
                    setTimer(entity, 0)
                    dismiss()
                }
            }
        }
    }

    private fun setTimer(entity: EntityState, minutes: Int) {
        timerCallbacks.remove(entity.entityId)?.let(::removeCallbacks)
        timerMinutes[entity.entityId] = minutes
        if (minutes > 0) {
            val callback = Runnable {
                callService(entity.domain, "turn_off", entity.entityId, JSONObject())
                timerMinutes[entity.entityId] = 0
                timerCallbacks.remove(entity.entityId)
                ui.sidecarRevision += 1
            }
            timerCallbacks[entity.entityId] = callback
            postDelayed(callback, minutes * 60_000L)
        }
        ui.sidecarRevision += 1
    }

    private fun toggleEntity(entity: EntityState) {
        when (entity.domain) {
            "cover" -> callService(
                "cover",
                if (entity.state in setOf("open", "opening")) "close_cover" else "open_cover",
                entity.entityId,
                JSONObject(),
            )
            else -> callService(entity.domain, "toggle", entity.entityId, JSONObject())
        }
    }

    private fun resolveEntity(widget: DashboardWidget, fallbackDomain: String? = null): EntityState? =
        widget.entityId?.let(states::get)
            ?: fallbackDomain?.let { domain -> states.values.firstOrNull { it.domain == domain } }

    private fun changeTemperature(climate: EntityState, value: Double) {
        val min = climate.numberAttribute("min_temp") ?: 7.0
        val max = climate.numberAttribute("max_temp") ?: 35.0
        callService(
            "climate",
            "set_temperature",
            climate.entityId,
            JSONObject().put("temperature", value.coerceIn(min, max)),
        )
    }

    private fun changeTemperatureRange(climate: EntityState, low: Double, high: Double) {
        val min = climate.numberAttribute("min_temp") ?: 7.0
        val max = climate.numberAttribute("max_temp") ?: 35.0
        val safeLow = low.coerceIn(min, max)
        val safeHigh = high.coerceIn(safeLow, max)
        callService(
            "climate",
            "set_temperature",
            climate.entityId,
            JSONObject()
                .put("target_temp_low", safeLow)
                .put("target_temp_high", safeHigh),
        )
    }

    private fun temperatureStep(climate: EntityState): Double =
        climate.numberAttribute("target_temp_step") ?: 0.5

    private fun roundActionButton(
        label: String,
        size: Int = 48,
        action: () -> Unit,
    ): Button =
        Button(context).apply {
            text = label
            textSize = 26f
            isAllCaps = false
            setTextColor(PanelTheme.ink)
            background = cardBackground(CONTROL)
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { action() }
            isEnabled = online
            alpha = if (online) 1f else .55f
            layoutParams = LayoutParams(dp(size), dp(size)).apply { setMargins(dp(5), 0, dp(5), 0) }
        }

    private fun cardBackground(
        color: Int,
        stroke: Int = CARD_EDGE,
        radius: Int = 18,
    ): GradientDrawable = PanelTheme.rounded(context, color, radius, stroke)

    private fun format(value: Double): String =
        if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
        else "%.1f".format(value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val CONTROL_DOMAINS = setOf("light", "switch", "input_boolean", "fan", "cover")
        private val TIMER_DOMAINS = setOf("light", "switch", "fan")
        private val WEEKDAY_IDS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        private val BACKGROUND get() = PanelTheme.canvas
        private val CARD get() = PanelTheme.card
        private val CARD_EDGE get() = PanelTheme.line
        private val CONTROL get() = PanelTheme.cardSecondary
        private val ACCENT get() = PanelTheme.accent
        private val ACCENT_DARK get() = PanelTheme.accent
        private val ACCENT_WASH get() = PanelTheme.accentWash
        private val MUTED get() = PanelTheme.muted
        private const val ENTITY_REFRESH_DELAY_MS = 50L
        private const val RENDER_LOG_TAG = "PanelRender"
    }
}
