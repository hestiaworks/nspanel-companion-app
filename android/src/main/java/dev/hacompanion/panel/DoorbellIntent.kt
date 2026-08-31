package dev.hacompanion.panel

/**
 * What one screen tells another about a ring.
 *
 * These lived on the WebRTC DoorbellActivity, which is gone: the vocabulary
 * of a doorbell intent is not the property of whichever screen happens to
 * answer it, and outliving that activity is the proof.
 */
object DoorbellIntent {
    const val EXTRA_STREAM_BASE_URL = "dev.hacompanion.panel.DOORBELL_BASE_URL"
    const val EXTRA_STREAM_NAME = "dev.hacompanion.panel.DOORBELL_STREAM"
    const val EXTRA_START_TALKING = "dev.hacompanion.panel.DOORBELL_START_TALKING"
    const val EXTRA_AUTO_CLOSE_MS = "dev.hacompanion.panel.DOORBELL_AUTO_CLOSE_MS"
    const val EXTRA_TALK_EXTEND_MS = "dev.hacompanion.panel.DOORBELL_TALK_EXTEND_MS"
    const val EXTRA_QUIET_MODE = "dev.hacompanion.panel.DOORBELL_QUIET_MODE"
    const val EXTRA_CHIME = "dev.hacompanion.panel.DOORBELL_CHIME"
    const val EXTRA_CHIME_VOLUME = "dev.hacompanion.panel.DOORBELL_CHIME_VOLUME"
    const val EXTRA_TALKBACK_GAIN = "dev.hacompanion.panel.DOORBELL_TALKBACK_GAIN"
    const val EXTRA_TALKBACK_TEST_URL = "dev.hacompanion.panel.DOORBELL_TALKBACK_TEST_URL"
    const val EXTRA_TALKBACK_URL = "dev.hacompanion.panel.DOORBELL_TALKBACK_URL"
    const val EXTRA_TALKBACK_KEY = "dev.hacompanion.panel.DOORBELL_TALKBACK_KEY"

    /** RFC 5737 documentation address: a placeholder, never a real panel's. */
    const val DEFAULT_STREAM_BASE_URL = "http://192.0.2.76:1984"
    const val DEFAULT_STREAM_NAME = "doorbell_sub"
}
