package dev.hacompanion.panel

/**
 * Whether to make the panel look for a better access point.
 *
 * Phones and laptops roam by themselves. This hardware often will not: after
 * a power cut it joins whichever router was already awake and stays there,
 * because Android weights holding a link it considers good enough. One panel
 * sat upstairs on a downstairs access point at -70 dBm with one at -44 in
 * range, and its own scoring preferred the near one — 100 against 44. It
 * simply never looked again.
 *
 * Reconnecting makes it choose afresh, which is the whole mechanism. The
 * judgement here is about not doing it for ever, because the app cannot tell
 * a panel that is on the wrong router from one that is merely far from the
 * only router there is: seeing what else is in range needs a location
 * permission a wall panel has no business asking for. So it tries a few
 * times, then stops and says what it found.
 */
object LinkPolicy {

    /**
     * How long a poor signal must persist before it is worth a disconnection.
     *
     * Reconnecting costs a few seconds offline, so a dip while someone walks
     * past the router is not worth acting on.
     */
    const val POOR_FOR_MS = 5L * 60 * 1000

    /** Between attempts, so a panel with nothing better in range is not thrashed. */
    const val COOLDOWN_MS = 30L * 60 * 1000

    /** After this many, stop and report rather than reconnecting indefinitely. */
    const val MAX_ATTEMPTS = 5

    /**
     * Whether the link is worse than this panel was told to accept.
     *
     * A threshold rather than a fixed number: a healthy panel in one house
     * reads -64 and in another -75, and the point is to fire only on a link
     * worse than whatever normal is here.
     */
    fun isPoor(layout: DashboardLayout, rssiDbm: Int): Boolean =
        layout.wifiReconnectEnabled && rssiDbm < layout.wifiReconnectBelowDbm

    /**
     * Whether to force a fresh access-point selection now.
     *
     * [sinceLastAttemptMs] is simply large when nothing has been tried yet.
     */
    fun shouldReassociate(
        poorForMs: Long,
        sinceLastAttemptMs: Long,
        attempts: Int,
    ): Boolean = attempts < MAX_ATTEMPTS &&
        poorForMs >= POOR_FOR_MS &&
        sinceLastAttemptMs >= COOLDOWN_MS

    /** Whether it is time to hand the problem to a person, having tried enough. */
    fun shouldReport(attempts: Int, alreadyReported: Boolean): Boolean =
        attempts >= MAX_ATTEMPTS && !alreadyReported

    /**
     * What to tell Home Assistant when reconnecting has not helped.
     *
     * It names the reading, because "weak signal" alone gives nobody
     * anything to act on, and it says how to ask for another round without
     * needing a button of its own: saving the panel's settings republishes
     * the layout, which is what resets the count.
     */
    fun report(rssiDbm: Int, attempts: Int): String =
        "Weak WiFi signal ($rssiDbm dBm). Reconnected $attempts times without " +
            "improvement, so the panel may be out of range of a better access " +
            "point. Save this panel's settings to try again."
}
