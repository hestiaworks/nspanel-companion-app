package dev.hacompanion.panel

import org.json.JSONArray
import org.json.JSONObject

/**
 * A layout and a set of entity states for looking at the panel without a
 * Home Assistant behind it.
 *
 * This lives in the debug source set, so it is compiled out of every release
 * build rather than guarded by a flag at runtime — the release APK does not
 * contain it at all.
 *
 * It exists because the states worth checking a design against are the ones a
 * real house rarely holds at the moment you want to look: a thermostat in dry
 * mode, a split setpoint, a room that reports humidity, a cover half open, an
 * entity that has gone unavailable. Waiting for the weather to oblige is not a
 * way to verify a layout.
 *
 * Pass a variant to choose which of those to draw:
 *
 *     adb shell am start -n dev.hacompanion.panel/.MainActivity \
 *       --ez demo true --es demo_variant dry
 */
object DemoLayout {

    /** The variants the harness knows how to draw, in the order they are useful. */
    val variants = listOf("heat", "cool", "auto", "dry", "fan_only", "off", "unavailable")

    fun layout(): DashboardLayout = DashboardLayout(
        schemaVersion = 1,
        revision = "demo",
        defaultPageId = "climate",
        themeMode = "dark",
        themeDark = true,
        pages = listOf(
            DashboardPage(
                id = "climate",
                title = "Living room",
                widgets = listOf(
                    DashboardWidget(type = "thermostat", entityId = "climate.demo", label = "Living room"),
                ),
            ),
            DashboardPage(
                id = "controls",
                title = "Controls",
                widgets = listOf(
                    DashboardWidget(type = "controls", entityId = "light.demo_ceiling", label = "Ceiling"),
                    DashboardWidget(type = "controls", entityId = "light.demo_lamp", label = "Lamp"),
                    DashboardWidget(type = "controls", entityId = "cover.demo_blind", label = "Blind"),
                    DashboardWidget(type = "sensor", entityId = "sensor.demo_temp", label = "Bedroom temp"),
                ),
            ),
            DashboardPage(
                id = "weather",
                title = "Weather",
                widgets = listOf(
                    DashboardWidget(type = "weather", entityId = "weather.demo", label = "Outside"),
                ),
            ),
        ),
    )

    fun states(variant: String): List<EntityState> = listOf(
        climate(variant),
        light("light.demo_ceiling", "Ceiling", on = true, brightness = 178),
        light("light.demo_lamp", "Lamp", on = false, brightness = null),
        cover(),
        sensor(),
        weather(),
    )

    /**
     * The thermostat, in whichever state was asked for.
     *
     * Fan and swing are always reported, so the attribute row is present in
     * every variant except the valve-like ones; humidity likewise, because the
     * caption under the reading is the thing hardest to see on real hardware.
     */
    private fun climate(variant: String): EntityState {
        val attributes = JSONObject()
            .put("friendly_name", "Living room")
            .put("hvac_modes", JSONArray(listOf("off", "heat", "cool", "heat_cool", "dry", "fan_only")))
            .put("fan_modes", JSONArray(listOf("auto", "low", "high")))
            .put("fan_mode", "low")
            .put("swing_modes", JSONArray(listOf("off", "vertical")))
            .put("swing_mode", "vertical")
            .put("current_temperature", 19.4)
            .put("current_humidity", 47)
            .put("temperature", 21.0)
            .put("target_temp_low", 20.0)
            .put("target_temp_high", 24.0)

        return when (variant) {
            "cool" -> EntityState("climate.demo", "cool", attributes
                .put("hvac_action", "cooling")
                .put("current_temperature", 26.2)
                .put("current_humidity", 58)
                .put("temperature", 23.0))
            "auto" -> EntityState("climate.demo", "heat_cool", attributes.put("hvac_action", "idle"))
            "dry" -> EntityState("climate.demo", "dry", attributes.put("hvac_action", "drying"))
            "fan_only" -> EntityState("climate.demo", "fan_only", attributes.put("hvac_action", "fan"))
            "off" -> EntityState("climate.demo", "off", attributes.put("hvac_action", "off"))
            "unavailable" -> EntityState("climate.demo", "unavailable", JSONObject()
                .put("friendly_name", "Living room"))
            else -> EntityState("climate.demo", "heat", attributes.put("hvac_action", "heating"))
        }
    }

    private fun light(id: String, name: String, on: Boolean, brightness: Int?): EntityState =
        EntityState(id, if (on) "on" else "off", JSONObject()
            .put("friendly_name", name)
            .put("supported_color_modes", JSONArray(listOf("brightness")))
            .apply { if (brightness != null) put("brightness", brightness) })

    private fun cover(): EntityState =
        EntityState("cover.demo_blind", "open", JSONObject()
            .put("friendly_name", "Blind")
            .put("current_position", 65)
            // OPEN | CLOSE | SET_POSITION | STOP, so the sheet offers a band.
            .put("supported_features", 15)
            .put("device_class", "blind"))

    private fun sensor(): EntityState =
        EntityState("sensor.demo_temp", "23.3", JSONObject()
            .put("friendly_name", "Bedroom temp")
            .put("unit_of_measurement", "°C")
            .put("device_class", "temperature"))

    private fun weather(): EntityState =
        EntityState("weather.demo", "sunny", JSONObject()
            .put("friendly_name", "Outside")
            .put("temperature", 24.0)
            .put("humidity", 41)
            .put("wind_speed", 11.0)
            .put("temperature_unit", "°C")
            .put("forecast", JSONArray(listOf(
                forecastDay("2026-08-28T12:00:00+00:00", "sunny", 26.0, 14.0),
                forecastDay("2026-08-29T12:00:00+00:00", "partlycloudy", 24.0, 13.0),
                forecastDay("2026-08-30T12:00:00+00:00", "rainy", 19.0, 12.0),
                forecastDay("2026-08-31T12:00:00+00:00", "cloudy", 21.0, 12.0),
                forecastDay("2026-09-01T12:00:00+00:00", "sunny", 25.0, 13.0),
            ))))

    private fun forecastDay(at: String, condition: String, high: Double, low: Double): JSONObject =
        JSONObject()
            .put("datetime", at)
            .put("condition", condition)
            .put("temperature", high)
            .put("templow", low)
}
