package dev.hacompanion.panel

import org.json.JSONArray
import org.json.JSONObject

data class ControlSchedule(
    val id: String?,
    val entityId: String,
    val time: String,
    val weekdays: List<String>,
    val action: String,
    val position: Int = 100,
    val scriptEntityId: String? = null,
    val enabled: Boolean = true,
    val nextRun: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("entity_id", entityId).put("time", time)
        .put("weekdays", JSONArray(weekdays)).put("action", action).put("position", position)
        .put("enabled", enabled).apply { id?.let { put("id", it) }; scriptEntityId?.let { put("script_entity_id", it) } }

    companion object {
        fun parse(value: JSONObject): ControlSchedule = ControlSchedule(
            value.optString("id").takeIf(String::isNotBlank), value.getString("entity_id"), value.getString("time"),
            buildList { val days = value.optJSONArray("weekdays") ?: JSONArray(); for (i in 0 until days.length()) add(days.optString(i)) },
            value.getString("action"), value.optInt("position", 100), value.optString("script_entity_id").takeIf(String::isNotBlank),
            value.optBoolean("enabled", true), value.optString("next_run").takeIf(String::isNotBlank),
        )
    }
}
