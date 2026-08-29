package dev.hacompanion.panel.ui

import dev.hacompanion.panel.ui.theme.PanelSize
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panel is 480 px tall and does not scroll, so a sheet either fits or
 * loses its bottom row silently.
 *
 * The cover sheet is the tallest one built: a level band, the three doors,
 * then the timer and schedule links. It came to 456 px against the 432 a
 * dialog is granted when the window reserves space for a navigation bar,
 * which cost the schedule row a quarter of its height on real hardware.
 */
class SheetBudgetTest {

    private val size = PanelSize()
    private val screen = 480

    /** Rule, header, and then each row with the hairline above it. */
    private fun coverSheetHeight(): Int = with(size) {
        (sheetRule + sheetHeader +
            (stroke + coverBand) +
            (stroke + listRow) +
            (stroke + listRow) +
            (stroke + listRow)).value.toInt()
    }

    @Test
    fun `the tallest sheet fits the screen`() {
        val height = coverSheetHeight()
        assertTrue(
            "Cover sheet is $height px, taller than the $screen px panel",
            height <= screen,
        )
    }

    @Test
    fun `there is not room for another row on the cover sheet`() {
        // Guards the reading of the test above: it passes because the sheet
        // fits, not because the budget is roomy. One more link overflows.
        val withAnotherRow = coverSheetHeight() + (size.stroke + size.listRow).value.toInt()
        assertTrue(withAnotherRow > screen)
    }
}
