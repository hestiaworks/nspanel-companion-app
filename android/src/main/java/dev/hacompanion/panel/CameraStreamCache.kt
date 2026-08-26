package dev.hacompanion.panel

/**
 * Holds a stream URL fetched shortly before it is needed.
 *
 * Scrypted mints a session per request — the RTSP port differs every time and
 * dies with the session — so the bridge deliberately returns no expiry and
 * tells callers to ask when they need it. Measured on the panel, that ask
 * costs about 620 ms of the 1.7 s it takes to reach a first frame.
 *
 * The compromise is to ask a moment early rather than to store: a URL is
 * usable only for [MAX_AGE_MS] and only once, so a session is always seconds
 * old when it is played, and a miss costs nothing beyond resolving as before.
 */
class CameraStreamCache {
    private class Entry(val url: String, val at: Long)

    private val entries = mutableMapOf<String, Entry>()
    private val inFlight = mutableMapOf<String, Long>()

    /** Records a freshly resolved URL. */
    @Synchronized
    fun store(key: String, url: String, at: Long) {
        inFlight.remove(key)
        if (url.isBlank()) return
        entries[key] = Entry(url, at)
    }

    /** Claims a URL if one was fetched recently enough, consuming it. */
    @Synchronized
    fun take(key: String, now: Long): String? {
        val entry = entries.remove(key) ?: return null
        return entry.url.takeIf { now - entry.at <= MAX_AGE_MS }
    }

    /**
     * Marks a fetch as started, returning whether the caller should make it.
     * Repeated page changes would otherwise ask the bridge to mint a session
     * per swipe. A mark older than the usable window is treated as lost.
     */
    @Synchronized
    fun beginPrefetch(key: String, now: Long): Boolean {
        val started = inFlight[key]
        if (started != null && now - started <= MAX_AGE_MS) return false
        inFlight[key] = now
        return true
    }

    companion object {
        /**
         * How long a minted session is trusted. Short on purpose: the panel
         * prefetches when the camera is one swipe away, so this only has to
         * cover the seconds between that and arriving.
         */
        const val MAX_AGE_MS = 15_000L
    }
}

/**
 * The cameras worth asking the bridge about from the page currently shown.
 *
 * One swipe either side, and never the page being shown: arriving resolves for
 * itself, and warming it too would mint a session nothing plays. A camera with
 * no bridge configured has nothing to ask.
 */
fun camerasToWarm(pages: List<DashboardPage>, index: Int): List<DashboardWidget> {
    if (index !in pages.indices) return emptyList()
    return listOf(index - 1, index + 1)
        .filter { it in pages.indices }
        .flatMap { pages[it].widgets }
        .filter { it.type == "camera" }
        .filter { !it.talkbackUrl.isNullOrBlank() && !it.talkbackKey.isNullOrBlank() }
}
