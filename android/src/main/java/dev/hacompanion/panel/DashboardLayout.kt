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
    val showHourly: Boolean = true,
    val icon: String = "auto",
    val showTimer: Boolean = true,
    val timerPresets: List<Int> = listOf(5, 15, 30, 60),
    val cardTap: Boolean? = null,
    val showFanSpeed: Boolean = false,
    val streamBaseUrl: String? = null,
    val streamName: String? = null,
    val talkbackUrl: String? = null,
    val talkbackKey: String? = null,
    val incomingAudio: Boolean = false,
    val tapAction: String = "fullscreen",
) {
    fun toJson(): JSONObject = JSONObject().put("type", type).apply {
        entityId?.let { put("entity_id", it) }
        label?.let { put("label", it) }
        if (type == "weather") {
            put("forecast_days", forecastDays)
            put("show_hourly", showHourly)
        }
        if (type == "entity_button") {
            put("icon", icon)
            put("show_timer", showTimer)
            put("timer_presets", JSONArray(timerPresets))
            cardTap?.let { put("card_tap", it) }
            put("show_fan_speed", showFanSpeed)
        }
        if (type == "camera") {
            streamBaseUrl?.let { put("stream_base_url", it) }; streamName?.let { put("stream_name", it) }
            talkbackUrl?.let { put("talkback_url", it) }; talkbackKey?.let { put("talkback_key", it) }
            put("incoming_audio", incomingAudio); put("tap_action", tapAction)
        }
    }

    companion object {
        val SUPPORTED_TYPES = setOf("thermostat", "weather", "controls", "entity_button", "sensor", "camera")

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
            val showHourly = json.optBoolean("show_hourly", true)
            val icon = json.optString("icon", "auto")
            require(type != "entity_button" || icon in CONTROL_ICONS) { "Invalid control icon" }
            val showTimer = json.optBoolean("show_timer", true)
            val timerValues = json.optJSONArray("timer_presets")
            val timerPresets = if (timerValues == null) listOf(5, 15, 30, 60) else buildList {
                for (index in 0 until timerValues.length()) add(timerValues.getInt(index))
            }
            require(type != "entity_button" || timerPresets.size in 1..4 && timerPresets.all { it in 1..1_440 }) {
                "Invalid timer presets"
            }
            val cardTap = if (json.has("card_tap")) json.optBoolean("card_tap") else null
            val showFanSpeed = json.optBoolean("show_fan_speed", false)
            val streamBaseUrl = json.optString("stream_base_url").takeIf(String::isNotBlank)
            val streamName = json.optString("stream_name").takeIf(String::isNotBlank)
            val talkbackUrl = json.optString("talkback_url").takeIf(String::isNotBlank)
            val talkbackKey = json.optString("talkback_key").takeIf(String::isNotBlank)
            val incomingAudio = json.optBoolean("incoming_audio", false)
            val tapAction = json.optString("tap_action", "fullscreen")
            require(type != "camera" || tapAction in setOf("none", "fullscreen", "intercom")) { "Invalid camera tap action" }
            require(type != "camera" || streamBaseUrl == null || streamBaseUrl.startsWith("rtsp://") ||
                streamBaseUrl.startsWith("rtsps://") || streamBaseUrl.startsWith("http://") ||
                streamBaseUrl.startsWith("https://")) { "Invalid camera stream URL" }
            return DashboardWidget(type, entityId, label, forecastDays, showHourly, icon, showTimer, timerPresets, cardTap, showFanSpeed, streamBaseUrl, streamName, talkbackUrl, talkbackKey, incomingAudio, tapAction)
        }

        val CONTROL_ICONS = setOf(
            "auto", "light", "ceiling-light", "floor-lamp", "wall-light", "led-strip", "spotlight",
            "fan", "ceiling-fan", "ventilation", "power", "switch", "plug", "socket", "curtains", "cover",
            "blinds", "shutter", "garage", "radiator", "air-conditioner", "fireplace", "lock",
            "gate", "pump", "vacuum", "speaker",
            "table-lamp", "chandelier", "pendant-light", "outdoor-light", "night-light", "desk-lamp",
            "desk-fan", "air-purifier", "humidifier", "dehumidifier", "extractor-fan", "power-strip",
            "battery", "solar", "energy", "meter", "ups", "awning", "window", "door", "skylight",
            "thermostat", "heater", "boiler", "temperature", "snowflake", "unlock", "alarm", "shield",
            "camera", "motion", "presence", "bell", "kitchen", "oven", "microwave", "fridge",
            "dishwasher", "washing-machine", "dryer", "coffee", "kettle", "robot-vacuum", "broom",
            "water", "faucet", "sprinkler", "pool", "shower", "television", "music", "radio",
            "gamepad", "projector", "bedroom", "bathroom", "office", "garden", "balcony", "stairs",
        )
    }
}
