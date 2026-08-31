package dev.hacompanion.panel

/**
 * Which of the answer and the offer arrived first, and when to accept.
 *
 * The caller offers, and only after Home Assistant tells it the call was
 * answered — one definite direction, rather than both ends racing to start
 * the negotiation. That makes "offer after answer" the normal order and
 * "offer before answer" the race, and the callee has to accept in either.
 *
 * This holds no session and no sockets: it says only whether the offer in
 * hand should be accepted now, which is the whole of the ordering and the
 * only part worth testing away from a panel.
 */
class IntercomHandshake {

    private var accepted = false
    private var held: String? = null
    private var taken = false

    /** The offer to accept now, if one is already waiting. */
    fun answered(): String? {
        accepted = true
        return take()
    }

    /** The offer to accept now, if this call has been answered. */
    fun offered(sdp: String): String? {
        if (taken) return null
        held = sdp
        return take()
    }

    private fun take(): String? {
        val offer = held
        if (!accepted || offer == null) return null
        taken = true
        held = null
        return offer
    }

    /** Forget everything: the next call negotiates from nothing. */
    fun reset() {
        accepted = false
        held = null
        taken = false
    }
}
