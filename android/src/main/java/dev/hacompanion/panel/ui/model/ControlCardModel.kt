package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import kotlin.math.roundToInt

/** Which body a control card draws below its header. */
enum class ControlBody { BINARY, DIMMER, FAN, COVER }

/** Everything a control card draws, with no entity or view left in it. */
data class ControlCardModel(
    val entityId: String,
    val name: String,
    val typeLabel: String,
    val icon: String,
    val active: Boolean,
    val body: ControlBody,
    val bodyText: String,
    val brightnessPercent: Int,
    val showPower: Boolean,
    val showTimer: Boolean,
    val showSchedule: Boolean,
    val cardTap: Boolean,
    val dense: Boolean,

    /**
     * The proportion of the tile to fill, or null when the device has no
     * level at all — an on/off light is not a dimmer set to 100.
     */
    val level: Int?,
    /**
     * The level as text, or null when the fill alone says it.
     *
     * A switch fills the tile completely when it is on, but showing "100%"
     * would claim a level it has no way to set.
     */
    val levelText: String?,
    /**
     * Whether this light can be tuned, and what it currently sits at.
     *
     * A light that reports no colour_temp_kelvin has no band; one that has
     * gone unavailable keeps reporting the attribute it last had, and a
     * band that cannot be moved is worse than no band.
     */
    val hasColourTemperature: Boolean,
    val colourTemperature: String?,
    /** Covers get ▲ ■ ▼ where everything else has room for a subtitle. */
    val actionStrip: Boolean,
    /**
     * False when Home Assistant has nothing to report about the device.
     *
     * The tile and its name stay so the grid never reflows around a device
     * that went away, but everything that would claim a state goes.
     */
    val available: Boolean,
    /** A cover in travel, the one state where stop is the lit cell. */
    val moving: Boolean,
    /** The raw state, for the one case that needs the direction of travel. */
    val state: String,
    val subtitle: String?,
    /** Scene, script, automation: a thing you run, not a device you switch. */
    val runnable: Boolean,
)

/** The domains whose tile is a button: one tap, one run, no state to hold. */
val RUNNABLE_DOMAINS = setOf("scene", "script", "automation")

/**
 * The service a tap calls, as domain to service.
 *
 * `toggle` is not universal. A scene has no such service at all — the call
 * went out over the websocket and was dropped, which looks exactly like a
 * tile that does nothing. An automation has one, but it enables and disables
 * the automation rather than running it, which is not what tapping a tile on
 * a wall means.
 */
fun tapService(entity: EntityState): Pair<String, String> = when (entity.domain) {
    "cover" -> "cover" to if (entity.state in setOf("open", "opening")) "close_cover" else "open_cover"
    "scene", "script" -> entity.domain to "turn_on"
    "automation" -> "automation" to "trigger"
    else -> entity.domain to "toggle"
}

// A cover is in here now. The admin hid its timer because a fourth footer
// button did not fit; the footer is gone and the timer lives in the sheet.
private val TIMER_DOMAINS = setOf("light", "switch", "fan", "cover", "input_boolean")

// Home Assistant's SET_SPEED bit. A fan without it has no speed to adjust,
// however the widget is configured.
private const val FAN_SET_SPEED = 1

private val ICON_ALIASES = mapOf(
    "lightbulb" to "light", "ceiling-light" to "ceiling-light", "floor-lamp" to "floor-lamp",
    "wall-sconce" to "wall-light", "led-strip-variant" to "led-strip", "spotlight" to "spotlight",
    "fan" to "fan", "ceiling-fan" to "ceiling-fan", "hvac" to "ventilation",
    "power" to "power", "toggle-switch" to "switch", "power-plug" to "plug", "power-socket" to "socket",
    "curtains" to "curtains", "blinds" to "blinds", "window-shutter" to "shutter", "garage" to "garage",
    "radiator" to "radiator", "air-conditioner" to "air-conditioner", "fireplace" to "fireplace",
    "lock" to "lock", "gate" to "gate", "pump" to "pump", "robot-vacuum" to "vacuum", "speaker" to "speaker",
)

fun controlIcon(entity: EntityState, configured: String): String {
    if (configured != "auto") return configured
    ICON_ALIASES[entity.attributes.optString("icon").removePrefix("mdi:")]?.let { return it }
    return when (entity.domain) {
        "fan" -> "fan"
        "cover" -> "curtains"
        "switch", "input_boolean" -> "power"
        "scene", "script", "automation" -> entity.domain
        else -> "light"
    }
}

fun deviceTypeLabel(entity: EntityState): String = when {
    entity.domain == "light" && entity.numberAttribute("brightness") != null -> "Dimmable light"
    entity.domain == "light" -> "Light"
    entity.domain == "fan" -> "Speed · ${entity.numberAttribute("percentage")?.roundToInt() ?: 0}%"
    entity.domain == "cover" -> "Position · ${entity.numberAttribute("current_position")?.roundToInt() ?: 0}%"
    else -> entity.domain.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun controlCard(
    entity: EntityState,
    widget: DashboardWidget?,
    dense: Boolean,
): ControlCardModel {
    val runnable = entity.domain in RUNNABLE_DOMAINS
    val fanHasSpeed = entity.domain == "fan" &&
        widget?.showFanSpeed == true &&
        entity.attributes.optInt("supported_features", 0) and FAN_SET_SPEED != 0
    val body = when (entity.domain) {
        "light" -> if (entity.numberAttribute("brightness") != null) ControlBody.DIMMER else ControlBody.BINARY
        "fan" -> if (fanHasSpeed) ControlBody.FAN else ControlBody.BINARY
        "cover" -> ControlBody.COVER
        else -> ControlBody.BINARY
    }
    val position = entity.numberAttribute("current_position")?.roundToInt()
    // Neither state carries a value, so neither can be drawn as one.
    val available = entity.state !in setOf("unavailable", "unknown")
    val moving = entity.state in setOf("opening", "closing")
    // A cover is as open as its position says, whichever way it is heading.
    // Reading state alone blanked the fill for a whole descent and then
    // snapped it back at rest.
    val on = available && !runnable && when (entity.domain) {
        "cover" -> (position ?: if (entity.state == "closed") 0 else 100) > 0
        else -> entity.state in setOf("on", "open", "opening")
    }

    // What proportion of the tile is filled. A device with no level of its
    // own is all or nothing, which is still a fill — it just cannot be
    // anywhere in between.
    val level: Int? = if (!available) null else when (entity.domain) {
        "light" -> entity.numberAttribute("brightness")
            ?.let { if (on) (it / 255.0 * 100.0).roundToInt() else 0 }
        "cover" -> position ?: if (on) 100 else 0
        "fan" -> entity.numberAttribute("percentage")?.roundToInt() ?: if (on) 100 else 0
        else -> if (on) 100 else 0
    }
    val kelvin = entity.numberAttribute("color_temp_kelvin")
        ?.takeIf { available && entity.domain == "light" && it > 0 }
    // Shown only where the number means something the tile can set.
    val levelText = level
        ?.takeIf { on && body != ControlBody.BINARY }
        ?.let { "$it%" }

    return ControlCardModel(
        entityId = entity.entityId,
        name = widget?.label ?: entity.friendlyName,
        typeLabel = if (entity.domain == "fan" && widget?.showFanSpeed != true) "Fan" else deviceTypeLabel(entity),
        icon = controlIcon(entity, widget?.icon ?: "auto"),
        active = on,
        body = body,
        bodyText = when (body) {
            ControlBody.FAN -> "Speed · ${entity.numberAttribute("percentage")?.roundToInt() ?: 0}%"
            ControlBody.COVER -> position?.let { "Position · $it%" }
                ?: entity.state.replaceFirstChar { it.uppercase() }
            else -> entity.state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        },
        brightnessPercent = ((entity.numberAttribute("brightness") ?: 0.0) / 255.0 * 100.0).roundToInt(),
        showPower = entity.domain != "cover",
        showTimer = entity.domain in TIMER_DOMAINS && widget?.showTimer != false,
        // Home Assistant schedules turn things on and off. There is no "off"
        // to schedule for a scene, and the sheet's rows would call services
        // that do not exist.
        showSchedule = !runnable && widget?.showSchedule != false,
        // The whole tile is the toggle. That was not true of a card, which
        // carried its own controls and could not also be one; a tile puts the
        // level in a sheet precisely so the surface is free to toggle.
        // A cover is the exception: it has a position, not an on and an off.
        // An unavailable device cannot be toggled, whatever the layout says.
        // Its long press survives: that sheet is the only thing that can say
        // why the tile has gone quiet.
        // A button is a button however the widget was configured: there is
        // nothing else a tap on it could mean.
        cardTap = available && (if (runnable) true else widget?.cardTap ?: (entity.domain != "cover")),
        dense = dense,
        level = level,
        levelText = levelText,
        hasColourTemperature = kelvin != null,
        colourTemperature = kelvin?.let { "${it.roundToInt()}K" },
        actionStrip = entity.domain == "cover",
        // A cover says its position in the fill and its actions in the strip,
        // so it has neither room nor need for a line of prose.
        available = available,
        moving = moving,
        state = entity.state,
        subtitle = when {
            !available -> "Unavailable"
            // A scene's state is the time it was last applied, which is not a
            // reading and read as gibberish under the name.
            runnable -> null
            entity.domain == "cover" -> null
            levelText != null -> null
            else -> entity.state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        },
        runnable = runnable,
    )
}
