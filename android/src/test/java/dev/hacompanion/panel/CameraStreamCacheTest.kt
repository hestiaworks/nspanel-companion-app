package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraStreamCacheTest {
    private val key = "camera.front_door"

    @Test
    fun handsBackAUrlThatWasJustFetched() {
        val cache = CameraStreamCache()
        cache.store(key, "rtsp://192.0.2.10:8554/a", at = 1_000)
        assertEquals("rtsp://192.0.2.10:8554/a", cache.take(key, now = 2_000))
    }

    @Test
    fun aUrlIsSingleUse() {
        val cache = CameraStreamCache()
        cache.store(key, "rtsp://192.0.2.10:8554/a", at = 1_000)
        cache.take(key, now = 1_500)
        // The session is claimed by the player that took it; a second reader
        // would be handed a port that is already in use.
        assertNull(cache.take(key, now = 1_600))
    }

    @Test
    fun aUrlOlderThanTheWindowIsRefused() {
        val cache = CameraStreamCache()
        cache.store(key, "rtsp://192.0.2.10:8554/a", at = 1_000)
        assertNull(cache.take(key, now = 1_000 + CameraStreamCache.MAX_AGE_MS + 1))
    }

    @Test
    fun aUrlExactlyAtTheWindowIsStillGood() {
        val cache = CameraStreamCache()
        cache.store(key, "rtsp://192.0.2.10:8554/a", at = 1_000)
        assertEquals("rtsp://192.0.2.10:8554/a", cache.take(key, now = 1_000 + CameraStreamCache.MAX_AGE_MS))
    }

    @Test
    fun aBlankUrlIsNeverStored() {
        val cache = CameraStreamCache()
        cache.store(key, "", at = 1_000)
        assertNull(cache.take(key, now = 1_000))
    }

    @Test
    fun camerasDoNotShareEachOthersSessions() {
        val cache = CameraStreamCache()
        cache.store(key, "rtsp://192.0.2.10:8554/a", at = 1_000)
        assertNull(cache.take("camera.garage", now = 1_000))
    }

    @Test
    fun aPrefetchInFlightIsNotStartedTwice() {
        val cache = CameraStreamCache()
        assertEquals(true, cache.beginPrefetch(key, now = 1_000))
        assertEquals(false, cache.beginPrefetch(key, now = 1_100))
    }

    @Test
    fun aPrefetchThatNeverLandedStopsBlockingTheNextOne() {
        val cache = CameraStreamCache()
        cache.beginPrefetch(key, now = 1_000)
        assertEquals(
            true,
            cache.beginPrefetch(key, now = 1_000 + CameraStreamCache.MAX_AGE_MS + 1),
        )
    }

    @Test
    fun storingClearsTheInFlightMark() {
        val cache = CameraStreamCache()
        cache.beginPrefetch(key, now = 1_000)
        cache.store(key, "rtsp://192.0.2.10:8554/a", at = 1_050)
        cache.take(key, now = 1_100)
        assertEquals(true, cache.beginPrefetch(key, now = 1_200))
    }
}
