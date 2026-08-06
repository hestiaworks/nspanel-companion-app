package dev.hacompanion.panel

import org.json.JSONObject

data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JSONObject,
) {
    val domain: String get() = entityId.substringBefore('.')
    val friendlyName: String
        get() = attributes.optString("friendly_name").ifBlank {
            entityId.substringAfter('.').replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }

    fun numberAttribute(name: String): Double? =
        attributes.optDouble(name, Double.NaN).takeUnless(Double::isNaN)
}
