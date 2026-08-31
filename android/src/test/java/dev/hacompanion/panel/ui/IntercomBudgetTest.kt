package dev.hacompanion.panel.ui

import dev.hacompanion.panel.ui.theme.PanelSize
import org.junit.Assert.assertTrue
import org.junit.Test

/** Three more screens against the 480 px panel. */
class IntercomBudgetTest {

    private val size = PanelSize()
    private val page = 480 - PanelSize().statusBar.value

    @Test
    fun `the call screen leaves a spacer rather than overflowing`() = with(size) {
        val fixed = intercomLiveRule + intercomHero + intercomMeter + intercomActions
        val spacer = page - fixed.value
        assertTrue("spacer is $spacer px", spacer > 0f)
    }

    @Test
    fun `every intercom touch target clears the floor`() = with(size) {
        listOf(intercomRow, intercomActions, intercomIncoming).forEach {
            assertTrue("${it.value} px", it.value >= 64f)
        }
    }

    @Test
    fun `four rows and their gaps fit under the list header`() = with(size) {
        val rows = page - intercomHeader.value
        val four = 4 * intercomRow.value + 4 * intercomRowGap.value
        assertTrue("four rows need $four of $rows", four <= rows)
    }
}
