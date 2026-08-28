package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermostatModelTest {
    private fun climate(state: String, attributes: String = "{}") =
        EntityState("climate.living", state, JSONObject(attributes))

    @Test
    fun readsBothTargetsInDualMode() {
        val model = thermostatModel(
            climate("heat_cool", """{"target_temp_low":21,"target_temp_high":25,"current_temperature":22.5}"""),
            label = null,
        )
        assertTrue(model.dual)
        assertEquals("heat_cool", model.mode)
        assertEquals("21°", model.heat)
        assertEquals("25°", model.cool)
        assertEquals("22.5°", model.current)
    }

    @Test
    fun usesTheSingleTargetForBothInSingleMode() {
        val model = thermostatModel(climate("heat", """{"temperature":20}"""), null)
        assertFalse(model.dual)
        assertEquals("20°", model.heat)
        assertEquals("20°", model.cool)
    }

    @Test
    fun fallsBackToCurrentWhenNoTargetIsSet() {
        val model = thermostatModel(climate("heat", """{"current_temperature":19}"""), null)
        assertEquals("19°", model.heat)
    }

    @Test
    fun reportsAnEmDashWhenThereIsNoReading() {
        assertEquals("—", thermostatModel(climate("off"), null).current)
    }

    @Test
    fun prefersTheActionOverTheState() {
        val model = thermostatModel(climate("heat_cool", """{"hvac_action":"idle"}"""), null)
        assertEquals("Idle", model.action)
    }

    @Test
    fun describesTheStateWhenNoActionIsReported() {
        assertEquals("Heat cool", thermostatModel(climate("heat_cool"), null).action)
    }

    @Test
    fun isPoweredUnlessOff() {
        assertTrue(thermostatModel(climate("heat"), null).powered)
        assertFalse(thermostatModel(climate("off"), null).powered)
    }

    @Test
    fun ordersTheModesTheDeviceOffers() {
        val model = thermostatModel(
            climate("cool", """{"hvac_modes":["off","cool","heat","dry"]}"""), null,
        )
        assertEquals(listOf("heat", "cool", "dry", "off"), model.modes.map { it.mode })
        assertEquals(listOf("Heat", "Cool", "Dry", "Off"), model.modes.map { it.label })
        assertTrue(model.modes.single { it.mode == "cool" }.active)
    }

    @Test
    fun offersTheCurrentModeAndOffWhenTheDeviceListsNone() {
        val model = thermostatModel(climate("heat"), null)
        assertEquals(listOf("heat", "off"), model.modes.map { it.mode })
    }

    @Test
    fun explainsHowToAdjustADualTarget() {
        assertEquals("Tap a temperature, then adjust", thermostatModel(climate("heat_cool"), null).hint)
        assertEquals("Adjust target temperature", thermostatModel(climate("heat"), null).hint)
    }

    private fun ac(state: String, attributes: String) =
        EntityState("climate.ac", state, JSONObject(attributes))

    private val FULL = """{
        "hvac_modes": ["off","heat","cool","heat_cool","dry","fan_only"],
        "fan_modes": ["auto","low","high"], "fan_mode": "low",
        "swing_modes": ["off","vertical"], "swing_mode": "vertical",
        "current_temperature": 19.4, "temperature": 21.0, "current_humidity": 47
    }"""

    @Test
    fun theModeRowHoldsHeatCoolAutoTheSlotAndOff() {
        val model = thermostatModel(ac("heat", FULL), null)
        assertEquals(
            listOf("heat", "cool", "heat_cool", MORE_KEY, "off"),
            model.modeCells.map { it.key },
        )
    }

    @Test
    fun theSlotSaysMoreWhileNeitherSecondaryModeRuns() {
        val model = thermostatModel(ac("cool", FULL), null)
        val slot = model.modeCells.single { it.key == MORE_KEY }
        assertEquals("MORE", slot.label)
        assertFalse(slot.active)
    }

    @Test
    fun theSlotBecomesTheModeThatIsRunning() {
        // So the row still shows the true state at a glance.
        val model = thermostatModel(ac("dry", FULL), null)
        val slot = model.modeCells.single { it.key == MORE_KEY }
        assertEquals("DRY", slot.label)
        assertTrue(slot.active)
    }

    @Test
    fun aUnitWithNeitherSecondaryModeDropsTheSlot() {
        val simple = """{"hvac_modes":["off","heat","cool","heat_cool"],
                         "current_temperature":19.4,"temperature":21.0}"""
        val model = thermostatModel(ac("heat", simple), null)
        assertEquals(listOf("heat", "cool", "heat_cool", "off"), model.modeCells.map { it.key })
        assertTrue(model.moreOptions.isEmpty())
    }

    @Test
    fun theSheetOffersOnlyWhatTheEntityReports() {
        val onlyDry = """{"hvac_modes":["off","heat","cool","dry"],
                          "current_temperature":19.4,"temperature":21.0}"""
        assertEquals(listOf("dry"), thermostatModel(ac("heat", onlyDry), null).moreOptions.map { it.key })
        assertEquals(listOf("dry", "fan_only"), thermostatModel(ac("heat", FULL), null).moreOptions.map { it.key })
    }

    @Test
    fun theAttributeRowCarriesFanAndSwingWhenBothAreReported() {
        val model = thermostatModel(ac("heat", FULL), null)
        assertEquals(listOf("FAN", "SWING"), model.attributes.map { it.label })
        assertEquals(listOf("Low", "Vertical"), model.attributes.map { it.value })
    }

    @Test
    fun aUnitWithOnlyFanSpeedsSaysSoAndTakesTheWholeRow() {
        val fanOnly = """{"hvac_modes":["off","cool"],"fan_modes":["low","high"],
                          "fan_mode":"high","current_temperature":26.2,"temperature":23.0}"""
        val model = thermostatModel(ac("cool", fanOnly), null)
        assertEquals(listOf("FAN SPEED"), model.attributes.map { it.label })
    }

    @Test
    fun aRadiatorValveGetsNoAttributeRowAtAll() {
        // The layout already approved is what a simple thermostat still draws.
        val valve = """{"hvac_modes":["off","heat"],"current_temperature":19.4,"temperature":21.0}"""
        assertTrue(thermostatModel(ac("heat", valve), null).attributes.isEmpty())
    }

    @Test
    fun dryShowsHumidityAsTheBigNumberWithTheRoomBeneath() {
        val model = thermostatModel(ac("dry", FULL), null)
        assertEquals("47", model.displayValue)
        assertEquals("%", model.displayUnit)
        assertEquals("HUMIDITY NOW", model.displayLabel)
        assertEquals("19.4° room", model.displayCaption)
    }

    @Test
    fun dryHasNoSetpointSoTheRailGreysOutRatherThanDisappearing() {
        // The bands must not reflow between modes.
        val model = thermostatModel(ac("dry", FULL), null)
        assertFalse(model.targetUsable)
        assertEquals("not used in this mode", model.targets.single().value)
    }

    @Test
    fun heatShowsTheRoomAsTheBigNumberWithHumidityBeneath() {
        val model = thermostatModel(ac("heat", FULL), null)
        assertEquals("19.4", model.displayValue)
        assertEquals("°", model.displayUnit)
        assertEquals("ROOM NOW", model.displayLabel)
        assertEquals("47% humidity", model.displayCaption)
        assertTrue(model.targetUsable)
    }

    @Test
    fun autoSplitsTheTargetRowInTwo() {
        val auto = """{"hvac_modes":["off","heat","cool","heat_cool"],
                       "current_temperature":19.4,"target_temp_low":20.0,
                       "target_temp_high":24.0}"""
        val model = thermostatModel(ac("heat_cool", auto), null)
        assertEquals(listOf("HEAT TO", "COOL TO"), model.targets.map { it.label })
        assertEquals(listOf("20.0\u00b0", "24.0\u00b0"), model.targets.map { it.value })
    }

    @Test
    fun autoNamesTheBandInTheHeader() {
        val auto = """{"hvac_modes":["off","heat_cool"],"hvac_action":"heating",
                       "current_temperature":19.4,"target_temp_low":20.0,
                       "target_temp_high":24.0}"""
        assertEquals("HEATING \u00b7 20\u201324\u00b0", thermostatModel(ac("heat_cool", auto), null).status)
    }
}
