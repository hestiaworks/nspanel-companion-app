package dev.hacompanion.panel.ui

import dev.hacompanion.panel.ui.theme.PanelSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The light page's bands against the 480 px screen.
 *
 * Everything but the level band has a stated height; the band takes what is
 * left. That is not the spec dodging a number — the plain frame lands on the
 * 120 px it states, which is the check worth having.
 */
class LightBudgetTest {

    private val size = PanelSize()
    private val page = 480 - size.statusBar.value.toInt()

    /** What the level band is left with, once the fixed bands have theirs. */
    private fun band(tuned: Boolean): Int = with(size) {
        val fixed = lightHeader.value +
            (if (tuned) lightHeroTuned else lightHero).value +
            (if (tuned) lightPresetsTuned else lightPresets).value +
            (if (tuned) lightColour.value else 0f) +
            lightSwitch.value
        page - fixed.toInt()
    }

    @Test
    fun `a plain light gives its band the height the spec states`() {
        assertEquals(120, band(tuned = false))
    }

    @Test
    fun `a tunable light keeps a band you can still drag`() {
        // The spec's own tunable frame does not sum to 480 — its bands come
        // to more than the screen, which overflow:hidden hides. The colour
        // row has to come from somewhere, and it comes from the band rather
        // than from a touch target.
        val tuned = band(tuned = true)
        assertTrue("band is $tuned px", tuned in 64..104)
    }

    @Test
    fun `every touch target stays above the floor`() = with(size) {
        listOf(lightPresets, lightPresetsTuned, lightSwitch).forEach {
            assertTrue("${it.value} px", it.value >= 64f)
        }
    }
}
