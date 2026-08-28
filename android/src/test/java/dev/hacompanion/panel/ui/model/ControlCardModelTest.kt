package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCardModelTest {
    private fun entity(id: String, state: String, attributes: String = "{}") =
        EntityState(id, state, JSONObject(attributes))

    private fun widget(entityId: String = "light.a", type: String = "controls") =
        DashboardWidget(type = type, entityId = entityId)

    @Test
    fun aDimmableLightGetsASlider() {
        val card = controlCard(entity("light.a", "on", """{"brightness": 128}"""), null, dense = false)
        assertEquals(ControlBody.DIMMER, card.body)
        assertEquals("Dimmable light", card.typeLabel)
        assertEquals(50, card.brightnessPercent)
    }

    @Test
    fun aPlainLightIsBinary() {
        val card = controlCard(entity("light.a", "on"), null, dense = false)
        assertEquals(ControlBody.BINARY, card.body)
        assertEquals("Light", card.typeLabel)
        assertEquals("On", card.bodyText)
    }

    @Test
    fun aFanShowsSpeedOnlyWhenTheWidgetAsksAndTheDeviceSupportsIt() {
        val speedy = entity("fan.a", "on", """{"percentage": 40, "supported_features": 1}""")
        val asked = widget("fan.a").copy(showFanSpeed = true)
        assertEquals(ControlBody.FAN, controlCard(speedy, asked, dense = false).body)
        assertEquals("Speed · 40%", controlCard(speedy, asked, dense = false).bodyText)
        assertEquals(ControlBody.BINARY, controlCard(speedy, widget("fan.a"), dense = false).body)
    }

    @Test
    fun aFanWithoutSpeedControlIsLabelledPlainly() {
        val card = controlCard(entity("fan.a", "on", """{"percentage": 40}"""), widget("fan.a"), dense = false)
        assertEquals("Fan", card.typeLabel)
    }

    @Test
    fun aFanTheDeviceCannotVaryStaysBinary() {
        val fixed = entity("fan.a", "on", """{"percentage": 100, "supported_features": 8}""")
        val card = controlCard(fixed, widget("fan.a").copy(showFanSpeed = true), dense = false)
        assertEquals(ControlBody.BINARY, card.body)
    }

    @Test
    fun aCoverShowsPositionAndHasNoPowerButton() {
        val card = controlCard(entity("cover.a", "open", """{"current_position": 30}"""), null, dense = false)
        assertEquals(ControlBody.COVER, card.body)
        assertEquals("Position · 30%", card.bodyText)
        assertFalse(card.showPower)
    }

    @Test
    fun aCoverWithoutAPositionFallsBackToItsState() {
        val card = controlCard(entity("cover.a", "closed"), null, dense = false)
        assertEquals("Closed", card.bodyText)
    }

    @Test
    fun anExplicitLabelWinsOverTheFriendlyName() {
        val card = controlCard(entity("light.a", "on"), widget().copy(label = "Reading lamp"), dense = false)
        assertEquals("Reading lamp", card.name)
    }

    @Test
    fun openingCountsAsActive() {
        assertTrue(controlCard(entity("cover.a", "opening"), null, dense = false).active)
        assertTrue(controlCard(entity("light.a", "on"), null, dense = false).active)
        assertFalse(controlCard(entity("light.a", "off"), null, dense = false).active)
    }

    @Test
    fun aConfiguredIconWinsAndOtherwiseTheEntityIconIsMapped() {
        assertEquals("garage", controlCard(entity("light.a", "on"), widget().copy(icon = "garage"), dense = false).icon)
        val known = entity("light.a", "on", """{"icon": "mdi:floor-lamp"}""")
        assertEquals("floor-lamp", controlCard(known, null, dense = false).icon)
    }

    @Test
    fun anUnknownIconFallsBackToTheDomain() {
        val odd = entity("cover.a", "open", """{"icon": "mdi:unheard-of"}""")
        assertEquals("curtains", controlCard(odd, null, dense = false).icon)
        assertEquals("power", controlCard(entity("switch.a", "on"), null, dense = false).icon)
    }

    @Test
    fun tappingTheCardTogglesOnlyWhenThereIsNoRicherControl() {
        assertTrue(controlCard(entity("light.a", "on"), null, dense = false).cardTap)
        val dimmable = entity("light.a", "on", """{"brightness": 10}""")
        assertFalse(controlCard(dimmable, null, dense = false).cardTap)
        assertTrue(controlCard(dimmable, widget().copy(cardTap = true), dense = false).cardTap)
    }

    @Test
    fun timersAreOfferedOnlyWhereTheyMakeSense() {
        assertTrue(controlCard(entity("light.a", "on"), null, dense = false).showTimer)
        assertFalse(controlCard(entity("cover.a", "open"), null, dense = false).showTimer)
        assertFalse(controlCard(entity("light.a", "on"), widget().copy(showTimer = false), dense = false).showTimer)
    }

    @Test
    fun aDimmableLightsLevelIsItsFill() {
        val card = controlCard(entity("light.a", "on", """{"brightness": 46}"""), null, dense = false)
        assertEquals(18, card.level)
        assertEquals("18%", card.levelText)
    }

    @Test
    fun aLightWithNoBrightnessHasNoLevelToFill() {
        val card = controlCard(entity("light.a", "on"), null, dense = false)
        assertEquals(null, card.level)
        assertEquals("Off", controlCard(entity("light.a", "off"), null, dense = false).subtitle)
    }

    @Test
    fun aCoverCarriesAnActionStripInsteadOfASubtitle() {
        val card = controlCard(entity("cover.a", "open", """{"current_position": 60}"""), null, dense = false)
        assertTrue(card.actionStrip)
        assertEquals(60, card.level)
        assertEquals(null, card.subtitle)
    }

    @Test
    fun aSwitchIsFullyFilledOrEmpty() {
        assertEquals(100, controlCard(entity("switch.a", "on"), null, dense = false).level)
        assertEquals(0, controlCard(entity("switch.a", "off"), null, dense = false).level)
    }

    @Test
    fun aSwitchHasNoPercentageToShowEvenThoughItHasAFill() {
        // Filling the tile says on; "100%" would claim a level it cannot set.
        assertEquals(null, controlCard(entity("switch.a", "on"), null, dense = false).levelText)
        assertEquals("On", controlCard(entity("switch.a", "on"), null, dense = false).subtitle)
    }

    @Test
    fun anOffDimmerFillsNothingAndSaysSo() {
        val card = controlCard(entity("light.a", "off", """{"brightness": 46}"""), null, dense = false)
        assertEquals(0, card.level)
        assertEquals(null, card.levelText)
        assertEquals("Off", card.subtitle)
    }
}
