package dev.hacompanion.panel

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCacheStoreTest {
    @Test
    fun storesOnlyWeatherAndRestoresWithinMaximumAge() {
        val directory = Files.createTempDirectory("weather-cache-test").toFile()
        var now = 1_000_000L
        try {
            val store = WeatherCacheStore(directory) { now }
            store.update(listOf(
                EntityState("weather.home", "sunny", JSONObject().put("temperature", 24)),
                EntityState("light.kitchen", "on", JSONObject()),
            ))
            val cached = store.load(60)
            assertEquals(1, cached.size)
            assertEquals("weather.home", cached.single().state.entityId)
            assertEquals(24.0, cached.single().state.numberAttribute("temperature")!!, 0.0)
            assertEquals(now, cached.single().updatedAtMillis)
            now += 61 * 60_000L
            assertTrue(store.load(60).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun zeroMaximumAgeDisablesRestore() {
        val directory = Files.createTempDirectory("weather-cache-disabled-test").toFile()
        try {
            val store = WeatherCacheStore(directory) { 1_000L }
            store.update(listOf(EntityState("weather.home", "rainy", JSONObject())))
            assertTrue(store.load(0).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
