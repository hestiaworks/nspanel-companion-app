package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState

/** One position on a page, and how it is drawn. */
sealed interface PageCell {
    data class Control(val card: ControlCardModel) : PageCell
    data class Reading(val tile: SensorTile) : PageCell
    data class Missing(val label: String) : PageCell
}

private val CONTROL_WIDGETS = setOf("controls", "entity_button")
private val CONTROL_DOMAINS = setOf("light", "switch", "input_boolean", "fan", "cover")

/**
 * Decide how each widget on a page is drawn.
 *
 * Presentation follows the widget's own type. It used to follow the page's
 * composition: a page of nothing but controls got the full control cards, and
 * a single sensor added beside them silently demoted every control to a plain
 * tile, taking its timer, schedule and slider with it. Nothing in the editor
 * said so, and the widget responsible was not the one that changed.
 */
fun pageCells(
    widgets: List<DashboardWidget>,
    entities: Map<String, EntityState>,
): List<PageCell> {
    val resolved = widgets.map { widget -> widget to resolveEntity(entities, widget) }

    // A controls widget with nothing chosen shows whatever the house has, as
    // it did when this was a page type of its own.
    val unconfigured = resolved.singleOrNull()
        ?.takeIf { (widget, entity) -> widget.type == "controls" && entity == null && widget.entityId == null }
    if (unconfigured != null) {
        val found = entities.values.filter { it.domain in CONTROL_DOMAINS }.take(4)
        return found.map { PageCell.Control(controlCard(it, null, dense = found.size > 2)) }
    }

    // Density is about how much room the controls have, so only they count.
    val controlCount = resolved.count { (widget, entity) ->
        entity != null && widget.type in CONTROL_WIDGETS
    }
    return resolved.map { (widget, entity) ->
        when {
            entity == null -> PageCell.Missing(widget.label ?: widget.entityId.orEmpty())
            widget.type in CONTROL_WIDGETS ->
                PageCell.Control(controlCard(entity, widget, dense = controlCount > 2))
            else -> PageCell.Reading(sensorTile(entity, widget.label))
        }
    }
}
