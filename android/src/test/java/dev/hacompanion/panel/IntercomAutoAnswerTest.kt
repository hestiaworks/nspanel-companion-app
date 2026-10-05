package dev.hacompanion.panel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntercomAutoAnswerTest {

    private val on = AutoAnswerSettings(enabled = true, lingerSeconds = 10, maxSeconds = 60)

    @Test fun `the layout carries the settings, off by default`() {
        val base = """{"schema_version": 1, "revision": "r", "default_page_id": "p",
            "pages": [{"id": "p", "widgets": [{"type": "weather"}]}]"""
        val plain = DashboardLayout.parse("$base}")
        assertEquals(AutoAnswerSettings(false, 10, 60), AutoAnswerSettings.of(plain))
        val set = DashboardLayout.parse("""$base, "intercom": {"auto_answer": true,
            "auto_answer_linger_seconds": 0, "auto_answer_max_seconds": 300}}""")
        assertEquals(AutoAnswerSettings(true, 0, 300), AutoAnswerSettings.of(set))
        assertEquals(AutoAnswerSettings.of(set), AutoAnswerSettings.of(DashboardLayout.parse(set.toJson().toString())))
    }

    @Test fun `it answers only when on, ringing is allowed, and nothing is busy`() {
        assertTrue(IntercomPolicy.autoAnswer(on, Treatment.RING, busy = false))
        assertFalse(IntercomPolicy.autoAnswer(on.copy(enabled = false), Treatment.RING, busy = false))
        // Show without sound means no voice either; don't show declines.
        assertFalse(IntercomPolicy.autoAnswer(on, Treatment.SILENT, busy = false))
        assertFalse(IntercomPolicy.autoAnswer(on, Treatment.SUPPRESS, busy = false))
        assertFalse(IntercomPolicy.autoAnswer(on, Treatment.RING, busy = true))
    }

    // A clock that runs only when told to.
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private fun fire() { timers.removeAt(0).second() }
    private val events = mutableListOf<String>()
    private val flow = AutoAnswerFlow(
        schedule = { delay, run -> val entry = delay to run; timers += entry; ({ timers.remove(entry) }) },
        endCall = { events += "end-call" },
        showEnded = { peer -> events += "ended:$peer" },
        close = { events += "close" },
    )

    @Test fun `the caller hanging up lingers, then returns`() {
        flow.started("Office", on)
        assertTrue(flow.listening)
        assertTrue(flow.remoteEnded())
        assertEquals(listOf("ended:Office"), events)
        assertEquals(10_000L, timers.single().first)
        fire()
        assertEquals(listOf("ended:Office", "close"), events)
    }

    @Test fun `a linger of zero returns at once`() {
        flow.started("Office", on.copy(lingerSeconds = 0))
        flow.remoteEnded()
        assertEquals(listOf("close"), events)
        assertTrue(timers.isEmpty())
    }

    @Test fun `nobody answering ends it at the limit`() {
        flow.started("Office", on)
        assertEquals(60_000L, timers.single().first)
        fire()
        assertEquals(listOf("end-call", "ended:Office"), events)
    }

    @Test fun `talk makes it an ordinary call`() {
        flow.started("Office", on)
        flow.responded()
        assertFalse(flow.listening)
        assertTrue(timers.isEmpty())
        // The caller hanging up is now an ordinary end, with no linger.
        assertFalse(flow.remoteEnded())
        assertTrue(events.isEmpty())
    }

    @Test fun `ending it here cancels the limit`() {
        flow.started("Office", on)
        flow.localEnded()
        assertTrue(timers.isEmpty())
        assertFalse(flow.listening)
    }

    @Test fun `a tap during the linger returns at once`() {
        flow.started("Office", on)
        flow.remoteEnded()
        flow.dismissed()
        assertTrue(timers.isEmpty())
        assertEquals(listOf("ended:Office", "close"), events)
    }

    @Test fun `a new ring replaces a linger and is not busy because of it`() {
        flow.started("Office", on)
        flow.remoteEnded()
        assertTrue(flow.lingering)
        flow.newRing()
        assertFalse(flow.lingering)
        assertTrue(timers.isEmpty())
    }
}
