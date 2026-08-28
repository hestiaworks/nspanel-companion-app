package dev.hacompanion.panel.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.hacompanion.panel.R

/**
 * Barlow, the design's voice. Bundled because the panel does not have it.
 *
 * Barlow covers Latin and Latin Extended but not Cyrillic. This Home
 * Assistant is Ukrainian — 159 of its 811 entities have Cyrillic names — but
 * panel widget labels are written by hand, and the decision is to keep them
 * Latin. A Cyrillic label would fall back to the system face glyph by glyph
 * and look wrong; if that ever becomes unavoidable, substitute a family with
 * Cyrillic coverage here and nothing else needs to move.
 */
object PanelFont {
    val barlow = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )

    /**
     * Readings update in place. Proportional digits reflow as the value
     * changes, so every number on the panel asks for tabular ones.
     */
    const val TABULAR = "tnum"
}
