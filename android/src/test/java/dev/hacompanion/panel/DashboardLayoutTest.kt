package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class DashboardLayoutTest {
    @Test
    fun parsesStatusSettingsAndLimitsControls() {
        val parsed = DashboardLayout.parse(
            """{"schema_version":1,"revision":"status","show_clock":false,"show_mic_indicator":true,"mic_indicator_linger_seconds":20,"pages":[{"id":"main","widgets":[]}]}""",
        )
        assertEquals(false, parsed.showClock)
        assertEquals(true, parsed.showMicIndicator)
        assertEquals(20, parsed.micIndicatorLingerSeconds)
        val controls = (1..5).joinToString(",") { "{\"type\":\"entity_button\",\"entity_id\":\"switch.room_$it\"}" }
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse("""{"schema_version":1,"revision":"too-many","pages":[{"id":"main","widgets":[$controls]}]}""")
        }
    }

    @Test
    fun defaultLayoutRoundTrips() {
        val original = DashboardLayout.default()
        val parsed = DashboardLayout.parse(original.toJson().toString())

        assertEquals(original, parsed)
        assertEquals("awaiting", parsed.defaultPageId)
        assertEquals(60, parsed.defaultPageReturnSeconds)
        assertEquals(360, parsed.weatherCacheMaxAgeMinutes)
        assertEquals(false, parsed.keepScreenOn)
        assertEquals(listOf(emptyList<DashboardWidget>()), parsed.pages.map { it.widgets })
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
            """{"schema_version":1,"revision":"control-options","pages":[{"id":"controls","widgets":[{"type":"entity_button","entity_id":"switch.washer","icon":"washing-machine","show_timer":false,"timer_presets":[5,20,45],"card_tap":true}]}]}""",
        )
        val widget = layout.pages.single().widgets.single()
        assertEquals("washing-machine", widget.icon)
        assertEquals(false, widget.showTimer)
        assertEquals(listOf(5, 20, 45), widget.timerPresets)
        assertEquals(true, widget.cardTap)
    }

    @Test
    fun parsesNativeCameraPage() {
        val layout = DashboardLayout.parse(
            """{"schema_version":1,"revision":"camera","pages":[{"id":"camera","widgets":[{"type":"camera","stream_base_url":"rtsp://192.0.2.76:46211/prebuffer","stream_name":"doorbell_sub","talkback_url":"http://192.0.2.76:11081/talk/44","talkback_key":"0123456789abcdef","incoming_audio":true,"tap_action":"intercom"}]}]}""",
        )
        val widget = layout.pages.single().widgets.single()
        assertEquals("camera", widget.type)
        assertEquals("rtsp://192.0.2.76:46211/prebuffer", widget.streamBaseUrl)
        assertEquals(true, widget.incomingAudio)
        // Written before the checkbox existed: the old tap_action still
        // answers the question, so the page keeps its talk button.
        assertEquals(true, widget.showIntercom)
        assertEquals(widget, DashboardWidget.parse(widget.toJson()))
    }

    @Test
    fun theIntercomCheckboxWinsOverTheActionItReplaced() {
        fun camera(fields: String) = DashboardLayout.parse(
            """{"schema_version":1,"revision":"c","pages":[{"id":"c","widgets":[{"type":"camera",""" +
                """"stream_base_url":"rtsp://192.0.2.76:46211/prebuffer",$fields}]}]}""",
        ).pages.single().widgets.single()

        assertEquals(true, camera(""""tap_action":"none","show_intercom":true""").showIntercom)
        assertEquals(false, camera(""""tap_action":"intercom","show_intercom":false""").showIntercom)
        // Fullscreen meant nothing on a page that is already full-bleed.
        assertEquals(false, camera(""""tap_action":"fullscreen"""").showIntercom)
        assertEquals(false, camera(""""stream_name":"doorbell_sub"""").showIntercom)
    }

    @Test
    fun rejectsAnUnknownSchemaButNotAnUnknownWidget() {
        // A schema this build cannot read is fatal: nothing in the document
        // can be trusted. One widget it has never heard of is not — the rest
        // of the layout is still good, and refusing it all would take every
        // page away the first time Home Assistant learns a new widget type.
        assertThrows(IllegalArgumentException::class.java) {
            DashboardLayout.parse(
                """{"schema_version":2,"revision":"x","pages":[{"id":"a","widgets":[]}]}""",
            )
        }
        val survived = DashboardLayout.parse(
            """{"schema_version":1,"revision":"x","pages":[{"id":"a","widgets":[
               {"type":"webview"},{"type":"weather"}]}]}""",
        )
        assertEquals(listOf("weather"), survived.pages.single().widgets.map { it.type })
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

    @Test
    fun `system UI settings default to what the app has always done`() {
        val layout = DashboardLayout.parse(
            """{"schema_version":1,"revision":"r","pages":[{"id":"p","widgets":[]}]}"""
        )
        assertEquals(NavBarMode.LISTENER, layout.navBarMode)
        assertFalse(layout.hideAccessibilityButton)
    }

    @Test
    fun `system UI settings survive a round trip`() {
        val layout = DashboardLayout.parse(
            """{"schema_version":1,"revision":"r","nav_bar_mode":"immersive",""" +
                """"hide_accessibility_button":true,"pages":[{"id":"p","widgets":[]}]}"""
        )
        assertEquals(NavBarMode.IMMERSIVE, layout.navBarMode)
        assertTrue(layout.hideAccessibilityButton)
        val again = DashboardLayout.parse(layout.toJson())
        assertEquals(NavBarMode.IMMERSIVE, again.navBarMode)
        assertTrue(again.hideAccessibilityButton)
    }

    @Test
    fun `waking on approach is off unless the layout asks for it`() {
        fun panel(fields: String) = DashboardLayout.parse(
            """{"schema_version":1,"revision":"r"$fields,"pages":[{"id":"p","widgets":[]}]}""",
        )
        assertFalse(panel("").wakeOnApproach)
        assertTrue(panel(""","wake_on_approach":true""").wakeOnApproach)
        assertTrue(panel(""","wake_on_approach":true""").toJson()
            .getBoolean("wake_on_approach"))
    }

    @Test
    fun `a widget this build does not know is skipped, not fatal`() {
        // A newer Home Assistant can send a widget type this app has never
        // heard of. Refusing the whole layout over it means every page
        // disappears — which is what happened when an intercom page reached
        // a panel that predated intercom: it silently kept its old pages.
        val layout = DashboardLayout.parse(
            """{"schema_version":1,"revision":"r","pages":[{"id":"p","widgets":[
               {"type":"weather"},{"type":"hologram"}]}]}""",
        )
        val widgets = layout.pages.single().widgets
        assertEquals(listOf("weather"), widgets.map { it.type })
    }

    @Test
    fun `a page left empty by an unknown widget is dropped rather than blank`() {
        val layout = DashboardLayout.parse(
            """{"schema_version":1,"revision":"r","pages":[
               {"id":"keep","widgets":[{"type":"weather"}]},
               {"id":"gone","widgets":[{"type":"hologram"}]}]}""",
        )
        assertEquals(listOf("keep"), layout.pages.map { it.id })
    }

    @Test
    fun `the built-in layout is one page, because it has one thing to say`() {
        // It used to be three placeholders. A panel that has never been given
        // a dashboard then showed three segments in the strip and let you
        // swipe between three copies of the same message.
        val builtin = DashboardLayout.default()
        assertEquals(DashboardLayout.BUILTIN_REVISION, builtin.revision)
        assertEquals(1, builtin.pages.size)
    }

    @Test
    fun `a layout carries the intercom's audio processing settings`() {
        val json = """
            {"schema_version":1,"revision":"audio","default_page_id":"p",
             "pages":[{"id":"p","widgets":[{"type":"weather"}]}],
             "intercom":{"enabled":true,"noise_suppression":false,"auto_gain":false}}
        """.trimIndent()
        val layout = DashboardLayout.parse(json)
        assertEquals(false, layout.intercomNoiseSuppression)
        assertEquals(false, layout.intercomAutoGain)
    }

    @Test
    fun `a layout written before those settings existed keeps webrtc's defaults`() {
        // Both on is what libwebrtc does when asked for nothing, so an older
        // layout must sound exactly as it did.
        val builtin = DashboardLayout.default()
        assertEquals(true, builtin.intercomNoiseSuppression)
        assertEquals(true, builtin.intercomAutoGain)
    }
}
