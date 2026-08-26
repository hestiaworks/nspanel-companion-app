package dev.hacompanion.panel

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPrefetchTest {
    private fun camera(name: String) = DashboardWidget(
        type = "camera",
        streamName = name,
        talkbackUrl = "http://192.0.2.10:11080/api/stream",
        talkbackKey = "key",
    )

    private fun page(id: String, vararg widgets: DashboardWidget) =
        DashboardPage(id, id, widgets.toList())

    private val thermostat = DashboardWidget(type = "thermostat", entityId = "climate.a")

    @Test
    fun warmsTheCameraOneSwipeAhead() {
        val pages = listOf(page("a", thermostat), page("b", camera("front")))
        assertEquals(listOf("front"), camerasToWarm(pages, index = 0).map { it.streamName })
    }

    @Test
    fun warmsTheCameraOneSwipeBehind() {
        val pages = listOf(page("a", camera("front")), page("b", thermostat))
        assertEquals(listOf("front"), camerasToWarm(pages, index = 1).map { it.streamName })
    }

    @Test
    fun doesNotWarmTheCameraTwoPagesAway() {
        val pages = listOf(page("a", thermostat), page("b", thermostat), page("c", camera("front")))
        assertEquals(emptyList<String>(), camerasToWarm(pages, index = 0).map { it.streamName })
    }

    @Test
    fun doesNotWarmTheCameraYouAreAlreadyOn() {
        // Arriving resolves for itself; warming here would mint a second session.
        val pages = listOf(page("a", camera("front")), page("b", thermostat))
        assertEquals(emptyList<String>(), camerasToWarm(pages, index = 0).map { it.streamName })
    }

    @Test
    fun warmsBothNeighboursWhenBothAreCameras() {
        val pages = listOf(page("a", camera("front")), page("b", thermostat), page("c", camera("garage")))
        assertEquals(listOf("front", "garage"), camerasToWarm(pages, index = 1).map { it.streamName })
    }

    @Test
    fun ignoresACameraWithNoBridgeToAsk() {
        val unbridged = DashboardWidget(type = "camera", streamName = "front")
        val pages = listOf(page("a", thermostat), page("b", unbridged))
        assertEquals(emptyList<String>(), camerasToWarm(pages, index = 0).map { it.streamName })
    }

    @Test
    fun copesWithAnIndexOutsideTheLayout() {
        val pages = listOf(page("a", thermostat), page("b", camera("front")))
        assertEquals(emptyList<String>(), camerasToWarm(pages, index = 7).map { it.streamName })
        assertEquals(emptyList<String>(), camerasToWarm(emptyList(), index = 0).map { it.streamName })
    }
}
