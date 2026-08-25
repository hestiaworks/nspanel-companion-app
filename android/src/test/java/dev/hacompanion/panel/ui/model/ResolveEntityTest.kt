package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.DashboardWidget
import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveEntityTest {
    private val entities = mapOf(
        "light.a" to EntityState("light.a", "on", JSONObject()),
        "weather.home" to EntityState("weather.home", "sunny", JSONObject()),
    )

    @Test
    fun findsTheEntityTheWidgetNames() {
        val widget = DashboardWidget(type = "entity_button", entityId = "light.a")
        assertEquals("light.a", resolveEntity(entities, widget)?.entityId)
    }

    @Test
    fun fallsBackToTheFirstEntityOfADomain() {
        val widget = DashboardWidget(type = "weather")
        assertEquals("weather.home", resolveEntity(entities, widget, "weather")?.entityId)
    }

    @Test
    fun fallsBackWhenTheNamedEntityIsGone() {
        val widget = DashboardWidget(type = "weather", entityId = "weather.missing")
        assertEquals("weather.home", resolveEntity(entities, widget, "weather")?.entityId)
    }

    @Test
    fun reportsNothingWhenNothingMatches() {
        assertNull(resolveEntity(entities, DashboardWidget(type = "sensor", entityId = "sensor.gone")))
        assertNull(resolveEntity(entities, DashboardWidget(type = "thermostat"), "climate"))
    }
}
