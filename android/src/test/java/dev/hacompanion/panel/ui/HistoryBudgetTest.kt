package dev.hacompanion.panel.ui

import dev.hacompanion.panel.ui.theme.PanelSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The history page's bands against the 480 px screen. */
class HistoryBudgetTest {

    private val size = PanelSize()

    @Test
    fun `the bars take what the fixed bands leave`() = with(size) {
        val page = 480 - statusBar.value
        val bars = page - (historyHeader + historyHero + historyAxis + historyRanges).value
        assertEquals(170f, bars, 0.001f)
    }

    @Test
    fun `the range buttons stay above the touch floor`() {
        assertTrue(size.historyRanges.value >= 64f)
    }
}
