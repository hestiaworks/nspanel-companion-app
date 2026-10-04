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
    "ding_dong" to R.raw.ding_dong,
    "three_tone" to R.raw.three_tone,
    "westminster" to R.raw.westminster,
    "marimba" to R.raw.marimba,
    "tubular" to R.raw.tubular,
    "vibraphone" to R.raw.vibraphone,
    "shop_door" to R.raw.shop_door,
    "music_box" to R.raw.music_box,
    "kalimba" to R.raw.kalimba,
    "bell_chords" to R.raw.bell_chords,
)

/**
 * The notification category: short, played once.
 *
 * Synthesised for this project, or from Kenney's Interface Sounds (CC0), so
 * none of them carries licence terms. Mixed to the same loudness as the
 * rings, so one volume setting means the same thing across both.
 */
val NOTIFICATION_SOUNDS: Map<String, Int> = mapOf(
    "notify_soft" to R.raw.notify_soft,
    "notify_chime" to R.raw.notify_chime,
    "notify_ping" to R.raw.notify_ping,
    "notify_bright" to R.raw.notify_bright,
    "notify_confirm" to R.raw.notify_confirm,
    "notify_query" to R.raw.notify_query,
    "notify_glass" to R.raw.notify_glass,
    "notify_kalimba" to R.raw.notify_kalimba,
    "notify_knock" to R.raw.notify_knock,
    "notify_vibraphone" to R.raw.notify_vibraphone,
    "notify_drop" to R.raw.notify_drop,
    "notify_music_box" to R.raw.notify_music_box,
    "notify_alert" to R.raw.notify_alert,
    "notify_double" to R.raw.notify_double,
    "notify_rise" to R.raw.notify_rise,
    "notify_confirm_long" to R.raw.notify_confirm_long,
    "notify_triple" to R.raw.notify_triple,
    "notify_kalimba_run" to R.raw.notify_kalimba_run,
    "notify_bell_chord" to R.raw.notify_bell_chord,
)

/** What plays when a notification names a sound this build does not carry. */
const val FALLBACK_NOTIFICATION_SOUND = "notify_soft"

/** A notification sound to play: a resource, and where to cut it, if anywhere. */
data class OneShot(val resource: Int, val limitMs: Long?)

/** What a notification named [sound] plays, or null for silence. */
fun notificationSound(sound: String): OneShot? = when {
    sound == "off" || sound.isBlank() -> null
    // A name from an editor newer than this panel: a notification someone
    // asked to hear should not arrive silently, so it gets the default.
    else -> OneShot(NOTIFICATION_SOUNDS[sound] ?: NOTIFICATION_SOUNDS.getValue(FALLBACK_NOTIFICATION_SOUND), null)
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
