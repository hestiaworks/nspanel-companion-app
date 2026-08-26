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
}
