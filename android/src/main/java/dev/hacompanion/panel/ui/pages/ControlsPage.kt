package dev.hacompanion.panel.ui.pages

/**
 * Everything a control can be asked to do.
 *
 * The page knows nothing about Home Assistant: it calls these, and
 * PanelDashboardView turns them into service calls and sheets.
 */
interface ControlActions {
    fun toggle(entityId: String)
    fun setBrightness(entityId: String, percent: Int)
    fun openFanSpeed(entityId: String)
    fun openCover(entityId: String)

    /** The sheet a dimmable light opens on a long press. */
    fun openBrightness(entityId: String)

    /** open, stop or close, from a cover tile's strip. */
    fun moveCover(entityId: String, action: String)
    fun openSchedule(entityId: String)
    fun openTimer(entityId: String)

    /** What a running timer has left as m:ss, or null when none is set. */
    fun timerRemaining(entityId: String): String?

    /** How many schedules the entity has, which the sheet row reports. */
    fun scheduleCount(entityId: String): Int

    /** When the next schedule fires, which is the line under that row. */
    fun scheduleNext(entityId: String): String?

    /**
     * True when a cover is travelling and has stopped saying where it is.
     *
     * The tile stripes a zone out of its leading edge rather than freezing
     * or animating the fill, because both of those claim something untrue.
     */
    fun coverIndeterminate(entityId: String): Boolean
}
