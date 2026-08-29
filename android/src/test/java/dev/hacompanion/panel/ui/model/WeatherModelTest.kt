package dev.hacompanion.panel.ui.model

import dev.hacompanion.panel.EntityState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherModelTest {
    private fun weather(attributes: String, state: String = "cloudy") =
        EntityState("weather.home", state, JSONObject(attributes))

    @Test
    fun formatsTemperatureWithoutATrailingZero() {
        val model = weatherModel(weather("""{"temperature":17.0,"temperature_unit":"°C"}"""), 5, false)
        assertEquals("17°", model.temperature)
    }

    @Test
    fun keepsOneDecimalWhenItCarriesInformation() {
        val model = weatherModel(weather("""{"temperature":16.8,"temperature_unit":"°C"}"""), 5, false)
        assertEquals("16.8°", model.temperature)
    }

    @Test
    fun reportsAnEmDashWhenTemperatureIsMissing() {
        assertEquals("—", weatherModel(weather("""{}"""), 5, false).temperature)
    }

    @Test
    fun describesApparentTemperatureAndHumidity() {
        val model = weatherModel(
            weather("""{"temperature":16.8,"apparent_temperature":15.2,"humidity":63}"""), 5, false,
        )
        // Rounded: an apparent temperature is a modelled number, and the
        // spec's mockups print whole degrees everywhere but the hero.
        assertEquals("feels 15° · 63%", model.detail)
    }

    @Test
    fun fallsBackToTemperatureWhenApparentIsMissing() {
        assertEquals("feels 17°", weatherModel(weather("""{"temperature":16.8}"""), 5, false).detail)
    }

    @Test
    fun limitsDailyForecastToTheRequestedDays() {
        val entries = (1..5).joinToString(",") {
            """{"label":"D$it","condition":"cloudy","temperature":2$it,"templow":1$it}"""
        }
        val model = weatherModel(weather("""{"forecast":[$entries]}"""), 3, false)
        assertEquals(3, model.daily.size)
        assertEquals("D1", model.daily.first().label)
    }

    @Test
    fun readsHighFromTemperatureAndLowFromTemplow() {
        val model = weatherModel(
            weather("""{"forecast":[{"label":"Today","condition":"rainy","temperature":22,"templow":11}]}"""),
            5, false,
        )
        assertEquals("22°", model.daily.first().high)
        assertEquals("11°", model.daily.first().low)
        assertEquals("☂\ufe0e", model.daily.first().symbol)
    }

    @Test
    fun omitsHourlyForecastWhenItIsTurnedOff() {
        val hourly = """{"hourly_forecast":[{"label":"Now","condition":"sunny","temperature":20}]}"""
        assertEquals(0, weatherModel(weather(hourly), 5, false).hourly.size)
        assertEquals(1, weatherModel(weather(hourly), 5, true).hourly.size)
    }

    @Test
    fun distinguishesNightFromDay() {
        assertEquals("☾\ufe0e", weatherModel(weather("""{}""", "clear-night"), 5, false).symbol)
        assertEquals("☀\ufe0e", weatherModel(weather("""{}""", "sunny"), 5, false).symbol)
    }

    @Test
    fun titleCasesTheCondition() {
        assertEquals("Clear night", weatherModel(weather("""{}""", "clear-night"), 5, false).condition)
    }

    @Test
    fun fallsBackToAGeneratedSummary() {
        assertEquals(
            "Sunny conditions continue.",
            weatherModel(weather("""{}""", "sunny"), 5, false).summary,
        )
    }

    @Test
    fun prefersTheSummaryTheIntegrationSupplies() {
        assertEquals(
            "Rain arriving this evening.",
            weatherModel(weather("""{"forecast_summary":"Rain arriving this evening."}"""), 5, false).summary,
        )
    }

    @Test
    fun survivesAnEmptyForecast() {
        val model = weatherModel(weather("""{"forecast":[]}"""), 5, true)
        assertEquals(0, model.daily.size)
        assertEquals(0, model.hourly.size)
    }

    private fun sky(attributes: String) =
        EntityState("weather.a", "sunny", JSONObject(attributes))

    private val HOURS = """{
        "temperature": 22, "humidity": 41, "temperature_unit": "\u00b0C",
        "hourly_forecast": [
            {"datetime":"2026-08-28T15:00:00+00:00","condition":"sunny","temperature":22,"precipitation_probability":0},
            {"datetime":"2026-08-28T16:00:00+00:00","condition":"rainy","temperature":21,"precipitation_probability":60}
        ]
    }"""

    @Test
    fun anHourCarriesItsChanceOfRainOnlyWhereThereIsRoomForIt() {
        // One day gives the hours a third of the screen; three days do not.
        val roomy = weatherModel(sky(HOURS), forecastDays = 1, showHourly = true)
        assertEquals(listOf(null, "60%"), roomy.hourly.map { it.precipitation })

        val tight = weatherModel(sky(HOURS), forecastDays = 3, showHourly = true)
        assertEquals(listOf(null, null), tight.hourly.map { it.precipitation })
    }

    @Test
    fun aDryHourSaysNothingRatherThanZero() {
        val model = weatherModel(sky(HOURS), forecastDays = 1, showHourly = true)
        assertEquals(null, model.hourly.first().precipitation)
    }

    @Test
    fun anEntityWithNoHourlyForecastHasNoHoursRatherThanThrowing() {
        val model = weatherModel(sky("""{"temperature": 22}"""), forecastDays = 1, showHourly = true)
        assertTrue(model.hourly.isEmpty())
    }

    @Test
    fun theHeroSplitsItsUnitOffSoTheNumeralCanBeSetAlone() {
        val model = weatherModel(sky(HOURS), forecastDays = 3, showHourly = true)
        assertEquals("22", model.temperatureValue)
        assertEquals("\u00b0", model.unit)
    }

    @Test
    fun aConditionGlyphAsksForItsTextFormRatherThanTheEmojiOne() {
        // Without U+FE0E Android substitutes the colour emoji font, which
        // ignores the colour the band sets and cannot be tinted for rain.
        assertTrue(weatherSymbol("sunny").endsWith("\ufe0e"))
        assertTrue(weatherSymbol("rainy").endsWith("\ufe0e"))
        assertTrue(weatherSymbol("nonsense").endsWith("\ufe0e"))
    }

    @Test
    fun theHeroKeepsItsDecimalWhileEveryForecastRounds() {
        // The hero is the one number that is measured rather than predicted,
        // so it earns its tenth. A forecast is not accurate to a tenth, and
        // six hour cells of differing width read as noise across a room.
        val model = weatherModel(sky(HOURS), forecastDays = 3, showHourly = true)
        assertEquals("22", model.temperatureValue)
        val model2 = weatherModel(
            sky("""{"temperature": 25.8, "temperature_unit": "\u00b0C"}"""), 1, false,
        )
        assertEquals("25.8", model2.temperatureValue)
    }

    @Test
    fun forecastReadingsAreWholeDegrees() {
        val model = weatherModel(sky(HOURS), forecastDays = 3, showHourly = true)
        model.hourly.forEach { assertFalse("hour ${it.label} kept a decimal", it.high.contains(".")) }
        model.daily.forEach {
            assertFalse("day ${it.label} kept a decimal", it.high.contains("."))
            assertFalse("day ${it.label} kept a decimal", it.low.contains("."))
        }
    }

    @Test
    fun oneDayGivesTheHoursTheScreenInsteadOfDayRows() {
        // 7's weather section: forecastDays 3 and 1 draw the same page with
        // the hourly band given more or less room. At one day there are no
        // day rows to draw, so the page says so rather than stretching a
        // lone row across the space they would have taken.
        assertTrue(weatherModel(sky(HOURS), forecastDays = 1, showHourly = true).hoursTakeTheScreen)
        assertFalse(weatherModel(sky(HOURS), forecastDays = 3, showHourly = true).hoursTakeTheScreen)
    }
}
