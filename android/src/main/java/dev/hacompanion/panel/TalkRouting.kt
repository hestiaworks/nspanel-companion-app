package dev.hacompanion.panel

/**
 * Where a panel should send the microphone.
 *
 * The talkback add-on reaches the doorbell over the camera's own protocol
 * and is seconds faster; Scrypted is the path that always existed. The
 * add-on is therefore an optimisation, and losing it should cost speed
 * rather than the ability to speak to whoever is at the door — a stopped
 * add-on, or one reinstalled so it no longer recognises the key it was
 * paired with, otherwise presents to the person holding the button as a
 * door that simply cannot hear them.
 *
 * Pure, and separate from the view, so the rule that decides this is the
 * one under test rather than a copy of it.
 */
object TalkRouting {

    /**
     * The endpoint and key to use, or null if there is nowhere to send audio.
     *
     * [fellBack] is set once the add-on has failed on this page, and keeps
     * the slower path for the rest of it: retrying a refused endpoint every
     * time the button is pressed would stutter rather than recover.
     */
    fun endpoint(widget: DashboardWidget, fellBack: Boolean): Pair<String, String>? {
        val scrypted = pair(widget.talkbackUrl, widget.talkbackKey)
        if (fellBack) return scrypted
        // A URL with no key is not half a route: it would 401 on every
        // request. Treated as absent so the working path is used instead.
        return pair(widget.talkUrl, widget.talkKey) ?: scrypted
    }

    private fun pair(url: String?, key: String?): Pair<String, String>? {
        val u = url?.takeIf(String::isNotBlank) ?: return null
        val k = key?.takeIf(String::isNotBlank) ?: return null
        return u to k
    }
}
