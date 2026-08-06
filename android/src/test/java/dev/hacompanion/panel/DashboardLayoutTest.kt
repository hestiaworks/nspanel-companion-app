package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class DashboardLayoutTest {
    @Test
    fun defaultLayoutRoundTrips() {
        val original = DashboardLayout.default()
        val parsed = DashboardLayout.parse(original.toJson().toString())

        assertEquals(original, parsed)
        assertEquals("climate", parsed.defaultPageId)
        assertEquals(60, parsed.defaultPageReturnSeconds)
        assertEquals(360, parsed.weatherCacheMaxAgeMinutes)
        assertEquals(false, parsed.keepScreenOn)
        assertEquals(listOf("thermostat", "weather", "controls"), parsed.pages.map { it.widgets.single().type })
    }

    @Test
    fun parsesKeepScreenOnPreference() {
        val parsed = DashboardLayout.parse(
            """{"schema_version":1,"revision":"always-on","keep_screen_on":true,"pages":[{"id":"main","widgets":[]}]}""",
        )
        assertEquals(true, parsed.keepScreenOn)
        assertEquals(true, parsed.toJson().getBoolean("keep_screen_on"))
    }

    @Test
    fun parsesPanelThemeAndPreservesLegacyLightDefault() {
        val inherited = DashboardLayout.parse(
            """{"schema_version":1,"revision":"theme","theme_mode":"inherit","theme_dark":true,"pages":[{"id":"main","widgets":[]}]}""",
        )
        assertEquals("inherit", inherited.themeMode)
        assertEquals(true, inherited.themeDark)
        val legacy = DashboardLayout.parse(
            """{"schema_version":1,"revision":"legacy","pages":[{"id":"main","widgets":[]}]}""",
        )
        assertEquals("light", legacy.themeMode)
    }

    @Test
    fun validatesWeatherCacheAge() {
        val parsed = DashboardLayout.parse(
            """{"schema_version":1,"revision":"cache","weather_cache_max_age_minutes":720,"pages":[{"id":"main","widgets":[]}]}""",
        )
        assertEquals(720, parsed.weatherCacheMaxAgeMinutes)
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":1,"revision":"cache","weather_cache_max_age_minutes":10081,"pages":[{"id":"main","widgets":[]}]}""",
            )
        }
    }

    @Test
    fun parsesAndValidatesDefaultPageReturnTimeout() {
        val parsed = DashboardLayout.parse(
            """{"schema_version":1,"revision":"timer","default_page_return_seconds":120,"pages":[{"id":"main","widgets":[]}]}""",
        )
        assertEquals(120, parsed.defaultPageReturnSeconds)
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":1,"revision":"timer","default_page_return_seconds":3601,"pages":[{"id":"main","widgets":[]}]}""",
            )
        }
    }

    @Test
    fun parsesExplicitEntityAndLabel() {
        val layout = DashboardLayout.parse(
            """
            {
              "schema_version": 1,
              "revision": "room-7",
              "default_page_id": "main",
              "pages": [{
                "id": "main",
                "title": "Living room",
                "widgets": [{
                  "type": "entity_button",
                  "entity_id": "light.ceiling",
                  "label": "Ceiling"
                }]
              }]
            }
            """.trimIndent(),
        )

        assertEquals("light.ceiling", layout.pages.single().widgets.single().entityId)
        assertEquals("Ceiling", layout.pages.single().widgets.single().label)
    }

    @Test
    fun parsesAndValidatesWeatherForecastLength() {
        val layout = DashboardLayout.parse(
            """{"schema_version":1,"revision":"forecast","pages":[{"id":"weather","widgets":[{"type":"weather","entity_id":"weather.home","forecast_days":3}]}]}""",
        )
        assertEquals(3, layout.pages.single().widgets.single().forecastDays)
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":1,"revision":"forecast","pages":[{"id":"weather","widgets":[{"type":"weather","forecast_days":4}]}]}""",
            )
        }
    }

    @Test
    fun parsesControlPresentationOptions() {
        val layout = DashboardLayout.parse(
            """{"schema_version":1,"revision":"control-options","pages":[{"id":"controls","widgets":[{"type":"entity_button","entity_id":"light.ceiling","icon":"light","show_timer":false,"card_tap":true}]}]}""",
        )
        val widget = layout.pages.single().widgets.single()
        assertEquals("light", widget.icon)
        assertEquals(false, widget.showTimer)
        assertEquals(true, widget.cardTap)
    }

    @Test
    fun rejectsUnknownSchemaAndWidgets() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":2,"revision":"x","pages":[{"id":"a","widgets":[]}]}""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":1,"revision":"x","pages":[{"id":"a","widgets":[{"type":"webview"}]}]}""",
            )
        }
    }

    @Test
    fun rejectsMissingDefaultPageAndDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":1,"revision":"x","default_page_id":"missing","pages":[{"id":"a","widgets":[]}]}""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":1,"revision":"x","pages":[{"id":"a","widgets":[]},{"id":"a","widgets":[]}]}""",
            )
        }
    }

    @Test
    fun atomicStoreLoadsValidLayoutAndIgnoresCorruption() {
        val directory = Files.createTempDirectory("dashboard-layout-test").toFile()
        try {
            val store = AtomicLayoutFileStore(directory)
            val layout = DashboardLayout.default().copy(revision = "saved-2")
            store.save(layout)
            assertEquals(layout, store.load())

            directory.resolve("dashboard-layout.json").writeText("not-json")
            assertNull(store.load())
        } finally {
            directory.deleteRecursively()
        }
    }
}
