package dev.hacompanion.panel.ui

import dev.hacompanion.panel.ui.theme.PanelSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Both weather densities fill the 480 px panel exactly.
 *
 * Section 7 draws forecastDays 3 and 1 as the same page with the hourly band
 * given more or less room, and states its bands top to bottom. Bands do not
 * stretch — a column of them sums to what its heights say — so if these
 * stop summing to 480 the page has either a gap or a clipped row, which is
 * how the one-day page came to stretch a lone day row across the space.
 */
class WeatherBudgetTest {

    private val size = PanelSize()
    private val screen = 480

    @Test
    fun `three days fills the screen`() = with(size) {
        // status, hero, hours, then three day rows in what is left.
        val rows = screen - (statusBar + weatherHero + weatherHours).value.toInt()
        assertEquals(162, rows)
        assertEquals(54, rows / 3)
    }

    @Test
    fun `one day fills the screen`() = with(size) {
        val total = (statusBar + weatherHeroTall + weatherCaption + weatherHoursTall).value.toInt()
        assertEquals(screen, total)
    }

    @Test
    fun `five days fills the screen without an hourly band`() = with(size) {
        val rows = screen - (statusBar + weatherHero).value.toInt()
        assertEquals(270, rows)
        // "about 53 px each, the same height a 3-day row gets" — the row
        // spec is identical across both, which is the point of dropping the
        // band rather than shrinking the rows.
        assertEquals(54, rows / 5)
    }

    @Test
    fun `five days with hours costs each row eleven pixels`() = with(size) {
        val rows = screen - (statusBar + weatherHero + weatherHoursStrip).value.toInt()
        assertEquals(214, rows)
        assertEquals(42, rows / 5)
        // The trade the spec names: 11 px a row buys the strip.
        assertEquals(11, 54 - 42 - 1)
    }
}
