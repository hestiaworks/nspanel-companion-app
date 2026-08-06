package dev.hacompanion.panel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAssistantProtocolTest {
    @Test
    fun parsesWeatherForecastSubscriptionEvent() {
        val event = HomeAssistantProtocol.weatherForecastEvent(
            """{"id":15,"type":"event","event":{"type":"hourly","forecast":[{"datetime":"2026-08-03T15:00:00+00:00","temperature":24}]}}""",
            15,
        )
        assertEquals("hourly", event?.first)
        assertEquals(24.0, event?.second?.getJSONObject(0)?.getDouble("temperature"))
    }

    @Test
    fun recognizesAuthenticationRequest() {
        assertEquals(
            "auth_required",
            HomeAssistantProtocol.messageType("""{"type":"auth_required","ha_version":"2026.7"}"""),
        )
    }

    @Test
    fun ignoresMalformedMessage() {
        assertNull(HomeAssistantProtocol.messageType("not-json"))
    }

    @Test
    fun createsAuthenticationPayloadWithoutLoggingToken() {
        val payload = JSONObject(HomeAssistantProtocol.auth("secret-token"))
        assertEquals("auth", payload.getString("type"))
        assertEquals("secret-token", payload.getString("access_token"))
    }

    @Test
    fun createsStateSubscription() {
        val payload = JSONObject(HomeAssistantProtocol.subscribeToStateChanges(42))
        assertEquals(42, payload.getInt("id"))
        assertEquals("subscribe_events", payload.getString("type"))
        assertEquals("state_changed", payload.getString("event_type"))
    }

    @Test
    fun parsesStateResult() {
        val states = HomeAssistantProtocol.statesFromResult(
            """
            {
              "id": 2,
              "type": "result",
              "success": true,
              "result": [{
                "entity_id": "climate.office",
                "state": "heat",
                "attributes": {
                  "friendly_name": "Office",
                  "current_temperature": 21.5,
                  "temperature": 22
                }
              }]
            }
            """.trimIndent(),
        )
        assertEquals(1, states.size)
        assertEquals("climate.office", states.single().entityId)
        assertEquals(21.5, states.single().numberAttribute("current_temperature")!!, 0.0)
    }

    @Test
    fun parsesStateChangedEvent() {
        val state = HomeAssistantProtocol.changedEntity(
            """
            {
              "type": "event",
              "event": {
                "event_type": "state_changed",
                "data": {
                  "new_state": {
                    "entity_id": "light.office",
                    "state": "on",
                    "attributes": {"friendly_name": "Office light"}
                  }
                }
              }
            }
            """.trimIndent(),
        )
        assertEquals("light.office", state?.entityId)
        assertEquals("on", state?.state)
    }

    @Test
    fun createsServiceCall() {
        val payload = JSONObject(
            HomeAssistantProtocol.callService(
                id = 9,
                domain = "light",
                service = "toggle",
                entityId = "light.office",
            ),
        )
        assertEquals("call_service", payload.getString("type"))
        assertEquals("light.office", payload.getJSONObject("target").getString("entity_id"))
    }

    @Test
    fun createsAndRecognizesDoorbellEventSubscription() {
        val subscription = JSONObject(
            HomeAssistantProtocol.subscribeToEvent("nspanel_doorbell", 3),
        )
        assertEquals("nspanel_doorbell", subscription.getString("event_type"))
        assertEquals(
            "nspanel_doorbell",
            HomeAssistantProtocol.eventType(
                """{"type":"event","event":{"event_type":"nspanel_doorbell","data":{}}}""",
            ),
        )
    }

    @Test
    fun parsesDoorbellMediaOptions() {
        val event = HomeAssistantProtocol.doorbellEvent(
            """
            {
              "type": "event",
              "event": {
                "event_type": "nspanel_doorbell",
                "data": {
                  "stream_base_url": "http://10.0.2.2:1985",
                  "stream_name": "native_test",
                  "quiet_mode": true,
                  "auto_close_ms": 30000,
                  "talk_extend_ms": 20000,
                  "talkback_test_url": "http://10.0.2.2:8124/api/talkback"
                }
              }
            }
            """.trimIndent(),
            "nspanel_doorbell",
        )
        assertEquals("http://10.0.2.2:1985", event?.streamBaseUrl)
        assertEquals("native_test", event?.streamName)
        assertEquals(true, event?.quietMode)
        assertEquals(30_000L, event?.autoCloseMs)
        assertEquals(20_000L, event?.talkExtendMs)
        assertEquals("http://10.0.2.2:8124/api/talkback", event?.talkbackTestUrl)
    }

    @Test
    fun parsesDashboardLayoutEvent() {
        val layout = HomeAssistantProtocol.dashboardLayoutEvent(
            """
            {"type":"event","event":{"event_type":"nspanel_layout","data":{"layout":{
              "schema_version":1,"revision":"test-3","default_page_id":"room",
              "pages":[{"id":"room","title":"Room","widgets":[
                {"type":"entity_button","entity_id":"light.test_ceiling"}
              ]}]
            }}}}
            """.trimIndent(),
            "nspanel_layout",
        )
        assertEquals("test-3", layout?.revision)
        assertEquals("light.test_ceiling", layout?.pages?.single()?.widgets?.single()?.entityId)
    }
}
