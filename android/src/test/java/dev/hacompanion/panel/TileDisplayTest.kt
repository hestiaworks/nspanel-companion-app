package dev.hacompanion.panel

import dev.hacompanion.panel.ui.model.ControlBody
import dev.hacompanion.panel.ui.model.controlCard
import dev.hacompanion.panel.ui.model.sentPosition
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two things a control may be told about how to present itself.
 *
 * Both exist because the entity alone does not settle the question: a
 * curtain motor that can only be inverted as a whole reports the percentage
 * the wrong way round for the room it is in, and a light that is off reports
 * no brightness at all, so there is nothing to draw a band from.
 */
class TileDisplayTest {

    private fun cover(position: Int, state: String = "open") = EntityState(
        "cover.curtain", state,
        JSONObject().put("friendly_name", "Curtain").put("current_position", position)
            .put("supported_features", 15),
    )

    @Test
    fun `a curtain reports the percentage its motor believes in`() {
        val plain = controlCard(cover(100), null, dense = false)
        assertEquals(100, plain.level)
        assertTrue(plain.active)
    }

    @Test
    fun `inverting turns the reading round for the room`() {
        // Zigbee2MQTT inverts a motor as a whole: the open and close buttons
        // come out right and the percentage does not. Fully open reads 100%
        // where the room calls it 0% — the curtain is at its rest position.
        val inverted = DashboardWidget(
            type = "entity_button", entityId = "cover.curtain", invertPosition = true,
        )
        val card = controlCard(cover(100), inverted, dense = false)
        assertEquals(0, card.level)
        assertFalse(card.active)
        assertEquals("Position · 0%", card.typeLabel)
    }

    @Test
    fun `a closed curtain reads as fully drawn when inverted`() {
        val inverted = DashboardWidget(
            type = "entity_button", entityId = "cover.curtain", invertPosition = true,
        )
        val card = controlCard(cover(0, state = "closed"), inverted, dense = false)
        assertEquals(100, card.level)
        assertTrue(card.active)
    }

    @Test
    fun `what is sent goes back the way the motor reads it`() {
        // The band hands back what the room sees; the motor is told the other.
        assertEquals(40, sentPosition(60, invert = true))
        assertEquals(60, sentPosition(60, invert = false))
    }

    @Test
    fun `the bars march with the fill, not with the motor`() {
        val plain = controlCard(cover(40, state = "opening"), null, dense = false)
        assertTrue(plain.fillGrowing)
        val inverted = DashboardWidget(
            type = "entity_button", entityId = "cover.curtain", invertPosition = true,
        )
        // Opening empties an inverted band, so the edge travels the other way.
        assertFalse(controlCard(cover(40, state = "opening"), inverted, dense = false).fillGrowing)
        assertTrue(controlCard(cover(40, state = "closing"), inverted, dense = false).fillGrowing)
    }

    private fun light(on: Boolean, brightness: Int?) = EntityState(
        "light.desk", if (on) "on" else "off",
        JSONObject().put("friendly_name", "Desk")
            .put("supported_color_modes", JSONArray(listOf("brightness")))
            .apply { if (brightness != null) put("brightness", brightness) },
    )

    @Test
    fun `a light that is off has no brightness to show`() {
        // Home Assistant drops the attribute entirely, so there is nothing to
        // draw a band from and the sheet offers the two doors instead.
        assertEquals(ControlBody.BINARY, controlCard(light(on = false, brightness = null), null, false).body)
    }

    @Test
    fun `asking for the band keeps it there while the light is off`() {
        val widget = DashboardWidget(
            type = "entity_button", entityId = "light.desk", brightnessWhenOff = true,
        )
        val card = controlCard(light(on = false, brightness = null), widget, dense = false)
        assertEquals(ControlBody.DIMMER, card.body)
        // Off is off: the tile still says so, and the band starts at nothing.
        assertFalse(card.active)
        assertEquals(0, card.brightnessPercent)
        assertNull(card.levelText)
    }

    @Test
    fun `a light with no brightness at all is never given a band`() {
        // A plain switch-like light reports no brightness mode, so the
        // setting has nothing to offer and must not invent one.
        val widget = DashboardWidget(
            type = "entity_button", entityId = "light.desk", brightnessWhenOff = true,
        )
        val plain = EntityState("light.desk", "off", JSONObject().put("friendly_name", "Desk"))
        assertEquals(ControlBody.BINARY, controlCard(plain, widget, dense = false).body)
    }
}
