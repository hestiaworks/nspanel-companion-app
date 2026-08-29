package dev.hacompanion.panel.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The Slab type scale: thirteen roles, nine for text and four for glyphs.
 *
 * It replaces nineteen. The pairs that sat a point apart by accident —
 * pageTitle and cardHeadline at 17 sp, the two weather glyphs at 19 and 18 —
 * collapse into one role each, which is what the token inventory deferred to
 * a redesign to settle.
 */
@Immutable
data class PanelType(
    val display: TextUnit = 120.sp,
    val hero: TextUnit = 106.sp,
    val reading: TextUnit = 34.sp,
    val title: TextUnit = 24.sp,
    val subtitle: TextUnit = 20.sp,
    val body: TextUnit = 17.sp,
    val bodySmall: TextUnit = 15.sp,
    val label: TextUnit = 13.sp,
    /** The clock, which is read at a glance rather than scanned as a label. */
    val clock: TextUnit = 16.sp,
    /** The unit beside a 120 px reading, and the caption under it. */
    val heroUnit: TextUnit = 42.sp,
    val heroUnitSmall: TextUnit = 38.sp,
    val caption: TextUnit = 16.sp,
    /** A phrase standing where a reading would be, so it is not set like one. */
    val note: TextUnit = 19.sp,
    /** The line under a sheet's title, saying what the choice affects. */
    val sheetSubtitle: TextUnit = 14.sp,
    /** A tile's level, and the same reading where a cover's strip takes room. */
    /** The reading sitting on a sheet's level band. */
    val sheetLevel: TextUnit = 56.sp,
    /** The time being edited, the one reading on that screen. */
    val editorTime: TextUnit = 68.sp,
    val tileLevel: TextUnit = 44.sp,
    /** A tile's level is set at .9, as the hero is at .82. */
    val tileLeading: Float = 0.9f,
    val tileLevelSmall: TextUnit = 36.sp,
    /** A tile's name: larger when no level sits above it, smaller under a strip. */
    val tileName: TextUnit = 19.sp,
    val tileNameLarge: TextUnit = 21.sp,
    val tileNameSmall: TextUnit = 17.sp,
    val micro: TextUnit = 11.sp,
    /** The countdown beside a corner mark's clock. */
    val markLabel: TextUnit = 16.sp,
    /** A schedule's time, the reading of its row. */
    val scheduleTime: TextUnit = 26.sp,
    val glyphLarge: TextUnit = 52.sp,
    val glyph: TextUnit = 30.sp,
    val glyphStep: TextUnit = 48.sp,
    val glyphMode: TextUnit = 22.sp,
    /** Tracking belongs to the label role now rather than being its own token. */
    val labelTracking: TextUnit = 0.12.em,
    /** Wider, for the labels that title a band rather than sit beside a value. */
    val labelTrackingWide: TextUnit = 0.14.em,
    /**
     * The proportion of its own size a display numeral occupies.
     *
     * The spec sets the hero line at .82, which no text style can express
     * here: lineHeight can add leading but never take a single line below the
     * font's ascent and descent. Applied as a layout instead.
     */
    val displayLeading: Float = 0.82f,
    /** The weather hero, which the spec sets a shade looser than the panel's. */
    val heroLeading: Float = 0.86f,
)

/** Slab is square. The mic dot and the status pill use CircleShape instead. */
@Immutable
data class PanelRadius(
    val card: Dp = 0.dp,
    val action: Dp = 0.dp,
)

/**
 * Bands run to the screen edge and are separated by a rule, not a gap, so the
 * only inset left is the one that keeps text off the edge.
 */
@Immutable
data class PanelSpace(
    val edge: Dp = 24.dp,
    /** The status strip is inset less than a band: it is chrome, not content. */
    val strip: Dp = 20.dp,
    /** The gap between page bar segments, and the bar's own inset. */
    val hair: Dp = 2.dp,
    /** A tile is inset less than a band: it is a quarter of the screen. */
    val tile: Dp = 20.dp,
    val tileDense: Dp = 18.dp,
    /**
     * How far the unit drops to sit at the numeral's cap height.
     *
     * The difference between two cap heights, which no layout alignment
     * expresses: top alignment lines up line boxes and baseline alignment
     * lines up the feet. Measured on the panel against the spec's frames.
     */
    val heroUnitDrop: Dp = 26.dp,
    /** The same drop against the smaller numeral, in the same proportion. */
    val heroUnitDropSmall: Dp = 23.dp,
    val unconfiguredInsetX: Dp = 30.dp,
    val unconfiguredInsetY: Dp = 24.dp,
    val unconfiguredTextInset: Dp = 16.dp,
    val unconfiguredNameGap: Dp = 10.dp,
    val unconfiguredBodyGap: Dp = 18.dp,
)

/**
 * The layout is a stack of fixed-height bands. These are the heights, and on
 * this hardware they are pixels too: the panel reports density 160, so
 * 1 dp = 1 px and a measurement taken in the spec is the number to use here.
 */
@Immutable
data class PanelSize(
    val statusBar: Dp = 34.dp,
    val pageBar: Dp = 3.dp,
    val headerRow: Dp = 56.dp,
    val targetRow: Dp = 64.dp,
    val attributeRow: Dp = 56.dp,
    val sheetHeader: Dp = 76.dp,
    val listRow: Dp = 88.dp,
    val presetCell: Dp = 96.dp,
    val confirmButton: Dp = 100.dp,
    val modeRow: Dp = 105.dp,
    /**
     * The mode row when an attribute row is also present.
     *
     * The attribute row's 56 px is not free: the spec buys it back from the
     * numeral and this row together, so the column still sums to 480 rather
     * than pushing the caption off the bottom of the reading block.
     */
    val modeRowCompact: Dp = 81.dp,
    val levelBand: Dp = 120.dp,
    /** The weather reading, and the hours under it. */
    val weatherHero: Dp = 176.dp,
    val weatherHours: Dp = 108.dp,
    /** A cover's band, shorter because an action row shares the sheet. */
    val coverBand: Dp = 110.dp,
    /** A travelling cover's striped zone, and the period of its bars. */
    val motionZone: Dp = 64.dp,
    val motionPeriod: Dp = 48.dp,
    /** How far a band's zone stops short of its scale labels. */
    val motionBaseline: Dp = 30.dp,
    val rail: Dp = 132.dp,
    val icon: Dp = 30.dp,
    /** A corner mark's glyph, smaller than the tile's own. */
    val mark: Dp = 19.dp,
    val dot: Dp = 10.dp,
    val stroke: Dp = 1.dp,
    /**
     * The rule at a sheet's top edge.
     *
     * The one place two pixels are used: it marks where the sheet begins
     * against the dimmed page behind it, which one pixel at 65% dim cannot.
     */
    val sheetRule: Dp = 2.dp,
    /** The rule marking the rail as the control the setpoint answers to. */
    val railRule: Dp = 4.dp,
    /** The ▲ ■ ▼ under a cover, where another tile has a subtitle. */
    val tileStrip: Dp = 64.dp,
    /** The row that adds a schedule, the one filled action in its sheet. */
    val addRow: Dp = 92.dp,
    /** The schedule editor's bands: header, the time it steps, its rows. */
    val editorHeader: Dp = 48.dp,
    val editorTime: Dp = 112.dp,
    val stepColumn: Dp = 80.dp,
    val segmentRow: Dp = 70.dp,
    val deleteWidth: Dp = 160.dp,
)

val LocalPanelType = staticCompositionLocalOf { PanelType() }
val LocalPanelRadius = staticCompositionLocalOf { PanelRadius() }
val LocalPanelSpace = staticCompositionLocalOf { PanelSpace() }
val LocalPanelSize = staticCompositionLocalOf { PanelSize() }
