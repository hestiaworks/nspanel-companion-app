package dev.hacompanion.panel

import org.json.JSONArray
import org.json.JSONObject

data class DashboardLayout(
    val schemaVersion: Int,
    val revision: String,
    val defaultPageId: String,
    val pages: List<DashboardPage>,
    val defaultPageReturnSeconds: Int = 60,
    val weatherCacheMaxAgeMinutes: Int = 360,
    val keepScreenOn: Boolean = false,
    val themeMode: String = "light",
    val themeDark: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema_version", schemaVersion)
        .put("revision", revision)
        .put("default_page_id", defaultPageId)
        .put("default_page_return_seconds", defaultPageReturnSeconds)
        .put("weather_cache_max_age_minutes", weatherCacheMaxAgeMinutes)
        .put("keep_screen_on", keepScreenOn)
        .put("theme_mode", themeMode)
        .put("theme_dark", themeDark)
        .put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } })

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_PAGES = 8
        const val MAX_WIDGETS_PER_PAGE = 12

        fun parse(text: String): DashboardLayout = parse(JSONObject(text))

        fun parse(json: JSONObject): DashboardLayout {
            val version = json.optInt("schema_version", -1)
            require(version == CURRENT_SCHEMA_VERSION) { "Unsupported layout schema: $version" }
            val revision = json.optString("revision").trim()
            require(revision.isNotEmpty() && revision.length <= 64) { "Invalid layout revision" }
            val values = json.optJSONArray("pages") ?: error("Layout pages are required")
            require(values.length() in 1..MAX_PAGES) { "Layout must contain 1–$MAX_PAGES pages" }
            val pages = buildList { for (index in 0 until values.length()) add(DashboardPage.parse(values.getJSONObject(index))) }
            require(pages.map(DashboardPage::id).distinct().size == pages.size) { "Page IDs must be unique" }
            val defaultPageId = json.optString("default_page_id").ifBlank { pages.first().id }
            require(pages.any { it.id == defaultPageId }) { "Default page does not exist" }
            val returnSeconds = json.optInt("default_page_return_seconds", 60)
            require(returnSeconds in 0..3_600) { "Default-page return must be 0–3600 seconds" }
            val cacheMinutes = json.optInt("weather_cache_max_age_minutes", 360)
            require(cacheMinutes in 0..10_080) { "Weather cache age must be 0–10080 minutes" }
            val keepScreenOn = json.optBoolean("keep_screen_on", false)
            val themeMode = json.optString("theme_mode", "light")
            require(themeMode in setOf("light", "dark", "inherit")) { "Invalid panel theme" }
            val themeDark = json.optBoolean("theme_dark", false)
            return DashboardLayout(version, revision, defaultPageId, pages, returnSeconds, cacheMinutes, keepScreenOn, themeMode, themeDark)
        }

        fun default(): DashboardLayout = DashboardLayout(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            revision = "builtin-1",
            defaultPageId = "climate",
            pages = listOf(
                DashboardPage("climate", "Thermostat", listOf(DashboardWidget("thermostat"))),
                DashboardPage("weather", "Weather", listOf(DashboardWidget("weather"))),
                DashboardPage("controls", "Controls", listOf(DashboardWidget("controls"))),
            ),
            defaultPageReturnSeconds = 60,
            weatherCacheMaxAgeMinutes = 360,
            keepScreenOn = false,
        )
    }
}

data class DashboardPage(
    val id: String,
    val title: String,
    val widgets: List<DashboardWidget>,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title)
        .put("widgets", JSONArray().apply { widgets.forEach { put(it.toJson()) } })

    companion object {
        fun parse(json: JSONObject): DashboardPage {
            val id = json.optString("id").trim()
            require(id.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { "Invalid page ID" }
            val title = json.optString("title").trim().takeIf(String::isNotEmpty) ?: id
            require(title.length <= 48) { "Page title is too long" }
            val values = json.optJSONArray("widgets") ?: JSONArray()
            require(values.length() <= DashboardLayout.MAX_WIDGETS_PER_PAGE) { "Too many widgets" }
            val widgets = buildList { for (index in 0 until values.length()) add(DashboardWidget.parse(values.getJSONObject(index))) }
            return DashboardPage(id, title, widgets)
        }
    }
}

data class DashboardWidget(
    val type: String,
    val entityId: String? = null,
    val label: String? = null,
    val forecastDays: Int = 5,
    val icon: String = "auto",
    val showTimer: Boolean = true,
    val cardTap: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("type", type).apply {
        entityId?.let { put("entity_id", it) }
        label?.let { put("label", it) }
        if (type == "weather") put("forecast_days", forecastDays)
        if (type == "entity_button") {
            put("icon", icon)
            put("show_timer", showTimer)
            cardTap?.let { put("card_tap", it) }
        }
    }

    companion object {
        val SUPPORTED_TYPES = setOf("thermostat", "weather", "controls", "entity_button", "sensor")

        fun parse(json: JSONObject): DashboardWidget {
            val type = json.optString("type").trim()
            require(type in SUPPORTED_TYPES) { "Unsupported widget: $type" }
            val entityId = json.optString("entity_id").trim().takeIf(String::isNotEmpty)
            require(entityId == null || entityId.matches(Regex("[a-z0-9_]+\\.[a-z0-9_]+"))) {
                "Invalid entity ID"
            }
            val label = json.optString("label").trim().takeIf(String::isNotEmpty)
            require(label == null || label.length <= 48) { "Widget label is too long" }
            val forecastDays = json.optInt("forecast_days", 5)
            require(type != "weather" || forecastDays in setOf(1, 3, 5)) { "Invalid weather forecast length" }
            val icon = json.optString("icon", "auto")
            require(type != "entity_button" || icon in CONTROL_ICONS) { "Invalid control icon" }
            val showTimer = json.optBoolean("show_timer", true)
            val cardTap = if (json.has("card_tap")) json.optBoolean("card_tap") else null
            return DashboardWidget(type, entityId, label, forecastDays, icon, showTimer, cardTap)
        }

        val CONTROL_ICONS = setOf(
            "auto", "light", "ceiling-light", "floor-lamp", "wall-light", "led-strip", "spotlight",
            "fan", "ceiling-fan", "ventilation", "power", "switch", "plug", "socket", "curtains", "cover",
            "blinds", "shutter", "garage", "radiator", "air-conditioner", "fireplace", "lock",
            "gate", "pump", "vacuum", "speaker",
        )
    }
}
