package dev.hacompanion.panel

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class CachedWeather(val state: EntityState, val updatedAtMillis: Long)

class WeatherCacheStore(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val target get() = File(directory, "weather-cache.json")

    fun load(maxAgeMinutes: Int): List<CachedWeather> {
        if (maxAgeMinutes == 0) return emptyList()
        return try {
            val cutoff = now() - maxAgeMinutes * 60_000L
            val values = JSONObject(target.readText()).optJSONArray("weather") ?: JSONArray()
            buildList {
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index) ?: continue
                    val updatedAt = item.optLong("updated_at", 0L)
                    val entityId = item.optString("entity_id")
                    if (updatedAt >= cutoff && entityId.startsWith("weather.")) add(
                        CachedWeather(
                            EntityState(entityId, item.optString("state"), item.optJSONObject("attributes") ?: JSONObject()),
                            updatedAt,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun update(values: Collection<EntityState>) {
        val weather = values.filter { it.domain == "weather" }.take(MAX_ENTITIES)
        if (weather.isEmpty()) return
        directory.mkdirs()
        val updatedAt = now()
        val payload = JSONObject().put("version", 1).put("weather", JSONArray().apply {
            weather.forEach { entity ->
                put(JSONObject()
                    .put("entity_id", entity.entityId)
                    .put("state", entity.state)
                    .put("attributes", JSONObject(entity.attributes.toString()))
                    .put("updated_at", updatedAt))
            }
        })
        val temporary = File(directory, "weather-cache.json.tmp")
        temporary.writeText(payload.toString())
        check(temporary.renameTo(target)) { "Unable to activate weather cache" }
    }

    companion object {
        private const val MAX_ENTITIES = 4
    }
}
