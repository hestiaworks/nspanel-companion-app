package dev.hacompanion.panel.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.hacompanion.panel.MicUsageTracker
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.hacompanion.panel.CameraPageView
import dev.hacompanion.panel.DashboardLayout
import dev.hacompanion.panel.DashboardPage
import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import dev.hacompanion.panel.ui.components.PanelText
import dev.hacompanion.panel.ui.model.pageCells
import dev.hacompanion.panel.ui.model.CONTROL_WIDGETS
import dev.hacompanion.panel.ui.model.HistorySeries
import dev.hacompanion.panel.ui.model.controlCard
import dev.hacompanion.panel.ui.pages.HistoryPage
import dev.hacompanion.panel.ui.pages.LightPage
import dev.hacompanion.panel.ui.model.resolveEntity
import dev.hacompanion.panel.ui.model.thermostatModel
import dev.hacompanion.panel.ui.model.weatherModel
import dev.hacompanion.panel.ui.pages.ControlActions
import dev.hacompanion.panel.ui.pages.PageGrid
import dev.hacompanion.panel.ui.pages.ThermostatPage
import dev.hacompanion.panel.ui.slab.HeaderRow
import dev.hacompanion.panel.ui.pages.WeatherPage
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.model.panelTime
import dev.hacompanion.panel.ui.slab.StatusStrip
import dev.hacompanion.panel.ui.theme.PanelThemeProvider
import java.util.TimeZone

/**
 * The dashboard's snapshot-backed inputs. Writing one of these is the whole
 * of a "render": the page is recomposed, not rebuilt, so swiping no longer
 * inflates a view tree.
 */
class DashboardUiState {
    var layout by mutableStateOf(DashboardLayout.default())
    var pageIndex by mutableStateOf(0)
    var online by mutableStateOf(false)
    var configured by mutableStateOf(true)
    var panelName by mutableStateOf("NSPanel Pro")
    var panelId by mutableStateOf("")
    var dark by mutableStateOf(false)

    /** Home Assistant's clock, which the panel's own is not trusted over. */
    var serverTimeMs by mutableStateOf(System.currentTimeMillis())
    var syncedAtElapsedMs by mutableStateOf(0L)
    var timezone: TimeZone by mutableStateOf(TimeZone.getDefault())
    var showClock by mutableStateOf(true)
    var showMic by mutableStateOf(true)
    var micLingerSeconds by mutableStateOf(15)

    /**
     * Bumped when schedules or timers change. Neither lives in the entity map,
     * so nothing else would tell the affected labels to recompose.
     */
    var sidecarRevision by mutableStateOf(0)

    /**
     * The spans a history page has been handed, by entity.
     *
     * A snapshot map, so a series arriving recomposes the page that asked
     * for it. Home Assistant sends what the layout configures on connect;
     * anything else is because someone pressed a range button.
     */
    val history = androidx.compose.runtime.mutableStateMapOf<String, HistorySeries>()

    /** Which span the panel is showing, remembered across pages. */
    val historyRange = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /**
     * Bumped once a second while a timer runs, so only the corner marks that
     * read it recompose rather than the whole page.
     */
    var timerTick by mutableStateOf(0)

    /**
     * Which setpoint the rail adjusts, per entity.
     *
     * Snapshot state, so choosing one moves the fill on the next frame. Held
     * in a plain map it moved on the next entity update instead, which read
     * as the rail adjusting whichever setpoint you had selected before.
     */
    val selectedTargets = mutableStateMapOf<String, String>()
}

/** Everything the dashboard's pages call back into. */
interface DashboardActions : ControlActions {
    fun openAdmin()

    /** A stream URL warmed while this camera was one swipe away, if any. */
    fun claimWarmedStream(widget: DashboardWidget): String?

    /** Ask Home Assistant for a span of an entity's past. */
    fun requestHistory(entityId: String, range: String)

    /** The span a history page was last left on, across restarts. */
    fun rememberedHistoryRange(entityId: String, fallback: String): String
    fun selectedClimateTarget(entityId: String): String
    fun selectClimateTarget(entityId: String, target: String)
    fun stepThermostat(entityId: String, up: Boolean)
    fun setHvacMode(entityId: String, mode: String)

    /** The sheet behind the mode row's MORE slot. */
    fun openMoreModes(entityId: String)

    /** The sheet behind a fan speed or swing cell. `key` is the attribute. */
    fun openClimateAttribute(entityId: String, key: String)
}

@Composable
fun DashboardRoot(
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    PanelThemeProvider(ui.dark) {
        Column(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
            // The strip is on every page, which is what makes it the place
            // for administration now that a grid page has no header to press.
            if (ui.showClock || ui.showMic) {
                PanelStatusStrip(ui, actions::openAdmin)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            if (!ui.configured) {
                UnconfiguredPage(ui.panelName, ui.panelId, actions::openAdmin)
            } else {
                val pages = ui.layout.pages
                val page = pages.getOrNull(ui.pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0)))
                if (page == null) {
                    EmptyPage("Dashboard", "This dashboard has no pages")
                } else {
                    // Keyed so moving between pages replaces the subtree
                    // outright rather than trying to reuse a different page's.
                    key(page.id) { PageContent(page, ui, entities, actions) }
                }
            }
            }
        }
    }
}

/**
 * The clock ticks in composition rather than from a view's handler, so the
 * whole strip is one recomposition a second and nothing is laid out twice.
 */
@Composable
private fun PanelStatusStrip(ui: DashboardUiState, onLongPress: () -> Unit) {
    val context = LocalContext.current
    var elapsed by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var micActive by remember { mutableStateOf(false) }
    LaunchedEffect(ui.micLingerSeconds) {
        while (true) {
            elapsed = SystemClock.elapsedRealtime()
            micActive = MicUsageTracker.recentlyUsed(context, ui.micLingerSeconds)
            delay(1_000)
        }
    }
    StatusStrip(
        time = if (ui.showClock) {
            panelTime(ui.serverTimeMs, ui.syncedAtElapsedMs, elapsed, ui.timezone)
        } else "",
        micActive = if (ui.showMic) micActive else null,
        pages = ui.layout.pages.size,
        current = ui.pageIndex,
        onLongPress = onLongPress,
    )
}

@Composable
private fun PageContent(
    page: DashboardPage,
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    val only = page.widgets.singleOrNull()

    // The camera fills the page itself: it has no title bar to long press.
    if (only?.type == "camera") {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                CameraPageView(
                    context,
                    only,
                    claimWarmed = { actions.claimWarmedStream(only) },
                )
            },
        )
        return
    }

    // A Slab page draws its own header band, so it sits outside the scaffold
    // and carries the long press that opens administration itself.
    if (only?.type == "thermostat") {
        ThermostatBody(only, ui, entities, actions)
        return
    }
    // Weather draws its own bands from the top edge down, so it has no header
    // row: the reading is the page's title.
    if (only?.type == "weather") {
        WeatherBody(only, entities)
        return
    }

    if (only?.type == "history") {
        HistoryBody(only, ui, entities, actions)
        return
    }

    // A light alone on its page gets the page rather than a quarter of it:
    // the same controls a sheet offers, at a size that can be used without
    // aiming. Anything else on the page and it is a tile like the rest.
    if (only != null && only.type in CONTROL_WIDGETS) {
        val entity = resolveEntity(entities, only)
        if (entity?.domain == "light") {
            @Suppress("UNUSED_EXPRESSION") ui.sidecarRevision
            LightPage(controlCard(entity, only, dense = false), ui.online, actions)
            return
        }
    }

    // No header. Every tile says what it is and what it is doing, so a band
    // repeating the page's name is a band of screen spent on nothing — and on
    // a four-tile page it is the 56 px that were making the names clip.
    Box(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
        PageBody(page, ui, entities, actions)
    }
}

/**
 * A history page, which is the one page that asks for its own data.
 *
 * Everything else draws state the panel already holds. A span of the past
 * has to be fetched, and the span last chosen is remembered — so the page
 * asks for it on arrival rather than resetting to the layout's default.
 */
@Composable
private fun HistoryBody(
    widget: DashboardWidget,
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    val entityId = widget.entityId.orEmpty()
    // In-memory first, then what the panel remembers from a previous run,
    // then the layout's default for a page nobody has moved yet.
    val range = ui.historyRange[entityId]
        ?: actions.rememberedHistoryRange(entityId, widget.historyRange)
    // Only the span being asked for is drawn: a series for the previous one
    // is stale the moment a range button is pressed.
    val series = ui.history[entityId]?.takeIf { it.range == range }
    val entity = entities[entityId]

    // Asked for once per span, when the page has nothing for it yet.
    androidx.compose.runtime.LaunchedEffect(entityId, range, ui.online) {
        if (ui.online && entityId.isNotBlank() && series?.range != range) {
            actions.requestHistory(entityId, range)
        }
    }

    HistoryPage(
        name = widget.label?.takeIf(String::isNotBlank)
            ?: entity?.attributes?.optString("friendly_name")?.takeIf(String::isNotBlank)
            ?: entityId,
        kind = entity?.attributes?.optString("device_class").orEmpty(),
        reading = entity?.state?.let { state ->
            state.toDoubleOrNull()?.let { trimReading(it) } ?: state
        } ?: "—",
        series = series,
        range = range,
        online = ui.online,
        onRange = { actions.requestHistory(entityId, it) },
    )
}

private fun trimReading(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)

@Composable
private fun WeatherBody(widget: DashboardWidget, entities: Map<String, EntityState>) {
    // Resolved inside composition, so a weather update recomposes the page.
    val entity = resolveEntity(entities, widget, "weather")
    if (entity == null) {
        PageMessage("No weather entity found")
        return
    }
    WeatherPage(weatherModel(entity, widget.forecastDays, widget.showHourly))
}

@Composable
private fun ThermostatBody(
    widget: DashboardWidget,
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    val climate = resolveEntity(entities, widget, "climate")
    if (climate == null) {
        PageMessage("No climate entity found")
        return
    }
    val selected = actions.selectedClimateTarget(climate.entityId)
    ThermostatPage(
        model = thermostatModel(climate, widget.label),
        selectedTarget = selected,
        online = ui.online,
        onTargetSelected = { actions.selectClimateTarget(climate.entityId, it) },
        onStep = { up -> actions.stepThermostat(climate.entityId, up) },
        onMode = { mode -> actions.setHvacMode(climate.entityId, mode) },
        onLongPressTitle = actions::openAdmin,
        onOpenMore = { actions.openMoreModes(climate.entityId) },
        onOpenAttribute = { key -> actions.openClimateAttribute(climate.entityId, key) },
    )
}

@Composable
private fun PageBody(
    page: DashboardPage,
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    // Read so a schedule or timer change recomposes the labels that show them.
    @Suppress("UNUSED_EXPRESSION") ui.sidecarRevision
    PageGrid(pageCells(page.widgets, entities), ui.online, actions)
}

/** The page title, which carries the long press that opens administration. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageScaffold(title: String, onLongPress: () -> Unit, content: @Composable () -> Unit) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    Column(
        Modifier.fillMaxSize().padding(
            start = space.edge,
            top = space.edge,
            end = space.edge,
            bottom = space.edge,
        ),
    ) {
        PanelText(
            title,
            type.body,
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$title. Long press for administrator controls" }
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(start = space.edge, bottom = space.edge),
            bold = true,
            maxLines = 1,
        )
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun PageMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PanelText(message, LocalPanelType.current.body, muted = true, align = TextAlign.Center)
    }
}

@Composable
private fun EmptyPage(title: String, message: String) {
    PageScaffold(title, {}) { PageMessage(message) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnconfiguredPage(panelName: String, panelId: String, onLongPress: () -> Unit) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    Column(
        Modifier.fillMaxSize().padding(
            horizontal = space.unconfiguredInsetX,
            vertical = space.unconfiguredInsetY,
        ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelText(
            "NSPANEL COMPANION", type.label,
            muted = true, bold = true, align = TextAlign.Center,
            letterSpacing = type.labelTracking,
        )
        PanelText(
            panelName, type.title,
            Modifier.padding(top = space.unconfiguredNameGap, bottom = space.edge),
            bold = true, align = TextAlign.Center,
        )
        PanelText("Dashboard not configured", type.subtitle, bold = true, align = TextAlign.Center)
        PanelText(
            "Open Home Assistant \u2192 NSPanel Companion, select this panel, and create its pages.",
            type.body,
            Modifier.padding(
                start = space.unconfiguredTextInset,
                top = space.unconfiguredNameGap,
                end = space.unconfiguredTextInset,
                bottom = space.unconfiguredBodyGap,
            ),
            muted = true,
            align = TextAlign.Center,
        )
        // The only route to administration before a dashboard exists.
        PanelText(
            panelId, type.label,
            Modifier
                .semantics { contentDescription = "Panel ID $panelId. Long press for administrator controls" }
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
            muted = true,
            align = TextAlign.Center,
            monospace = true,
        )
    }
}

private val CONTROL_DOMAINS = setOf("light", "switch", "input_boolean", "fan", "cover")
