package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraSourceTest {
    @Test
    fun prefersTheUrlResolvedFromTheBridge() {
        assertEquals(
            "rtsp://192.0.2.20:8554/live",
            chooseStreamSource(fresh = "rtsp://192.0.2.20:8554/live", stored = "rtsp://192.0.2.20:1/old"),
        )
    }

    @Test
    fun fallsBackToTheStoredUrlWhenTheBridgeCannotBeReached() {
        assertEquals(
            "rtsp://192.0.2.20:8554/manual",
            chooseStreamSource(fresh = "", stored = "rtsp://192.0.2.20:8554/manual"),
        )
        assertEquals(
            "rtsp://192.0.2.20:8554/manual",
            chooseStreamSource(fresh = null, stored = "rtsp://192.0.2.20:8554/manual"),
        )
    }

    @Test
    fun reportsNothingWhenNeitherIsAvailable() {
        assertEquals("", chooseStreamSource(fresh = null, stored = null))
        assertEquals("", chooseStreamSource(fresh = "", stored = ""))
    }
}
