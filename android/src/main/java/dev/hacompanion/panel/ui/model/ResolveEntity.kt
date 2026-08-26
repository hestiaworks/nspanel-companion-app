package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState

/**
 * Find the entity a widget refers to.
 *
 * Pure so a composable can call it while reading state, which is what turns an
 * entity update into a recomposition rather than a rebuilt page.
 *
 * A widget that names an entity which is not present falls back to the domain,
 * matching the behaviour the dashboard has always had: a panel whose entity was
 * renamed still shows something.
 */
fun resolveEntity(
    entities: Map<String, EntityState>,
    widget: DashboardWidget,
    fallbackDomain: String? = null,
): EntityState? =
    widget.entityId?.let(entities::get)
        ?: fallbackDomain?.let { domain -> entities.values.firstOrNull { it.domain == domain } }
