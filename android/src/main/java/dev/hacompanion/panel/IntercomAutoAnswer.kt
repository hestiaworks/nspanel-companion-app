package dev.hacompanion.panel

/** A panel's auto-answer settings, from its layout. */
data class AutoAnswerSettings(val enabled: Boolean, val lingerSeconds: Int, val maxSeconds: Int) {
    companion object {
        fun of(layout: DashboardLayout?) = AutoAnswerSettings(
            enabled = layout?.intercomAutoAnswer ?: false,
            lingerSeconds = layout?.intercomAutoAnswerLingerSeconds ?: 10,
            maxSeconds = layout?.intercomAutoAnswerMaxSeconds ?: 60,
        )
    }
}

object IntercomPolicy {
    /**
     * Whether a ring is answered by itself.
     *
     * Only where ringing is allowed at this hour: "show without sound" means
     * no voice either, and a panel already in a call or a doorbell ring is not
     * free to start talking over it.
     */
    fun autoAnswer(settings: AutoAnswerSettings, treatment: Treatment, busy: Boolean): Boolean =
        settings.enabled && treatment == Treatment.RING && !busy

    /**
     * Whether something else has the panel's audio.
     *
     * A session open without a call id is a call being placed: answering a
     * ring on it would accept the offer on that open, unmuted microphone.
     */
    fun busy(callId: String?, sessionOpen: Boolean, doorbellOnScreen: Boolean): Boolean =
        callId != null || sessionOpen || doorbellOnScreen

    /** The longest a doorbell conversation can last, talk extensions included. */
    const val DOORBELL_LONGEST_MS = 300_000L

    /**
     * Whether the doorbell screen is still up.
     *
     * It runs in its own process, so the dashboard cannot ask it; but the
     * dashboard is paused while it is in front and resumes when it closes.
     */
    fun doorbellOnScreen(launchedAt: Long, now: Long, dashboardBackAt: Long): Boolean =
        launchedAt > 0 && dashboardBackAt < launchedAt && now - launchedAt < DOORBELL_LONGEST_MS
}

/**
 * An auto-answered call, from answering to the screen going back.
 *
 * Listening until someone here taps Talk; a message that ends — the caller
 * hangs up, or nobody answers within the limit — lingers on screen, then the
 * panel returns to its page. Pure: the panel supplies the clock and the
 * effects, so every path is tested without a call.
 */
class AutoAnswerFlow(
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    /** Tell the caller this end has hung up, and close the session. */
    private val endCall: () -> Unit,
    /** Show "Message from <peer> ended". */
    private val showEnded: (String) -> Unit,
    /** Close the call screen; the page underneath was never changed. */
    private val close: () -> Unit,
) {
    var listening = false
        private set
    var lingering = false
        private set
    private var peer = ""
    private var settings = AutoAnswerSettings(false, 10, 60)
    private var cancel: (() -> Unit)? = null

    fun started(peer: String, settings: AutoAnswerSettings) {
        stopTimer()
        this.peer = peer
        this.settings = settings
        listening = true
        lingering = false
        cancel = schedule(settings.maxSeconds * 1000L) {
            cancel = null
            if (!listening) return@schedule
            listening = false
            endCall()
            linger()
        }
    }

    /** Someone here tapped Talk: an ordinary call from now on. */
    fun responded() {
        listening = false
        stopTimer()
    }

    /**
     * The call is closing for any reason: drop the listening state and its
     * limit. A call that dies on its own — the caller's panel lost its wifi —
     * leaves nothing behind to mute the next call or end it. A linger, if one
     * is showing, is left alone.
     */
    fun abandoned() {
        if (!listening) return
        listening = false
        stopTimer()
    }

    /**
     * The caller finished a message. The activity checks [listening] before
     * closing the call, since closing abandons it, and then calls this.
     */
    fun messageEnded() {
        listening = false
        stopTimer()
        linger()
    }

    /** Ended here with End; nothing lingers. */
    fun localEnded() {
        listening = false
        lingering = false
        stopTimer()
    }

    /** A new ring replaces whatever was lingering. */
    fun newRing() {
        lingering = false
        stopTimer()
    }

    /** A tap during the linger. */
    fun dismissed() {
        if (!lingering) return
        lingering = false
        stopTimer()
        close()
    }

    private fun linger() {
        if (settings.lingerSeconds <= 0) {
            close()
            return
        }
        lingering = true
        showEnded(peer)
        cancel = schedule(settings.lingerSeconds * 1000L) {
            cancel = null
            lingering = false
            close()
        }
    }

    private fun stopTimer() {
        cancel?.invoke()
        cancel = null
    }
}
