package dev.hacompanion.panel

import android.content.Context
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
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
    private val states = linkedMapOf<String, EntityState>()
    private val weatherUpdatedAt = mutableMapOf<String, Long>()
    private val pageHost = FrameLayout(context)
    private val pageLabel = TextView(context)
    private val panelStatus = PanelStatusView(context)
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
    private val entityBindings = mutableMapOf<String, MutableList<EntityBinding>>()
    private val dirtyEntityIds = linkedSetOf<String>()
    private var entityRefreshPending = false
    private var fullRenderCount = 0
    private var targetedRefreshBatchCount = 0
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

    init {
        orientation = VERTICAL
        setBackgroundColor(BACKGROUND)
        setPadding(dp(4), dp(4), dp(4), 0)
        addView(
            pageHost,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )
        addView(createFooter())
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
        if (entityBindings.containsKey(value.entityId)) scheduleEntityRefresh(value.entityId)
    }

    fun updateWeatherForecast(entityId: String, forecastType: String, forecast: JSONArray) {
        val existing = states[entityId] ?: return
        val attributes = JSONObject(existing.attributes.toString())
        attributes.put(if (forecastType == "hourly") "hourly_forecast" else "forecast", forecast)
        val updated = existing.copy(attributes = attributes)
        states[entityId] = updated
        weatherUpdatedAt[entityId] = System.currentTimeMillis()
        if (entityBindings.containsKey(entityId)) scheduleEntityRefresh(entityId)
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
        panelStatus.configure(value.showClock, value.showMicIndicator, value.micIndicatorLingerSeconds)
        pageIndex = value.pages.indexOfFirst { it.id == value.defaultPageId }.coerceAtLeast(0)
        scheduleRender()
    }

    fun synchronizeServerTime(serverTimeMs: Long, serverTimezone: String) {
        panelStatus.synchronize(serverTimeMs, serverTimezone)
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
                flushEntityRefreshes()
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

    private fun render() {
        fullRenderCount += 1
        if (BuildConfig.DEBUG) Log.d(RENDER_LOG_TAG, "full=$fullRenderCount targeted=$targetedRefreshBatchCount")
        entityBindings.clear()
        dirtyEntityIds.clear()
        pageHost.removeAllViews()
        if (!configured) {
            pageHost.addView(unconfiguredPage())
            pageLabel.text = ""
            return
        }
        val definition = layout.pages[pageIndex.coerceIn(0, layout.pages.lastIndex)]
        val page = renderPage(definition)
        pageHost.addView(page)
        if (layout.showClock || layout.showMicIndicator) {
            (panelStatus.parent as? android.view.ViewGroup)?.removeView(panelStatus)
            pageHost.addView(panelStatus, FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply { topMargin = dp(3); marginEnd = dp(6) })
        }
        pageLabel.text = layout.pages.indices.joinToString(" ") { if (it == pageIndex) "●" else "○" }
    }

    private fun renderPage(page: DashboardPage): View {
        val only = page.widgets.singleOrNull()
        if (page.widgets.isNotEmpty() && page.widgets.all { it.type in setOf("controls", "entity_button") }) {
            return controlsPage(page)
        }
        return when (only?.type) {
            "thermostat" -> boundEntityView(resolveEntity(only, "climate")) {
                thermostatPage(page.title, only)
            }
            "weather" -> boundEntityView(resolveEntity(only, "weather")) {
                weatherPage(page.title, only)
            }
            "camera" -> CameraPageView(context, only, openCamera)
            "controls" -> controlsPage(page)
            else -> generalPage(page)
        }
    }

    private fun boundEntityView(entity: EntityState?, create: () -> View): View {
        if (entity == null) return create()
        return FrameLayout(context).also { host ->
            fun refresh() {
                host.removeAllViews()
                host.addView(create(), FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
            }
            refresh()
            entityBindings.getOrPut(entity.entityId, ::mutableListOf).add(EntityBinding(::refresh))
        }
    }

    private fun scheduleEntityRefresh(entityId: String) {
        dirtyEntityIds += entityId
        if (interactionActive || entityRefreshPending) return
        entityRefreshPending = true
        postDelayed({
            entityRefreshPending = false
            flushEntityRefreshes()
        }, ENTITY_REFRESH_DELAY_MS)
    }

    private fun flushEntityRefreshes() {
        if (interactionActive || dirtyEntityIds.isEmpty()) return
        val pending = dirtyEntityIds.toList()
        dirtyEntityIds.clear()
        targetedRefreshBatchCount += 1
        if (BuildConfig.DEBUG) Log.d(
            RENDER_LOG_TAG,
            "targeted batch=$targetedRefreshBatchCount entities=${pending.size} full=$fullRenderCount",
        )
        pending.forEach { entityId -> entityBindings[entityId]?.toList()?.forEach { it.refresh() } }
    }

    private data class EntityBinding(val refresh: () -> Unit)

    private fun thermostatPage(title: String, widget: DashboardWidget): View {
        val climate = resolveEntity(widget, "climate")
            ?: return emptyPage(title, "No climate entity found")
        val current = climate.numberAttribute("current_temperature")
        val target = climate.numberAttribute("temperature") ?: current ?: 20.0
        val low = climate.numberAttribute("target_temp_low") ?: target
        val high = climate.numberAttribute("target_temp_high") ?: target
        val unit = climate.attributes.optString("temperature_unit", "°")
        val action = climate.attributes.optString("hvac_action").ifBlank { climate.state }

        return verticalPage(title).apply {
            addView(
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = cardBackground(CARD)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    addView(thermostatHeader(widget.label ?: climate.friendlyName, action, climate.state != "off"))
                    addView(unifiedThermostatContent(climate, current, target, low, high, unit, action), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
                    addView(thermostatModes(climate))
                },
                LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun thermostatHeader(name: String, action: String, powered: Boolean): View =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    addView(primaryText(name, 18f).apply { typeface = Typeface.DEFAULT_BOLD })
                    addView(secondaryText(action.replace('_', ' ').replaceFirstChar { it.uppercase() }).apply {
                        textSize = 11f
                    })
                },
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(TextView(context).apply {
                text = if (powered) "ON" else "OFF"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (powered) ACCENT else MUTED)
                background = PanelTheme.pill(context, if (powered) ACCENT_WASH else CONTROL)
                setPadding(dp(12), dp(7), dp(12), dp(7))
            })
        }

    private fun unifiedThermostatContent(
        climate: EntityState,
        current: Double?,
        target: Double,
        low: Double,
        high: Double,
        unit: String,
        action: String,
    ): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val dual = climate.state == "heat_cool"
        val selected = selectedClimateTarget.getOrPut(climate.entityId) {
            if (climate.state == "cool") "cool" else "heat"
        }
        addView(DualThermostatDialView(
            context,
            mode = climate.state,
            action = action.replace('_', ' ').replaceFirstChar { it.uppercase() },
            current = current?.let { "${format(it)}$unit" } ?: "—",
            heat = if (dual) "${format(low)}$unit" else "${format(target)}$unit",
            cool = if (dual) "${format(high)}$unit" else "${format(target)}$unit",
            selectedTarget = selected,
            onTargetSelected = {
                selectedClimateTarget[climate.entityId] = it
                scheduleEntityRefresh(climate.entityId)
            },
        ), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            val step = temperatureStep(climate)
            addView(roundActionButton("−", 42) {
                if (!dual) changeTemperature(climate, target - step)
                else if (selected == "heat") changeTemperatureRange(climate, low - step, high)
                else changeTemperatureRange(climate, low, (high - step).coerceAtLeast(low + step))
            })
            addView(secondaryText(if (dual) "Tap a temperature, then adjust" else "Adjust target temperature").apply {
                gravity = Gravity.CENTER
                textSize = 11f
            }, LayoutParams(dp(170), LayoutParams.WRAP_CONTENT))
            addView(roundActionButton("+", 42) {
                if (!dual) changeTemperature(climate, target + step)
                else if (selected == "heat") changeTemperatureRange(climate, (low + step).coerceAtMost(high - step), high)
                else changeTemperatureRange(climate, low, high + step)
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
    }

    private fun targetSelector(
        label: String,
        value: Double,
        unit: String,
        selected: Boolean,
        color: Int,
        action: () -> Unit,
    ): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = cardBackground(if (selected) PanelTheme.accentWash else PanelTheme.panel, if (selected) color else PanelTheme.line, 14)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        addView(eyebrow(label).apply { textSize = 8f }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(primaryText("${format(value)}$unit", 24f).apply {
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (selected) color else PanelTheme.ink)
        })
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun dualSetpointCard(
        label: String,
        value: Double,
        unit: String,
        decrease: () -> Unit,
        increase: () -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = cardBackground(PanelTheme.panel, PanelTheme.line, 16)
        setPadding(dp(4), dp(6), dp(4), dp(5))
        addView(eyebrow(label).apply {
            textSize = 9f
            gravity = Gravity.CENTER
            maxLines = 1
        })
        addView(primaryText("${format(value)}$unit", 30f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(roundActionButton("−", 40, decrease))
            addView(roundActionButton("+", 40, increase))
        })
    }

    private fun thermostatModes(climate: EntityState): View {
        val configured = climate.attributes.optJSONArray("hvac_modes")
        val available = if (configured != null) {
            buildList { for (index in 0 until configured.length()) add(configured.optString(index)) }
        } else {
            listOf(climate.state, "off").distinct()
        }
        val ordered = listOf("heat", "cool", "heat_cool", "fan_only", "dry", "off").filter(available::contains)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER
            ordered.forEach { mode ->
                val active = climate.state == mode
                addView(Button(context).apply {
                    text = when (mode) {
                        "heat" -> "☀\nHeat"
                        "cool" -> "❄\nCool"
                        "heat_cool" -> "↔\nAuto"
                        "fan_only" -> "≋\nFan"
                        "dry" -> "◌\nDry"
                        else -> "○\nOff"
                    }
                    textSize = 10f
                    gravity = Gravity.CENTER
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(2), 0, dp(2), 0)
                    setTextColor(if (active) ACCENT else MUTED)
                    background = cardBackground(if (active) ACCENT_WASH else PanelTheme.panel, if (active) ACCENT_WASH else PanelTheme.line, 13)
                    isEnabled = online
                    alpha = if (online) 1f else .55f
                    setOnClickListener {
                        callService(
                            "climate",
                            "set_hvac_mode",
                            climate.entityId,
                            JSONObject().put("hvac_mode", mode),
                        )
                    }
                }, LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
    }

    private fun weatherPage(title: String, widget: DashboardWidget): View {
        val weather = resolveEntity(widget, "weather")
            ?: return emptyPage(title, "No weather entity found")
        val temperature = weather.numberAttribute("temperature")
        val humidity = weather.numberAttribute("humidity")
        val unit = weather.attributes.optString("temperature_unit", "°").let { if (it.contains('°')) "°" else it }
        val forecastDays = widget.forecastDays
        val daily = forecastPeriods(weather, "forecast").take(forecastDays)
        val hourly = forecastPeriods(weather, "hourly_forecast").take(6)

        return verticalPage(title).apply {
            addView(
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    addView(LinearLayout(context).apply {
                        orientation = VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        background = cardBackground(CARD, CARD_EDGE, 20)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        addView(primaryText(weatherSymbol(weather.state), 50f).apply { gravity = Gravity.CENTER })
                        addView(primaryText(temperature?.let { "${format(it)}$unit" } ?: "—", 48f).apply {
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                        })
                        addView(primaryText(weather.state.replace('-', ' ').replaceFirstChar { it.uppercase() }, 18f).apply {
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        })
                        addView(secondaryText(buildString {
                            append("Feels like ")
                            append(weather.numberAttribute("apparent_temperature")?.let(::format) ?: temperature?.let(::format) ?: "—")
                            append(unit)
                            humidity?.let { append(" · ${format(it)}%") }
                        }).apply {
                            textSize = 13f
                            gravity = Gravity.CENTER
                            maxLines = 2
                        })
                    }, LayoutParams(0, LayoutParams.MATCH_PARENT, if (forecastDays == 1) 1.15f else 1f).apply { rightMargin = dp(4) })
                    addView(
                        LinearLayout(context).apply {
                            orientation = VERTICAL
                            daily.forEach { period ->
                                addView(dailyForecastRow(period, unit), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                                    bottomMargin = dp(4)
                                })
                            }
                        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(4) },
                    )
                }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
            )
            if (widget.showHourly && hourly.isNotEmpty()) addView(
                hourlyForecastCard(weather, hourly, unit),
                LayoutParams(LayoutParams.MATCH_PARENT, dp(104)).apply { topMargin = dp(7) },
            )
        }
    }

    private fun dailyForecastRow(period: ForecastPeriod, unit: String): View =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground(CARD, CARD_EDGE, 15)
            setPadding(dp(9), dp(5), dp(9), dp(5))
            addView(primaryText(period.label, 11f).apply { typeface = Typeface.DEFAULT_BOLD }, LayoutParams(dp(42), LayoutParams.WRAP_CONTENT))
            addView(primaryText(weatherSymbol(period.condition), 19f).apply { gravity = Gravity.CENTER }, LayoutParams(dp(30), LayoutParams.WRAP_CONTENT))
            addView(secondaryText(period.low?.let { "${format(it)}$unit" } ?: "—").apply {
                textSize = 14f
                gravity = Gravity.END
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(primaryText(period.high?.let { "${format(it)}$unit" } ?: "—", 13f).apply {
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.END
            }, LayoutParams(dp(38), LayoutParams.WRAP_CONTENT))
        }

    private fun hourlyForecastCard(
        weather: EntityState,
        hourly: List<ForecastPeriod>,
        unit: String,
    ): View = LinearLayout(context).apply {
        orientation = VERTICAL
        background = cardBackground(CARD, CARD_EDGE, 19)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        addView(secondaryText(weather.attributes.optString("forecast_summary").ifBlank {
            "${weather.state.replace('-', ' ').replaceFirstChar { it.uppercase() }} conditions continue."
        }).apply {
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(LinearLayout(context).apply {
            hourly.forEach { period ->
                addView(LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    addView(secondaryText(period.label).apply { textSize = 9f; gravity = Gravity.CENTER })
                    addView(primaryText(weatherSymbol(period.condition), 18f).apply { gravity = Gravity.CENTER })
                    addView(primaryText(period.high?.let { "${format(it)}$unit" } ?: "—", 13f).apply {
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                    })
                }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })
    }

    private fun forecastPeriods(weather: EntityState, attribute: String): List<ForecastPeriod> {
        val values = weather.attributes.optJSONArray(attribute) ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                add(ForecastPeriod(
                    label = item.optString("label").ifBlank {
                        forecastLabel(item.optString("datetime"), index, attribute == "hourly_forecast")
                    },
                    condition = item.optString("condition", weather.state),
                    high = item.optDouble("temperature", Double.NaN).takeUnless(Double::isNaN),
                    low = item.optDouble("templow", Double.NaN).takeUnless(Double::isNaN),
                ))
            }
        }
    }

    private fun forecastLabel(datetime: String, index: Int, hourly: Boolean): String = runCatching {
        val value = OffsetDateTime.parse(datetime)
        if (hourly) {
            if (index == 0) "Now" else value.format(DateTimeFormatter.ofPattern("HH", Locale.getDefault()))
        } else if (index == 0) "Today" else value.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    }.getOrElse { if (index == 0) if (hourly) "Now" else "Today" else "+$index" }

    private data class ForecastPeriod(
        val label: String,
        val condition: String,
        val high: Double?,
        val low: Double?,
    )

    private fun controlsPage(page: DashboardPage): View {
        val configured = page.widgets.mapNotNull { widget ->
            widget.entityId?.let(states::get)?.let { widget to it }
        }
        val controls = if (configured.isNotEmpty()) configured
        else states.values.filter { it.domain in CONTROL_DOMAINS }.take(4).map { null to it }
        if (controls.isEmpty()) return emptyPage(page.title, "No supported controls found")

        return verticalPage(page.title).apply {
            val rows = controls.chunked(2)
            val dense = controls.size > 2
            rows.forEach { rowItems ->
                addView(
                    LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        rowItems.forEach { (widget, entity) ->
                            addView(
                                boundEntityView(entity) { deviceControlCard(states[entity.entityId] ?: entity, widget, dense) },
                                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                                    setMargins(dp(4), dp(4), dp(4), dp(4))
                                },
                            )
                        }
                    },
                    LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
                )
            }
        }
    }

    private fun deviceControlCard(entity: EntityState, widget: DashboardWidget? = null, dense: Boolean = false): View {
        val active = entity.state in setOf("on", "open", "opening")
        val fanHasSpeed = entity.domain == "fan" && widget?.showFanSpeed == true && entity.attributes.optInt("supported_features", 0) and 1 != 0
        val complex = when (entity.domain) {
            "light" -> entity.numberAttribute("brightness") != null
            "fan" -> fanHasSpeed
            "cover" -> true
            else -> false
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cardBackground(if (active) ACCENT_WASH else CARD, if (active) PanelTheme.accentWash else CARD_EDGE, 20)
            addView(deviceCardHeader(entity, active, widget, dense, showPower = entity.domain != "cover"))
            if (dense) addView(denseControlIdentity(widget?.label ?: entity.friendlyName, entity, widget))
            when (entity.domain) {
                "light" -> if (entity.numberAttribute("brightness") != null) {
                    addView(dimmerBody(entity), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
                } else addView(binaryBody(entity, dense), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
                "fan" -> if (fanHasSpeed) addView(fanBody(entity), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
                    else addView(binaryBody(entity, dense), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
                "cover" -> addView(coverBody(entity, widget), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
                else -> addView(binaryBody(entity, dense), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            }
            if (entity.domain != "cover") addView(controlFooter(entity, widget))
            if (widget?.cardTap ?: !complex) {
                setOnClickListener { toggleEntity(entity) }
                isClickable = online
            }
            alpha = if (online) 1f else .55f
        }
    }

    private fun deviceCardHeader(entity: EntityState, active: Boolean, widget: DashboardWidget?, dense: Boolean, showPower: Boolean): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ControlIconView(context, controlIcon(entity, widget?.icon ?: "auto"), if (active) ACCENT_DARK else PanelTheme.ink), LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(5) })
                if (!dense) addView(primaryText(widget?.label ?: entity.friendlyName, 15f).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) else addView(View(context), LayoutParams(0, 1, 1f))
                if (showPower) addView(Button(context).apply {
                    text = if (active) "ON" else "OFF"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(5), 0, dp(5), 0)
                    setTextColor(if (active) Color.WHITE else MUTED)
                    background = PanelTheme.pill(context, if (active) ACCENT else PanelTheme.panel)
                    isEnabled = online
                    setOnClickListener { toggleEntity(entity) }
                }, LayoutParams(dp(62), dp(42)))
            })
            if (!dense) addView(secondaryText(controlTypeLabel(entity, widget)).apply {
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            })
        }

    private fun denseControlIdentity(name: String, entity: EntityState, widget: DashboardWidget?): View = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(0, dp(5), 0, dp(2))
        addView(primaryText(name, if (name.length > 22) 12f else 14f).apply {
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(34)))
        addView(secondaryText(controlTypeLabel(entity, widget)).apply {
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
    }

    private fun controlTypeLabel(entity: EntityState, widget: DashboardWidget?): String =
        if (entity.domain == "fan" && widget?.showFanSpeed != true) "Fan" else deviceTypeLabel(entity)

    private fun controlIcon(entity: EntityState, configured: String): String {
        if (configured != "auto") return configured
        val mdi = entity.attributes.optString("icon").removePrefix("mdi:")
        val alias = mapOf(
            "lightbulb" to "light", "ceiling-light" to "ceiling-light", "floor-lamp" to "floor-lamp",
            "wall-sconce" to "wall-light", "led-strip-variant" to "led-strip", "spotlight" to "spotlight",
            "fan" to "fan", "ceiling-fan" to "ceiling-fan", "hvac" to "ventilation",
            "power" to "power", "toggle-switch" to "switch", "power-plug" to "plug", "power-socket" to "socket",
            "curtains" to "curtains", "blinds" to "blinds", "window-shutter" to "shutter", "garage" to "garage",
            "radiator" to "radiator", "air-conditioner" to "air-conditioner", "fireplace" to "fireplace",
            "lock" to "lock", "gate" to "gate", "pump" to "pump", "robot-vacuum" to "vacuum", "speaker" to "speaker",
        )[mdi]
        if (alias != null) return alias
        return when (entity.domain) {
            "fan" -> "fan"
            "cover" -> "curtains"
            "switch", "input_boolean" -> "power"
            else -> "light"
        }
    }

    private fun binaryBody(entity: EntityState, dense: Boolean = false): View =
        LinearLayout(context).apply {
            gravity = Gravity.BOTTOM
            if (!dense) addView(primaryText(entity.state.replace('_', ' ').replaceFirstChar { it.uppercase() }, 22f).apply {
                    typeface = Typeface.DEFAULT_BOLD
                })
        }

    private fun dimmerBody(entity: EntityState): View {
        val percent = ((entity.numberAttribute("brightness") ?: 0.0) / 255.0 * 100.0).roundToInt()
        return sliderBody("Brightness", percent) { value ->
            callService("light", "turn_on", entity.entityId, JSONObject().put("brightness_pct", value))
        }
    }

    private fun fanBody(entity: EntityState): View = LinearLayout(context).apply {
        orientation = VERTICAL
        val percent = entity.numberAttribute("percentage")?.roundToInt() ?: 0
        gravity = Gravity.BOTTOM
        addView(primaryText("Speed · $percent%", 17f).apply { typeface = Typeface.DEFAULT_BOLD })
        addView(modalAction("Adjust speed") { showFanSpeedDialog(entity) }, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })
    }

    private fun coverBody(entity: EntityState, widget: DashboardWidget?): View = LinearLayout(context).apply {
        orientation = VERTICAL
        val position = entity.numberAttribute("current_position")?.roundToInt()
        gravity = Gravity.BOTTOM
        addView(primaryText(position?.let { "Position · $it%" } ?: entity.state.replaceFirstChar { it.uppercase() }, 17f).apply { typeface = Typeface.DEFAULT_BOLD })
        addView(LinearLayout(context).apply {
            addView(modalAction("Control") { showCoverDialog(entity, widget) }, LayoutParams(0, dp(42), 1f).apply {
                if (widget?.showSchedule != false) rightMargin = dp(3)
            })
            if (widget?.showSchedule != false) addView(scheduleAction(entity, widget), LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(3) })
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })
    }

    private fun controlFooter(entity: EntityState, widget: DashboardWidget?): View = LinearLayout(context).apply {
        val timer = entity.domain in TIMER_DOMAINS && widget?.showTimer != false
        val schedule = widget?.showSchedule != false
        if (timer) addView(timerAction(entity, widget?.timerPresets ?: listOf(5, 15, 30, 60)), LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(3) })
        if (schedule) addView(scheduleAction(entity, widget), LayoutParams(0, dp(42), 1f).apply { if (timer) leftMargin = dp(3) })
    }

    private fun scheduleAction(entity: EntityState, widget: DashboardWidget?): View = modalAction(
        schedules.filter { it.entityId == entity.entityId }.let { values -> if (values.isEmpty()) "Schedule" else "Schedules · ${values.size}" }
    ) { showScheduleListDialog(entity, widget) }

    private fun showScheduleListDialog(entity: EntityState, widget: DashboardWidget?) {
        val values = schedules.filter { it.entityId == entity.entityId }.sortedBy { it.time }
        if (values.isEmpty()) {
            showScheduleDialog(entity, widget, null)
            return
        }
        showControlDialog("Schedules", entity.friendlyName) { dialog ->
            values.forEach { schedule ->
                addView(modalAction("${schedule.time}  ·  ${schedule.action.replace('_', ' ')}") {
                    dialog.dismiss()
                    showScheduleDialog(entity, widget, schedule)
                }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(5) })
            }
            addView(modalAction("＋ Add schedule") {
                dialog.dismiss()
                showScheduleDialog(entity, widget, null)
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(4) })
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

    private fun sliderBody(label: String, value: Int, onChanged: (Int) -> Unit): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(secondaryText(label).apply { textSize = 10f }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(primaryText("$value%", 18f).apply { typeface = Typeface.DEFAULT_BOLD })
            })
            addView(deviceSeekBar(value, onChanged), LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        }

    private fun deviceSeekBar(value: Int, onChanged: (Int) -> Unit): View =
        PanelSliderView(context, value, onChanged).apply { isEnabled = online }

    private fun compactAction(label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 10f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(MUTED)
            background = cardBackground(PanelTheme.panel, PanelTheme.line, 12)
            isClickable = true
            isFocusable = true
            isEnabled = online
            setOnClickListener { action() }
        }

    private fun timerAction(entity: EntityState, presets: List<Int>): View = TextView(context).apply {
        val activeMinutes = timerMinutes[entity.entityId]?.takeIf { it > 0 }
        text = activeMinutes?.let { "◷  Timer · ${it} min" } ?: "◷  Set timer"
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (activeMinutes != null) Color.WHITE else PanelTheme.ink)
        background = cardBackground(if (activeMinutes != null) ACCENT else PanelTheme.panel, PanelTheme.line, 13)
        isClickable = true
        isFocusable = true
        setOnClickListener { showTimerDialog(entity, presets) }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(5) }
    }

    private fun showFanSpeedDialog(entity: EntityState) {
        val percent = entity.numberAttribute("percentage")?.roundToInt() ?: 0
        showControlDialog("Fan speed", entity.friendlyName) { dialog ->
            addView(primaryText("$percent%", 34f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
            addView(deviceSeekBar(percent) { value ->
                callService("fan", "set_percentage", entity.entityId, JSONObject().put("percentage", value))
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(64)))
            addView(LinearLayout(context).apply {
                listOf(25 to "Low", 50 to "Medium", 100 to "High").forEach { (value, label) ->
                    addView(TextView(context).apply {
                        text = label
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        background = cardBackground(PanelTheme.panel, PanelTheme.line, 15)
                        setTextColor(PanelTheme.ink)
                        setOnClickListener {
                            callService("fan", "set_percentage", entity.entityId, JSONObject().put("percentage", value))
                            dialog.dismiss()
                        }
                    }, LayoutParams(0, dp(54), 1f).apply { setMargins(dp(3), dp(4), dp(3), 0) })
                }
            })
        }
    }

    private fun showCoverDialog(entity: EntityState, widget: DashboardWidget?) {
        val position = entity.numberAttribute("current_position")?.roundToInt() ?: 0
        val canPosition = entity.attributes.optInt("supported_features", 0) and 4 != 0
        showControlDialog("Curtains", entity.friendlyName) { dialog ->
            if (canPosition) {
                addView(primaryText("Position · $position%", 22f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
                addView(deviceSeekBar(position) { value ->
                    callService("cover", "set_cover_position", entity.entityId, JSONObject().put("position", value))
                }, LayoutParams(LayoutParams.MATCH_PARENT, dp(64)))
            }
            addView(LinearLayout(context).apply {
                listOf("Open" to "open_cover", "Stop" to "stop_cover", "Close" to "close_cover").forEach { (label, service) ->
                    addView(TextView(context).apply {
                        text = label
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        background = cardBackground(PanelTheme.panel, PanelTheme.line, 15)
                        setTextColor(PanelTheme.ink)
                        setOnClickListener {
                            callService("cover", service, entity.entityId, JSONObject())
                            if (service == "stop_cover") {
                                widget?.gradualOpenScript?.let { callService("script", "turn_off", it, JSONObject()) }
                                widget?.gradualCloseScript?.let { callService("script", "turn_off", it, JSONObject()) }
                            }
                            if (service != "stop_cover") dialog.dismiss()
                        }
                    }, LayoutParams(0, dp(58), 1f).apply { setMargins(dp(3), dp(4), dp(3), 0) })
                }
            })
            widget?.gradualOpenScript?.let { script ->
                addView(modalAction("Gradually open") {
                    callService("script", "turn_on", script, JSONObject())
                    dialog.dismiss()
                }, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(8) })
            }
            widget?.gradualCloseScript?.let { script ->
                addView(modalAction("Gradually close") {
                    callService("script", "turn_on", script, JSONObject())
                    dialog.dismiss()
                }, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(8) })
            }
        }
    }

    private fun showControlDialog(title: String, subtitle: String, content: LinearLayout.(Dialog) -> Unit) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = cardBackground(CARD, CARD_EDGE, 24)
            addView(primaryText(title, 24f).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(secondaryText(subtitle).apply { textSize = 13f; setPadding(0, dp(3), 0, dp(14)) })
            content(dialog)
        })
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(.65f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .9f).roundToInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showTimerDialog(entity: EntityState, presets: List<Int>) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = cardBackground(CARD, CARD_EDGE, 24)
            addView(primaryText("Timer", 24f).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(secondaryText(entity.friendlyName).apply { textSize = 13f; setPadding(0, dp(3), 0, dp(14)) })
            presets.distinct().chunked(2).forEach { row ->
                addView(LinearLayout(context).apply {
                    row.forEach { minutes ->
                        val selected = timerMinutes[entity.entityId] == minutes
                        addView(TextView(context).apply {
                            text = "$minutes min"
                            textSize = 17f
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                            setTextColor(if (selected) Color.WHITE else PanelTheme.ink)
                            background = cardBackground(if (selected) ACCENT else PanelTheme.panel, if (selected) ACCENT else PanelTheme.line, 16)
                            setOnClickListener { setTimer(entity, minutes); dialog.dismiss() }
                        }, LayoutParams(0, dp(58), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                    }
                    if (row.size == 1) addView(View(context), LayoutParams(0, dp(58), 1f))
                })
            }
            timerMinutes[entity.entityId]?.takeIf { it > 0 }?.let {
                addView(TextView(context).apply {
                    text = "Cancel timer"
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(210, 74, 63))
                    background = cardBackground(PanelTheme.panel, PanelTheme.line, 15)
                    setOnClickListener { setTimer(entity, 0); dialog.dismiss() }
                }, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(9) })
            }
        })
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(.65f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout((resources.displayMetrics.widthPixels * .86f).roundToInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .86f).roundToInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun setTimer(entity: EntityState, minutes: Int) {
        timerCallbacks.remove(entity.entityId)?.let(::removeCallbacks)
        timerMinutes[entity.entityId] = minutes
        if (minutes > 0) {
            val callback = Runnable {
                callService(entity.domain, "turn_off", entity.entityId, JSONObject())
                timerMinutes[entity.entityId] = 0
                timerCallbacks.remove(entity.entityId)
                scheduleEntityRefresh(entity.entityId)
            }
            timerCallbacks[entity.entityId] = callback
            postDelayed(callback, minutes * 60_000L)
        }
        scheduleEntityRefresh(entity.entityId)
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

    private fun deviceTypeLabel(entity: EntityState): String = when {
        entity.domain == "light" && entity.numberAttribute("brightness") != null -> "Dimmable light"
        entity.domain == "light" -> "Light"
        entity.domain == "fan" -> "Speed · ${entity.numberAttribute("percentage")?.roundToInt() ?: 0}%"
        entity.domain == "cover" -> "Position · ${entity.numberAttribute("current_position")?.roundToInt() ?: 0}%"
        else -> entity.domain.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun generalPage(page: DashboardPage): View = verticalPage(page.title).apply {
        val compact = page.widgets.filter { it.type in setOf("entity_button", "sensor") }
        compact.chunked(2).forEach { row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEach { widget ->
                    val entity = resolveEntity(widget)
                    val view = entity?.let { initial ->
                        boundEntityView(initial) {
                            val current = states[initial.entityId] ?: initial
                            if (widget.type == "entity_button") entityButton(current, widget.label)
                            else sensorCard(current, widget.label)
                        }
                    } ?: unavailableCard(widget.label ?: widget.entityId.orEmpty())
                    addView(view, LayoutParams(0, dp(88), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                }
                if (row.size == 1) addView(View(context), LayoutParams(0, dp(88), 1f))
            })
        }
        page.widgets.filterNot { it in compact }.forEach { widget ->
            when (widget.type) {
                "thermostat" -> addView(thermostatPage(widget.label ?: "Thermostat", widget))
                "weather" -> addView(weatherPage(widget.label ?: "Weather", widget))
            }
        }
        if (page.widgets.isEmpty()) addView(secondaryText("This page has no widgets"))
    }

    private fun entityButton(entity: EntityState, label: String? = null): Button =
        Button(context).apply {
            val active = entity.state in setOf("on", "open", "opening")
            text = "${if (active) "●" else "○"}  ${label ?: entity.friendlyName}\n${entity.state.uppercase()}"
            isAllCaps = false
            textSize = 14f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(10), dp(8))
            setTextColor(if (active) Color.WHITE else PanelTheme.ink)
            background = cardBackground(if (active) ACCENT_DARK else CARD, if (active) ACCENT else CARD_EDGE)
            setOnClickListener {
                callService(entity.domain, "toggle", entity.entityId, JSONObject())
            }
            isEnabled = online
            alpha = if (online) 1f else .55f
        }

    private fun sensorCard(entity: EntityState, label: String?): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(10), dp(8))
            background = cardBackground(CARD)
            addView(eyebrow(label ?: entity.friendlyName))
            addView(primaryText(entity.state, 22f).apply { typeface = Typeface.DEFAULT_BOLD })
        }

    private fun unavailableCard(label: String): View = metricCard(label.uppercase(), "Unavailable").apply { alpha = .55f }

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

    private fun createFooter(): View =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
            pageLabel.apply {
                gravity = Gravity.CENTER
                setTextColor(MUTED)
                textSize = 11f
            }
            addView(pageLabel, LayoutParams(dp(86), dp(24)))
        }

    private fun verticalPage(title: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            addView(
                primaryText(title, 17f).apply {
                    setTextColor(PanelTheme.ink)
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = .04f
                    setPadding(dp(2), 0, 0, dp(6))
                    contentDescription = "$title. Long press for administrator controls"
                    setOnLongClickListener {
                        openAdmin()
                        true
                    }
                },
            )
        }

    private fun emptyPage(title: String, message: String): View =
        verticalPage(title).apply {
            addView(
                secondaryText(message).apply { gravity = Gravity.CENTER },
                LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }

    private fun unconfiguredPage(): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(24), dp(30), dp(24))
            addView(eyebrow("NSPanel Companion"))
            addView(primaryText(panelName, 28f).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(10), 0, dp(8))
            })
            addView(secondaryText("Dashboard not configured").apply {
                gravity = Gravity.CENTER
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(PanelTheme.ink)
            })
            addView(secondaryText("Open Home Assistant → NSPanel Companion, select this panel, and create its pages.").apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(10), dp(16), dp(18))
            })
            addView(secondaryText(panelId).apply {
                gravity = Gravity.CENTER
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                contentDescription = "Panel ID $panelId. Long press for administrator controls"
                setOnLongClickListener {
                    openAdmin()
                    true
                }
            })
        }

    private fun primaryText(value: String, size: Float): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(PanelTheme.ink)
        }

    private fun secondaryText(value: String): TextView =
        TextView(context).apply {
            text = value
            textSize = 14f
            setTextColor(MUTED)
        }

    private fun eyebrow(value: String): TextView = secondaryText(value.uppercase()).apply {
        textSize = 11f
        letterSpacing = .1f
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun metricBlock(label: String, value: String, size: Float): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(eyebrow(label))
            addView(primaryText(value, size).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            })
        }

    private fun metricCard(label: String, value: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = cardBackground(CARD)
            addView(eyebrow(label))
            addView(primaryText(value, 20f).apply { typeface = Typeface.DEFAULT_BOLD })
        }

    private fun statusPill(label: String, active: Boolean): TextView =
        TextView(context).apply {
            text = "${if (active) "●" else "○"}  ${label.uppercase()}"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (active) ACCENT else MUTED)
            background = cardBackground(if (active) ACCENT_WASH else CARD)
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }

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

    private fun navButton(label: String, action: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 22f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            background = cardBackground(CARD)
            setTextColor(PanelTheme.ink)
            setOnClickListener { action() }
        }

    private fun weatherSymbol(condition: String): String = when (condition.lowercase()) {
        "sunny", "clear-night" -> if (condition == "clear-night") "☾" else "☀"
        "cloudy", "partlycloudy" -> "☁"
        "rainy", "pouring", "lightning-rainy" -> "☂"
        "snowy", "snowy-rainy" -> "❄"
        "fog" -> "≋"
        else -> "◌"
    }

    private fun weatherAge(entityId: String): String {
        val elapsedMinutes = ((System.currentTimeMillis() - (weatherUpdatedAt[entityId] ?: 0L)) / 60_000L).coerceAtLeast(0)
        return when {
            elapsedMinutes < 1 -> "just now"
            elapsedMinutes < 60 -> "${elapsedMinutes}m ago"
            elapsedMinutes < 1_440 -> "${elapsedMinutes / 60}h ago"
            else -> "${elapsedMinutes / 1_440}d ago"
        }
    }

    private fun cardBackground(
        color: Int,
        stroke: Int = CARD_EDGE,
        radius: Int = 18,
    ): GradientDrawable = PanelTheme.rounded(context, color, radius, stroke)

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 24f
            isAllCaps = false
            setOnClickListener { action() }
        }

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
