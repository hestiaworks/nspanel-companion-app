package dev.hacompanion.panel

import org.json.JSONObject
import org.json.JSONArray

object HomeAssistantProtocol {
    fun messageType(text: String): String? =
        try {
            JSONObject(text).optString("type").takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }

    fun auth(accessToken: String): String =
        JSONObject()
            .put("type", "auth")
            .put("access_token", accessToken)
            .toString()

    fun subscribeToStateChanges(id: Int = 1): String =
        JSONObject()
            .put("id", id)
            .put("type", "subscribe_events")
            .put("event_type", "state_changed")
            .toString()

    fun getStates(id: Int = 2): String =
        JSONObject()
            .put("id", id)
            .put("type", "get_states")
            .toString()

    fun subscribeToEvent(eventType: String, id: Int = 3): String =
        JSONObject()
            .put("id", id)
            .put("type", "subscribe_events")
            .put("event_type", eventType)
            .toString()

    fun callService(
        id: Int,
        domain: String,
        service: String,
        entityId: String,
        serviceData: JSONObject = JSONObject(),
    ): String =
        JSONObject()
            .put("id", id)
            .put("type", "call_service")
            .put("domain", domain)
            .put("service", service)
            .put("target", JSONObject().put("entity_id", entityId))
            .put("service_data", serviceData)
            .toString()

    fun statesFromResult(text: String, expectedId: Int = 2): List<EntityState> {
        val message = try {
            JSONObject(text)
        } catch (_: Exception) {
            return emptyList()
        }
        if (
            message.optString("type") != "result" ||
            message.optInt("id") != expectedId ||
            !message.optBoolean("success")
        ) return emptyList()
        return entitiesFromArray(message.optJSONArray("result") ?: JSONArray())
    }

    fun changedEntity(text: String): EntityState? {
        val message = try {
            JSONObject(text)
        } catch (_: Exception) {
            return null
        }
        if (message.optString("type") != "event") return null
        val event = message.optJSONObject("event") ?: return null
        if (event.optString("event_type") != "state_changed") return null
        val newState = event.optJSONObject("data")?.optJSONObject("new_state") ?: return null
        return entityFromJson(newState)
    }

    fun eventType(text: String): String? =
        try {
            JSONObject(text)
                .takeIf { it.optString("type") == "event" }
                ?.optJSONObject("event")
                ?.optString("event_type")
                ?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }

    fun doorbellEvent(text: String, expectedType: String): DoorbellEvent? {
        val message = try {
            JSONObject(text)
        } catch (_: Exception) {
            return null
        }
        val event = message.optJSONObject("event") ?: return null
        if (message.optString("type") != "event" || event.optString("event_type") != expectedType) {
            return null
        }
        val data = event.optJSONObject("data") ?: JSONObject()
        return DoorbellEvent(
            streamBaseUrl = data.optString("stream_base_url").takeIf(String::isNotBlank),
            streamName = data.optString("stream_name").takeIf(String::isNotBlank),
            talkbackUrl = data.optString("talkback_url").takeIf(String::isNotBlank),
            talkbackKey = data.optString("talkback_key").takeIf(String::isNotBlank),
            quietMode = data.optBoolean("quiet_mode", false),
            autoCloseMs = data.optLong("auto_close_ms").takeIf { it > 0L },
            talkExtendMs = data.optLong("talk_extend_ms", 15_000L).coerceIn(0L, 60_000L),
            talkbackTestUrl = data.optString("talkback_test_url").takeIf(String::isNotBlank),
        )
    }

    fun dashboardLayoutEvent(text: String, expectedType: String): DashboardLayout? {
        return try {
            val message = JSONObject(text)
            val event = message.optJSONObject("event") ?: return null
            if (message.optString("type") != "event" || event.optString("event_type") != expectedType) {
                return null
            }
            val data = event.optJSONObject("data") ?: return null
            DashboardLayout.parse(data.optJSONObject("layout") ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    fun detail(text: String, key: String): String =
        try {
            JSONObject(text).optString(key)
        } catch (_: Exception) {
            ""
        }

    private fun entitiesFromArray(array: JSONArray): List<EntityState> =
        buildList {
            for (index in 0 until array.length()) {
                entityFromJson(array.optJSONObject(index) ?: continue)?.let(::add)
            }
        }

    private fun entityFromJson(value: JSONObject): EntityState? {
        val entityId = value.optString("entity_id")
        if (entityId.isBlank()) return null
        return EntityState(
            entityId = entityId,
            state = value.optString("state"),
            attributes = value.optJSONObject("attributes") ?: JSONObject(),
        )
    }
}
