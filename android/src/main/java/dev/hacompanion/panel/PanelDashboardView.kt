package dev.hacompanion.panel

import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import dev.hacompanion.panel.ui.model.sentenceCase
import dev.hacompanion.panel.ui.model.thermostatModel
import dev.hacompanion.panel.ui.slab.Sheet
import dev.hacompanion.panel.ui.slab.SheetModes
import dev.hacompanion.panel.ui.slab.SheetLevel
import dev.hacompanion.panel.ui.slab.SheetLink
import dev.hacompanion.panel.ui.slab.EditorActions
import dev.hacompanion.panel.ui.slab.EditorHeader
import dev.hacompanion.panel.ui.slab.EditorPick
import dev.hacompanion.panel.ui.slab.ScheduleRow
import dev.hacompanion.panel.ui.slab.SegmentedRow
import dev.hacompanion.panel.ui.slab.TimeStepper
import dev.hacompanion.panel.ui.slab.WeekdayRow
import dev.hacompanion.panel.ui.slab.showPanelScreen
import dev.hacompanion.panel.ui.slab.SheetAdd
import dev.hacompanion.panel.ui.slab.SheetNote
import dev.hacompanion.panel.ui.slab.SheetAction
import dev.hacompanion.panel.ui.slab.SheetActions
import dev.hacompanion.panel.ui.slab.SheetOptions
import dev.hacompanion.panel.ui.slab.SheetPresets
import dev.hacompanion.panel.ui.model.presetsFor
import dev.hacompanion.panel.ui.slab.showPanelSheet
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import dev.hacompanion.panel.ui.model.ControlBody
import dev.hacompanion.panel.ui.model.ControlCardModel
import dev.hacompanion.panel.ui.model.controlCard
import dev.hacompanion.panel.ui.model.coverIndeterminate
import dev.hacompanion.panel.ui.model.timerRemaining
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
    /**
     * When each running timer fires, on the elapsed-realtime clock.
     *
     * A deadline rather than the duration that was chosen, because the tile
     * shows a countdown. Snapshot state so the corner mark follows it, and
     * elapsed-realtime so a wall-clock change cannot move a timer.
     */
    private val timerDeadlines = mutableStateMapOf<String, Long>()
    private val timerCallbacks = mutableMapOf<String, Runnable>()
    private var schedules = emptyList<ControlSchedule>()
    /**
     * Every sheet currently on screen, outermost first.
     *
     * Kept so returning to the default page can clear them: that timeout is
     * the panel deciding the interaction is over, and a sheet left open over
     * a page nobody chose is the interaction outliving it.
     */
    private val openSheets = mutableListOf<Dialog>()

    private fun dismissSheets() {
        openSheets.toList().forEach(Dialog::dismiss)
        openSheets.clear()
    }

    private val returnToDefault = Runnable {
        if (!interactionActive && dashboardActive) {
            dismissSheets()
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
            selectedTargetFor(entityId)

        override fun selectClimateTarget(entityId: String, target: String) {
            ui.selectedTargets[entityId] = target
        }

        override fun stepThermostat(entityId: String, up: Boolean) {
            states[entityId]?.let { this@PanelDashboardView.stepThermostat(it, up) }
        }

        override fun openMoreModes(entityId: String) {
            states[entityId]?.let(::showMoreModesSheet)
        }

        override fun openClimateAttribute(entityId: String, key: String) {
            states[entityId]?.let { showClimateAttributeSheet(it, key) }
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

        override fun openBrightness(entityId: String) {
            states[entityId]?.let(::showControlSheet)
        }

        override fun moveCover(entityId: String, action: String) {
            states[entityId]?.let { nudgeCover(it, action) }
        }

        override fun openFanSpeed(entityId: String) {
            states[entityId]?.let(::showControlSheet)
        }

        override fun openCover(entityId: String) {
            states[entityId]?.let(::showControlSheet)
        }

        override fun openSchedule(entityId: String) {
            states[entityId]?.let { showScheduleListDialog(it, widgetFor(entityId)) }
        }

        override fun openTimer(entityId: String) {
            states[entityId]?.let {
                showTimerSheet(it, widgetFor(entityId)?.timerPresets ?: listOf(5, 15, 30, 60))
            }
        }

        override fun timerRemaining(entityId: String): String? {
            // Read the tick so this recomposes each second while one runs.
            ui.timerTick
            return timerRemaining(timerDeadlines[entityId], SystemClock.elapsedRealtime())
        }

        override fun coverIndeterminate(entityId: String): Boolean {
            // Reading the tick is what makes a tile redraw when the silence
            // crosses 1.5 s with no new state arriving to trigger it.
            ui.timerTick
            val entity = states[entityId] ?: return false
            val now = SystemClock.elapsedRealtime()
            return coverIndeterminate(
                moving = entity.state in setOf("opening", "closing"),
                sincePosition = dashboardState.sincePosition(entityId, now),
            )
        }

        override fun scheduleNext(entityId: String): String? =
            schedules.filter { it.entityId == entityId && it.enabled }
                .minByOrNull { it.time }
                ?.let { "Next \u00b7 ${it.time} \u00b7 ${sentenceCase(it.action)}" }

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
            dashboardState.noteCoverPositions(SystemClock.elapsedRealtime())
            startMotionTickIfMoving()
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
        val selected = selectedTargetFor(climate.entityId)
        // The two targets may not cross, so each is clamped against the other.
        if (selected == "heat") {
            changeTemperatureRange(climate, (low + step).coerceAtMost(high - step.absoluteValue), high)
        } else {
            changeTemperatureRange(climate, low, (high + step).coerceAtLeast(low + step.absoluteValue))
        }
    }

    /**
     * The strip's arrows move a cover a step at a time.
     *
     * open_cover runs the blind all the way, which is almost never what a tap
     * on a panel means — you are adjusting the light in the room, not sending
     * it to a limit. A cover that cannot be positioned has only the limits, so
     * there it stays a full traverse.
     */
    private fun nudgeCover(cover: EntityState, action: String) {
        if (action == "stop") {
            callService("cover", "stop_cover", cover.entityId, JSONObject())
            return
        }
        val position = cover.numberAttribute("current_position")?.roundToInt()
        val canPosition = cover.attributes.optInt("supported_features", 0) and COVER_SET_POSITION != 0
        if (position == null || !canPosition) {
            callService("cover", "${action}_cover", cover.entityId, JSONObject())
            return
        }
        val step = if (action == "open") COVER_NUDGE else -COVER_NUDGE
        callService(
            "cover", "set_cover_position", cover.entityId,
            JSONObject().put("position", (position + step).coerceIn(0, 100)),
        )
    }

    /** A sheet that the panel knows about, so returning home can clear it. */
    private fun panelSheet(content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) =
        showPanelSheet(
            context, PanelTheme.isDark,
            onShow = { openSheets += it },
            onDismiss = { openSheets -= it },
            content = content,
        )

    /**
     * The schedules an entity has, and the way to another.
     *
     * Home Assistant keeps these, so the sheet says so: they fire with the
     * panel asleep, which is the whole difference between one of these and
     * the timer sitting above it.
     */
    private fun showScheduleSheet(entity: EntityState, widget: DashboardWidget?) {
        val entityId = entity.entityId
        val name = widget?.label ?: entity.friendlyName
        panelSheet { dismiss ->
            // sidecarRevision is what a schedule change bumps, so the list
            // follows an edit made in the sheet stacked above this one.
            ui.sidecarRevision
            val values = schedules.filter { it.entityId == entityId }.sortedBy { it.time }
            Sheet(
                "Schedules",
                "$name \u00b7 kept by Home Assistant",
                dismiss,
            ) {
                values.forEach { schedule ->
                    ScheduleRow(
                        time = schedule.time,
                        detail = "${sentenceCase(schedule.action)} \u00b7 ${weekdayLabel(schedule.weekdays)}",
                        enabled = schedule.enabled,
                        onToggle = { upsertSchedule(schedule.copy(enabled = !schedule.enabled)) },
                        onOpen = { showScheduleDialog(entity, widget, schedule) },
                    )
                }
                SheetAdd("\uff0b Add schedule") { showScheduleDialog(entity, widget, null) }
            }
        }
    }

    /** Mon-Fri rather than five names, which will not fit the row. */
    private fun weekdayLabel(days: List<String>): String {
        val all = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        val chosen = all.filter { it in days.map(String::lowercase) }
        return when {
            chosen.size == 7 -> "Every day"
            chosen.isEmpty() -> "Once"
            chosen == all.take(5) -> "Weekdays"
            chosen == all.drop(5) -> "Weekends"
            else -> chosen.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }
    }

    /**
     * Every control opens the same sheet; only the band in the middle differs.
     *
     * The variant comes from what the entity can actually do rather than from
     * a setting, so a light that reports brightness gets a level band and one
     * that does not gets none — and both still get the timer and schedule
     * rows, which is where those live now that the tile carries only marks.
     */
    private fun showControlSheet(entity: EntityState) {
        val entityId = entity.entityId
        val widget = widgetFor(entityId)
        val name = widget?.label ?: entity.friendlyName
        val card = controlCard(entity, widget, dense = false)
        val canPosition =
            entity.attributes.optInt("supported_features", 0) and COVER_SET_POSITION != 0

        panelSheet { dismiss ->
            // The live entity, not the one captured on the way in: a band that
            // freezes while the light it drives moves is worse than no band.
            val live = states[entityId] ?: entity
            val moving = live.state in setOf("opening", "closing")
            Sheet(name, sheetSubtitle(live, card, moving), dismiss) {
                // The long press survives an unavailable device precisely so
                // this line can exist: the tile has no room to say why it went
                // quiet, and every control below it would be a lie.
                when {
                    !card.available -> SheetNote(
                        "Home Assistant is not reporting this device. Its schedules " +
                            "still run \u2014 they are kept there, not here.",
                    )
                    else -> Unit
                }
                if (card.available) when (card.body) {
                    ControlBody.DIMMER -> {
                        val percent =
                            ((live.numberAttribute("brightness") ?: 0.0) / 255.0 * 100.0).roundToInt()
                        SheetLevel(percent) { setBrightness(entityId, it) }
                        SheetPresets(presetsFor("light")) {
                            setBrightness(entityId, it)
                            dismiss()
                        }
                    }
                    ControlBody.FAN -> {
                        val percent = live.numberAttribute("percentage")?.roundToInt() ?: 0
                        SheetLevel(percent) { setFanSpeed(entityId, it) }
                        SheetPresets(presetsFor("fan")) {
                            setFanSpeed(entityId, it)
                            dismiss()
                        }
                    }
                    ControlBody.COVER -> {
                        if (canPosition) {
                            val position =
                                live.numberAttribute("current_position")?.roundToInt() ?: 0
                            SheetLevel(
                                position,
                                height = LocalPanelSize.current.coverBand,
                                // The sheet is not a different device: a cover
                                // that has gone quiet is quiet here too.
                                indeterminate = dashboardActions.coverIndeterminate(entityId),
                                opening = live.state == "opening",
                            ) {
                                callService(
                                    "cover", "set_cover_position", entityId,
                                    JSONObject().put("position", it),
                                )
                            }
                        }
                        SheetActions(
                            listOf(
                                SheetAction("open", "\u25b2", "OPEN"),
                                SheetAction("stop", "\u25a0", "STOP", weight = 1.4f, active = moving),
                                SheetAction("close", "\u25bc", "CLOSE"),
                            ),
                        ) { action ->
                            callService("cover", "${action}_cover", entityId, JSONObject())
                            if (action != "stop") dismiss()
                        }
                    }
                    // A binary control has no level to set, so its sheet is
                    // the two doors and nothing above them.
                    ControlBody.BINARY -> Unit
                }

                if (card.showTimer && card.available) {
                    ui.timerTick
                    val left = timerRemaining(timerDeadlines[entityId], SystemClock.elapsedRealtime())
                    SheetLink(
                        "clock",
                        if (left == null) "Timer" else "Timer \u00b7 $left",
                        if (left == null) "Turns it off after a while \u00b7 this panel only"
                        else "Turns off when it ends \u00b7 this panel only",
                        filled = left != null,
                    ) {
                        showTimerSheet(live, widget?.timerPresets ?: listOf(5, 15, 30, 60))
                    }
                }
                if (card.showSchedule) {
                    val count = schedules.count { it.entityId == entityId }
                    SheetLink(
                        "schedule",
                        if (count == 0) "Schedules" else "Schedules \u00b7 $count",
                        // Home Assistant runs these, so they hold with the
                        // panel asleep — worth saying, since the timer above
                        // does not.
                        dashboardActions.scheduleNext(entityId) ?: "None yet \u00b7 kept by Home Assistant",
                    ) {
                        showScheduleSheet(live, widget)
                    }
                }
            }
        }
    }

    /** What the sheet header says the thing is doing right now. */
    private fun sheetSubtitle(live: EntityState, card: ControlCardModel, moving: Boolean): String =
        when {
            moving -> sentenceCase(live.state)
            card.levelText != null -> "${sentenceCase(live.state)} \u00b7 ${card.levelText}"
            else -> sentenceCase(live.state)
        }

    private fun setBrightness(entityId: String, percent: Int) = callService(
        "light", "turn_on", entityId, JSONObject().put("brightness_pct", percent),
    )

    private fun setFanSpeed(entityId: String, percent: Int) = callService(
        "fan", "set_percentage", entityId, JSONObject().put("percentage", percent),
    )

    /**
     * The modes that did not earn a cell in the row, as a sheet.
     *
     * Picking one is a mode change like any other, so it replaces whatever is
     * running rather than adding to it — which is what the subtitle says.
     */
    private fun showMoreModesSheet(climate: EntityState) {
        val model = thermostatModel(climate, widgetFor(climate.entityId)?.label)
        if (model.moreOptions.isEmpty()) return
        showPanelSheet(context, PanelTheme.isDark) { dismiss ->
            Sheet("More modes", "${model.name} \u00b7 replaces the current mode", dismiss) {
                SheetModes(model.moreOptions) { cell ->
                    callService(
                        "climate", "set_hvac_mode", climate.entityId,
                        JSONObject().put("hvac_mode", cell.key),
                    )
                    dismiss()
                }
            }
        }
    }

    /**
     * Fan speed or swing, whose option lists are the vendor's rather than
     * ours — so the sheet is built from what the entity reports.
     */
    private fun showClimateAttributeSheet(climate: EntityState, key: String) {
        val optionsKey = if (key == "swing_mode") "swing_modes" else "fan_modes"
        val array = climate.attributes.optJSONArray(optionsKey) ?: return
        val options = buildList<Pair<String, String>> {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value to sentenceCase(value))
            }
        }
        if (options.isEmpty()) return
        val title = if (key == "swing_mode") "Swing" else "Fan speed"
        val name = widgetFor(climate.entityId)?.label ?: climate.friendlyName
        showPanelSheet(context, PanelTheme.isDark) { dismiss ->
            Sheet(title, "$name \u00b7 stays set across modes", dismiss) {
                SheetOptions(options, climate.attributes.optString(key)) { value ->
                    val service = if (key == "swing_mode") "set_swing_mode" else "set_fan_mode"
                    callService("climate", service, climate.entityId, JSONObject().put(key, value))
                    dismiss()
                }
            }
        }
    }

    /**
     * The setpoint the rail is adjusting.
     *
     * Resolved rather than stored on first read: a cooling unit starts on its
     * cool setpoint, and writing that default during composition would be a
     * snapshot write from inside the frame that reads it.
     */
    private fun selectedTargetFor(entityId: String): String =
        ui.selectedTargets[entityId]
            ?: if (states[entityId]?.state == "cool") "cool" else "heat"

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

    /**
     * The schedule editor, as a screen rather than a sheet.
     *
     * It needs the full width for its seven day cells, and no keyboard: the
     * time is stepped, the action is picked, the days are toggled. That was
     * the last EditText, Spinner and CheckBox on the dashboard. The ones
     * left are in administration, where a URL and a token do need typing.
     */
    private fun showScheduleDialog(
        entity: EntityState,
        widget: DashboardWidget?,
        existing: ControlSchedule?,
    ) {
        val choices = scheduleActions(entity, widget)
        val start = existing?.time?.split(":")?.mapNotNull(String::toIntOrNull)
        var hour by mutableStateOf(start?.getOrNull(0) ?: 7)
        var minute by mutableStateOf(start?.getOrNull(1) ?: 0)
        var action by mutableStateOf(existing?.action ?: choices.first().first)
        var position by mutableStateOf(existing?.position ?: 100)
        val days = mutableStateListOf<String>().apply {
            addAll(existing?.weekdays ?: WEEKDAY_IDS)
        }

        showPanelScreen(
            context, PanelTheme.isDark,
            onShow = { openSheets += it },
            onDismiss = { openSheets -= it },
        ) { dismiss ->
            val name = widget?.label ?: entity.friendlyName
            Column(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
                EditorHeader("Schedule \u00b7 $name")
                TimeStepper(
                    hour = hour,
                    minute = minute,
                    // Both wrap: a stepper that stops at midnight makes 23:00
                    // a long way from 00:00 for no reason.
                    onHour = { hour = (it + 24) % 24 },
                    onMinute = { minute = (it + 60) % 60 },
                )
                // Three choices fit side by side; five do not, and two of a
                // cover's five are there only when the scripts are configured.
                if (choices.size <= 3) {
                    SegmentedRow(choices, action) { action = it }
                } else {
                    EditorPick(
                        "ACTION",
                        choices.first { it.first == action }.second,
                    ) {
                        showActionPicker(choices, action) { action = it }
                    }
                }
                when {
                    action == "set_position" -> SheetLevel(
                        position,
                        height = LocalPanelSize.current.segmentRow,
                    ) { position = it }
                    action.startsWith("gradual_") -> EditorPick(
                        "SCRIPT",
                        scriptFor(widget, action) ?: "not configured",
                    ) {}
                }
                WeekdayRow(WEEKDAY_IDS, days.toSet()) { day ->
                    if (day in days) days.remove(day) else days.add(day)
                }
                EditorActions(
                    // Only a saved schedule can be deleted, and only one with
                    // an id has reached Home Assistant to be deleted from.
                    onDelete = existing?.id?.let { id ->
                        {
                            deleteSchedule(id)
                            dismiss()
                        }
                    },
                    onDiscard = dismiss,
                ) {
                    upsertSchedule(
                        ControlSchedule(
                            id = existing?.id,
                            entityId = entity.entityId,
                            time = "%02d:%02d".format(hour, minute),
                            weekdays = WEEKDAY_IDS.filter(days::contains),
                            action = action,
                            position = position,
                            scriptEntityId = scriptFor(widget, action),
                            enabled = existing?.enabled ?: true,
                        ),
                    )
                    dismiss()
                }
            }
        }
    }

    /** Exactly what this entity accepts, which is what the picker lists. */
    private fun scheduleActions(
        entity: EntityState,
        widget: DashboardWidget?,
    ): List<Pair<String, String>> = when (entity.domain) {
        "cover" -> buildList {
            add("open" to "Open")
            add("close" to "Close")
            add("set_position" to "Set position")
            if (widget?.gradualOpenScript != null) add("gradual_open" to "Gradual open")
            if (widget?.gradualCloseScript != null) add("gradual_close" to "Gradual close")
        }
        else -> listOf("turn_on" to "Turn on", "turn_off" to "Turn off", "toggle" to "Toggle")
    }

    private fun scriptFor(widget: DashboardWidget?, action: String): String? = when (action) {
        "gradual_open" -> widget?.gradualOpenScript
        "gradual_close" -> widget?.gradualCloseScript
        else -> null
    }

    private fun showActionPicker(
        choices: List<Pair<String, String>>,
        selected: String,
        onPick: (String) -> Unit,
    ) = panelSheet { dismiss ->
        Sheet("Action", "What the schedule will do", dismiss) {
            SheetOptions(choices, selected) {
                onPick(it)
                dismiss()
            }
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

    private fun showCoverSheet(entity: EntityState) {
        val entityId = entity.entityId
        val name = widgetFor(entityId)?.label ?: entity.friendlyName
        val canPosition =
            entity.attributes.optInt("supported_features", 0) and COVER_SET_POSITION != 0
        showPanelSheet(context, PanelTheme.isDark) { dismiss ->
            val live = states[entityId]
            val position = live?.numberAttribute("current_position")?.roundToInt() ?: 0
            val moving = live?.state in setOf("opening", "closing")
            val subtitle = buildString {
                append(sentenceCase(live?.state ?: "unknown"))
                // While it travels, the number that matters is where it is
                // going — the fill already says where it is.
                if (moving) {
                    live?.numberAttribute("current_position")?.let { append(" \u00b7 $position%") }
                }
            }
            Sheet(name, subtitle, dismiss) {
                if (canPosition) {
                    SheetLevel(position, height = LocalPanelSize.current.coverBand) { value ->
                        callService(
                            "cover", "set_cover_position", entityId,
                            JSONObject().put("position", value),
                        )
                    }
                }
                SheetActions(
                    listOf(
                        SheetAction("open", "\u25b2", "OPEN"),
                        SheetAction("stop", "\u25a0", "STOP", weight = 1.4f, active = moving),
                        SheetAction("close", "\u25bc", "CLOSE"),
                    ),
                ) { action ->
                    callService("cover", "${action}_cover", entityId, JSONObject())
                    if (action != "stop") dismiss()
                }
            }
        }
    }

    /**
     * The timer as a sheet: the presets the layout chose, and a way out.
     *
     * The layout may name one to four of them, so the grid follows the count
     * rather than the count following a fixed grid — SheetOptions already
     * gives an odd one out the full width of its row.
     */
    private fun showTimerSheet(entity: EntityState, presets: List<Int>) {
        val entityId = entity.entityId
        val name = widgetFor(entityId)?.label ?: entity.friendlyName
        panelSheet { dismiss ->
            ui.timerTick
            val left = timerRemaining(timerDeadlines[entityId], SystemClock.elapsedRealtime())
            Sheet(
                if (left == null) "Timer" else "Timer \u00b7 $left",
                if (left == null) "$name \u00b7 turns off when it ends"
                else "$name \u00b7 this panel only",
                dismiss,
            ) {
                SheetOptions(
                    presets.distinct().take(4).map { "$it" to "$it min" },
                    // Nothing is selected once it is running: the running time
                    // is in the header, and the preset that started it is no
                    // longer the thing you are choosing between.
                    selected = null,
                ) { minutes ->
                    setTimer(entity, minutes.toInt())
                    dismiss()
                }
                if (left != null) {
                    SheetOptions(listOf("cancel" to "Cancel timer"), selected = null) {
                        setTimer(entity, 0)
                        dismiss()
                    }
                }
            }
        }
    }

    private fun setTimer(entity: EntityState, minutes: Int) {
        timerCallbacks.remove(entity.entityId)?.let(::removeCallbacks)
        if (minutes <= 0) {
            timerDeadlines.remove(entity.entityId)
            stopTimerTickIfIdle()
            return
        }
        timerDeadlines[entity.entityId] = SystemClock.elapsedRealtime() + minutes * 60_000L
        val callback = Runnable {
            callService(entity.domain, "turn_off", entity.entityId, JSONObject())
            timerDeadlines.remove(entity.entityId)
            timerCallbacks.remove(entity.entityId)
            stopTimerTickIfIdle()
        }
        timerCallbacks[entity.entityId] = callback
        postDelayed(callback, minutes * 60_000L)
        startTimerTick()
    }

    /**
     * One second is the coarsest tick a countdown can use and still be a
     * countdown, and it runs only while a timer does — an idle panel posts
     * nothing.
     */
    private val timerTick = object : Runnable {
        override fun run() {
            ui.timerTick += 1
            if (timerDeadlines.isNotEmpty() || anyCoverMoving()) postDelayed(this, 1_000L)
        }
    }

    private fun anyCoverMoving() =
        states.values.any { it.state == "opening" || it.state == "closing" }

    /**
     * A travelling cover needs the same tick a timer does: without a state
     * arriving to trigger it, nothing would redraw at the moment the silence
     * becomes long enough to admit to.
     */
    private fun startMotionTickIfMoving() {
        if (anyCoverMoving()) startTimerTick()
    }

    private fun startTimerTick() {
        removeCallbacks(timerTick)
        postDelayed(timerTick, 1_000L)
    }

    private fun stopTimerTickIfIdle() {
        ui.timerTick += 1
        if (timerDeadlines.isEmpty()) removeCallbacks(timerTick)
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
        /**
         * How far one tap on a cover's arrow moves it.
         *
         * A guess worth stating: five percent is small enough that a tap is an
         * adjustment rather than a command, and large enough to see. If it
         * wants to be per-widget it needs a field in the layout schema, which
         * is the integration's side of the wire.
         */
        private const val COVER_NUDGE = 5

        /** Home Assistant's SUPPORT_SET_POSITION. */
        private const val COVER_SET_POSITION = 4

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
