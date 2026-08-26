package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCellsTest {
    private fun entity(id: String, state: String, attributes: String = "{}") =
        EntityState(id, state, JSONObject(attributes))

    private val entities = mapOf(
        "light.a" to entity("light.a", "on"),
        "sensor.bedroom" to entity("sensor.bedroom", "21.7"),
        "switch.b" to entity("switch.b", "off"),
    )

    private fun widget(type: String, id: String) = DashboardWidget(type = type, entityId = id)

    @Test
    fun aControlIsDrawnAsAControlWhateverElseSharesThePage() {
        // A sensor on the page used to demote every control on it to a plain
        // tile, losing the timer, schedule and slider with nothing to say so.
        val cells = pageCells(
            listOf(widget("entity_button", "light.a"), widget("sensor", "sensor.bedroom")),
            entities,
        )
        assertTrue(cells[0] is PageCell.Control)
        assertTrue(cells[1] is PageCell.Reading)
    }

    @Test
    fun aPageOfOnlyControlsIsUnchanged() {
        val cells = pageCells(
            listOf(widget("entity_button", "light.a"), widget("controls", "switch.b")),
            entities,
        )
        assertTrue(cells.all { it is PageCell.Control })
    }

    @Test
    fun aPageOfOnlyReadingsIsUnchanged() {
        val cells = pageCells(listOf(widget("sensor", "sensor.bedroom")), entities)
        assertTrue(cells.single() is PageCell.Reading)
    }

    @Test
    fun aWidgetWithNoEntityIsReportedRatherThanDropped() {
        // The raw id, as the view build showed it: the entity is not there to
        // supply a friendly name, and inventing one hides which id is wrong.
        val cells = pageCells(listOf(widget("entity_button", "light.missing")), entities)
        assertEquals("light.missing", (cells.single() as PageCell.Missing).label)
    }

    @Test
    fun theControlsAreDenseOnlyWhenThereAreEnoughOfThem() {
        val two = pageCells(
            listOf(widget("entity_button", "light.a"), widget("controls", "switch.b")),
            entities,
        )
        assertTrue(two.filterIsInstance<PageCell.Control>().none { it.card.dense })

        val three = pageCells(
            listOf(
                widget("entity_button", "light.a"),
                widget("controls", "switch.b"),
                widget("entity_button", "light.a"),
            ),
            entities,
        )
        assertTrue(three.filterIsInstance<PageCell.Control>().all { it.card.dense })
    }

    @Test
    fun densityCountsControlsNotReadings() {
        // Three readings beside one control must not squeeze the control.
        val cells = pageCells(
            listOf(
                widget("entity_button", "light.a"),
                widget("sensor", "sensor.bedroom"),
                widget("sensor", "sensor.bedroom"),
                widget("sensor", "sensor.bedroom"),
            ),
            entities,
        )
        assertTrue(cells.filterIsInstance<PageCell.Control>().none { it.card.dense })
    }

    @Test
    fun anUnconfiguredControlsPageFallsBackToWhateverTheHouseHas() {
        val cells = pageCells(listOf(DashboardWidget(type = "controls")), entities)
        val controls = cells.filterIsInstance<PageCell.Control>()
        assertEquals(listOf("light.a", "switch.b"), controls.map { it.card.entityId })
    }
}
