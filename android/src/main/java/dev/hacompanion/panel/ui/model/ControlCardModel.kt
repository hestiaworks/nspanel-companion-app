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
    /** Covers get ▲ ■ ▼ where everything else has room for a subtitle. */
    val actionStrip: Boolean,
    val subtitle: String?,
)

private val TIMER_DOMAINS = setOf("light", "switch", "fan")

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
    val on = entity.state in setOf("on", "open", "opening")

    // What proportion of the tile is filled. A device with no level of its
    // own is all or nothing, which is still a fill — it just cannot be
    // anywhere in between.
    val level: Int? = when (entity.domain) {
        "light" -> entity.numberAttribute("brightness")
            ?.let { if (on) (it / 255.0 * 100.0).roundToInt() else 0 }
        "cover" -> position ?: if (on) 100 else 0
        "fan" -> entity.numberAttribute("percentage")?.roundToInt() ?: if (on) 100 else 0
        else -> if (on) 100 else 0
    }
    // Shown only where the number means something the tile can set.
    val levelText = level
        ?.takeIf { on && body != ControlBody.BINARY }
        ?.let { "$it%" }

    return ControlCardModel(
        entityId = entity.entityId,
        name = widget?.label ?: entity.friendlyName,
        typeLabel = if (entity.domain == "fan" && widget?.showFanSpeed != true) "Fan" else deviceTypeLabel(entity),
        icon = controlIcon(entity, widget?.icon ?: "auto"),
        active = entity.state in setOf("on", "open", "opening"),
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
        showSchedule = widget?.showSchedule != false,
        // A card whose body already has a richer control should not also
        // toggle when tapped anywhere.
        cardTap = widget?.cardTap ?: (body == ControlBody.BINARY),
        dense = dense,
        level = level,
        levelText = levelText,
        actionStrip = entity.domain == "cover",
        // A cover says its position in the fill and its actions in the strip,
        // so it has neither room nor need for a line of prose.
        subtitle = when {
            entity.domain == "cover" -> null
            levelText != null -> null
            else -> entity.state.replace('_', ' ').replaceFirstChar { it.uppercase() }
        },
    )
}
