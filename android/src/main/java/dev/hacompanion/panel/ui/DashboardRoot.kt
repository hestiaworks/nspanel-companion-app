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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import dev.hacompanion.panel.ui.model.controlCard
import dev.hacompanion.panel.ui.model.controlTile
import dev.hacompanion.panel.ui.model.resolveEntity
import dev.hacompanion.panel.ui.model.sensorTile
import dev.hacompanion.panel.ui.model.thermostatModel
import dev.hacompanion.panel.ui.model.weatherModel
import dev.hacompanion.panel.ui.pages.ControlActions
import dev.hacompanion.panel.ui.pages.ControlsPage
import dev.hacompanion.panel.ui.pages.GeneralPage
import dev.hacompanion.panel.ui.pages.PageTile
import dev.hacompanion.panel.ui.pages.ThermostatPage
import dev.hacompanion.panel.ui.pages.WeatherPage
import dev.hacompanion.panel.ui.theme.LocalPanelColors
import dev.hacompanion.panel.ui.theme.LocalPanelSpace
import dev.hacompanion.panel.ui.theme.LocalPanelType
import dev.hacompanion.panel.ui.theme.PanelThemeProvider

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

    /**
     * Bumped when schedules or timers change. Neither lives in the entity map,
     * so nothing else would tell the affected labels to recompose.
     */
    var sidecarRevision by mutableStateOf(0)
}

/** Everything the dashboard's pages call back into. */
interface DashboardActions : ControlActions {
    fun openAdmin()
    fun openCamera(widget: DashboardWidget)
    fun selectedClimateTarget(entityId: String): String
    fun selectClimateTarget(entityId: String, target: String)
    fun stepThermostat(entityId: String, up: Boolean)
    fun setHvacMode(entityId: String, mode: String)
}

@Composable
fun DashboardRoot(
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    PanelThemeProvider(ui.dark) {
        Box(Modifier.fillMaxSize().background(LocalPanelColors.current.canvas)) {
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

@Composable
private fun PageContent(
    page: DashboardPage,
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    val only = page.widgets.singleOrNull()
    val allControls = page.widgets.isNotEmpty() &&
        page.widgets.all { it.type in setOf("controls", "entity_button") }

    // The camera fills the page itself: it has no title bar to long press.
    if (!allControls && only?.type == "camera") {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> CameraPageView(context, only) { actions.openCamera(only) } },
        )
        return
    }

    PageScaffold(page.title, actions::openAdmin) {
        when {
            allControls || only?.type == "controls" -> ControlsBody(page, ui, entities, actions)
            only?.type == "thermostat" -> ThermostatBody(only, ui, entities, actions)
            only?.type == "weather" -> WeatherBody(only, entities)
            else -> GeneralBody(page, ui, entities, actions)
        }
    }
}

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
    )
}

@Composable
private fun ControlsBody(
    page: DashboardPage,
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    val configured = page.widgets.mapNotNull { widget ->
        widget.entityId?.let { id -> entities[id]?.let { widget to it } }
    }
    val chosen = configured.ifEmpty {
        entities.values.filter { it.domain in CONTROL_DOMAINS }.take(4).map { null to it }
    }
    if (chosen.isEmpty()) {
        PageMessage("No supported controls found")
        return
    }
    // Read so a schedule or timer change recomposes the labels that show them.
    @Suppress("UNUSED_EXPRESSION") ui.sidecarRevision
    ControlsPage(
        cards = chosen.map { (widget, entity) -> controlCard(entity, widget, dense = chosen.size > 2) },
        online = ui.online,
        actions = actions,
    )
}

@Composable
private fun GeneralBody(
    page: DashboardPage,
    ui: DashboardUiState,
    entities: Map<String, EntityState>,
    actions: DashboardActions,
) {
    val tiles = page.widgets.map { widget ->
        when (val entity = resolveEntity(entities, widget)) {
            null -> PageTile.Missing(widget.label ?: widget.entityId.orEmpty())
            else ->
                if (widget.type == "entity_button") PageTile.Control(controlTile(entity, widget.label))
                else PageTile.Reading(sensorTile(entity, widget.label))
        }
    }
    GeneralPage(tiles, ui.online, actions::toggle)
}

/** The page title, which carries the long press that opens administration. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageScaffold(title: String, onLongPress: () -> Unit, content: @Composable () -> Unit) {
    val type = LocalPanelType.current
    val space = LocalPanelSpace.current
    Column(
        Modifier.fillMaxSize().padding(
            start = space.pageStart,
            top = space.pageTop,
            end = space.pageStart,
            bottom = space.pageBottom,
        ),
    ) {
        PanelText(
            title,
            type.pageTitle,
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$title. Long press for administrator controls" }
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(start = space.tiny, bottom = space.titleGap),
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
            letterSpacing = type.eyebrowTracking,
        )
        PanelText(
            panelName, type.panelName,
            Modifier.padding(top = space.unconfiguredNameGap, bottom = space.columnGap),
            bold = true, align = TextAlign.Center,
        )
        PanelText("Dashboard not configured", type.headline, bold = true, align = TextAlign.Center)
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
