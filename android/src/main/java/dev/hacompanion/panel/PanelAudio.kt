package dev.hacompanion.panel

/**
 * The sounds this build carries.
 *
 * A name Home Assistant sends that is not here plays nothing. The panel can
 * only play what was bundled into it, and a newer editor offering a sound an
 * older panel lacks must be silence rather than a crash — the same rule the
 * layout parser follows for widgets it has never heard of.
 */
val RING_SOUNDS = mapOf(
    "chime_1" to R.raw.chime_1,
    "chime_2" to R.raw.chime_2,
    "chime_3" to R.raw.chime_3,
)

/**
 * The notification category: short, played once.
 *
 * Empty until the four sounds are bundled. Until then every notification
 * sound but "off" plays the start of chime_1 — a stopgap the design record
 * names as one, not the intended result.
 */
val NOTIFICATION_SOUNDS: Map<String, Int> = emptyMap()

/** How much of chime_1 stands in for a notification sound. */
const val NOTIFICATION_STOPGAP_MS = 1_200L

/** A notification sound to play: a resource, and where to cut it, if anywhere. */
data class OneShot(val resource: Int, val limitMs: Long?)

/** What a notification named [sound] plays, or null for silence. */
fun notificationSound(sound: String): OneShot? = when {
    sound == "off" || sound.isBlank() -> null
    sound in NOTIFICATION_SOUNDS -> OneShot(NOTIFICATION_SOUNDS.getValue(sound), null)
    // A sound this build does not carry. A known notification name is the
    // stopgap; anything else, from an editor newer than this panel, is too:
    // a notification someone asked to hear should not arrive silently.
    else -> OneShot(R.raw.chime_1, NOTIFICATION_STOPGAP_MS)
}

/** Whether a sound should be made at all. */
fun shouldPlay(sound: String, quiet: Boolean): Boolean =
    !quiet && sound in RING_SOUNDS

/** A 0–100 setting as the fraction a media player wants. */
fun volumeOf(percent: Int): Float = percent.coerceIn(0, 100) / 100f

/**
 * Make the captured audio louder, in place.
 *
 * Android exposes no way to set the microphone's own gain, so the only place
 * to raise a quiet talkback is the samples themselves. The buffer is 16-bit
 * little-endian PCM, as AudioRecord fills it.
 *
 * Only the first [count] bytes are touched: a read fills a prefix of the
 * buffer and the tail still holds the previous round, which amplifying would
 * replay. A read can also end mid-sample, and the stray byte is left alone
 * rather than treated as a whole one — that would put a click in the stream
 * every frame.
 *
 * Overflow clips rather than wraps. A wrapped sample flips polarity, so a
 * raised voice becomes a burst of noise, much worse than a flat top.
 */
fun applyGain(buffer: ByteArray, count: Int, percent: Int) {
    if (percent == 100) return
    val scale = percent / 100f
    val usable = minOf(count, buffer.size)
    var index = 0
    while (index + 1 < usable) {
        val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xFF)).toShort()
        val scaled = (sample * scale).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        buffer[index] = (scaled and 0xFF).toByte()
        buffer[index + 1] = ((scaled shr 8) and 0xFF).toByte()
        index += 2
    }
}
