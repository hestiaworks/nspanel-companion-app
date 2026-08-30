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
    fun theWholeTileIsTheToggle() {
        // A card carried its own controls and so could not also be one. A tile
        // puts the level in a sheet precisely so its surface is free to toggle.
        assertTrue(controlCard(entity("light.a", "on"), null, dense = false).cardTap)
        val dimmable = entity("light.a", "on", """{"brightness": 10}""")
        assertTrue(controlCard(dimmable, null, dense = false).cardTap)
        assertTrue(controlCard(entity("switch.a", "on"), null, dense = false).cardTap)
    }

    @Test
    fun aCoverHasAPositionRatherThanAnOnAndAnOff() {
        assertFalse(controlCard(entity("cover.a", "open"), null, dense = false).cardTap)
    }

    @Test
    fun theLayoutCanStillSayOtherwise() {
        val cover = entity("cover.a", "open")
        assertTrue(controlCard(cover, widget(entityId = "cover.a").copy(cardTap = true), dense = false).cardTap)
    }

    @Test
    fun timersAreOfferedOnlyWhereTheyMakeSense() {
        assertTrue(controlCard(entity("light.a", "on"), null, dense = false).showTimer)
        // A cover has one now: see aCoverGetsATimerNowThat... below. What
        // still has none is anything with no off to arrive at.
        assertFalse(controlCard(entity("sensor.a", "21.4"), null, dense = false).showTimer)
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

    @Test
    fun anUnavailableEntityKeepsItsTileAndLosesEverythingElse() {
        // The grid must not reflow around a device that went away, so the
        // tile and the name stay; the fill and the wash go, because they
        // would claim a state nothing is reporting.
        val card = controlCard(entity("light.a", "unavailable", """{"brightness": 200}"""), null, dense = false)
        assertFalse(card.available)
        assertFalse(card.active)
        assertEquals(null, card.level)
        assertEquals(null, card.levelText)
        assertEquals("Unavailable", card.subtitle)
    }

    @Test
    fun anUnknownEntityIsUnavailableTooSinceNeitherReportsAState() {
        assertFalse(controlCard(entity("switch.a", "unknown"), null, dense = false).available)
    }

    @Test
    fun anUnavailableEntityCannotBeTappedButCanStillBeOpened() {
        // The long press survives: its sheet is the only thing that can say
        // why the tile has gone quiet.
        assertFalse(controlCard(entity("light.a", "unavailable"), null, dense = false).cardTap)
    }

    @Test
    fun aMovingCoverIsTheOneStateWhereStopIsTheLitCell() {
        assertTrue(controlCard(entity("cover.a", "opening"), null, dense = false).moving)
        assertTrue(controlCard(entity("cover.a", "closing"), null, dense = false).moving)
        assertFalse(controlCard(entity("cover.a", "open"), null, dense = false).moving)
    }

    @Test
    fun aFanShowsItsSpeedOnlyWhereTheLayoutAskedFor() {
        // show_fan_speed is what reveals the fill and the percentage; without
        // it a fan is a switch, which is what the admin's toggle means.
        val fan = entity("fan.a", "on", """{"percentage": 66, "supported_features": 1}""")
        assertEquals("66%", controlCard(fan, widget(entityId = "fan.a").copy(showFanSpeed = true), dense = false).levelText)
        assertEquals(null, controlCard(fan, widget(entityId = "fan.a").copy(showFanSpeed = false), dense = false).levelText)
    }

    @Test
    fun aClosingCoverIsStillAsOpenAsItsPositionSays() {
        // A cover on its way down is not off. Reading state alone blanked the
        // fill for the whole descent and then snapped it back at rest.
        val card = controlCard(
            entity("cover.a", "closing", """{"current_position": 60}"""), null, dense = false,
        )
        assertTrue(card.active)
        assertEquals(60, card.level)
        assertEquals("60%", card.levelText)
    }

    @Test
    fun aFullyClosedCoverIsOffHoweverItGotThere() {
        listOf("closed", "closing", "open").forEach { state ->
            val card = controlCard(
                entity("cover.a", state, """{"current_position": 0}"""), null, dense = false,
            )
            assertFalse("$state at 0 should read as off", card.active)
        }
    }

    @Test
    fun aCoverGetsATimerNowThatItIsNoLongerCompetingForFooterRoom() {
        // The admin hid this because a fourth footer button did not fit. The
        // footer is gone; the timer lives in the sheet.
        assertTrue(controlCard(entity("cover.a", "open"), null, dense = false).showTimer)
    }

    @Test
    fun colourTemperatureIsOfferedOnlyWhenTheLightReportsIt() {
        val plain = entity("light.a", "on", """{"brightness": 128}""")
        assertFalse(controlCard(plain, null, dense = false).hasColourTemperature)
        val tunable = entity("light.a", "on", """{"brightness":128,"color_temp_kelvin":3200}""")
        val card = controlCard(tunable, null, dense = false)
        assertTrue(card.hasColourTemperature)
        assertEquals("3200K", card.colourTemperature)
    }

    @Test
    fun anUnavailableLightOffersNoColourTemperature() {
        // The attribute survives in the state machine after a light drops
        // off, and a band you cannot move is worse than no band.
        val gone = entity("light.a", "unavailable", """{"color_temp_kelvin":3200}""")
        assertFalse(controlCard(gone, null, dense = false).hasColourTemperature)
    }
}
