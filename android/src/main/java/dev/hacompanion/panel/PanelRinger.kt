package dev.hacompanion.panel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

/**
 * The panel's ring, for the doorbell and for an intercom call.
 *
 * One at a time, and never over a call: [stop] is called before any audio
 * session opens, because a ringtone playing into an open microphone is the
 * echo canceller's problem and it should not have to be.
 *
 * The sound loops until something stops it. A doorbell that chimes once
 * while nobody is in the room is a doorbell nobody answered.
 */
class PanelRinger(private val context: Context) {

    private var player: MediaPlayer? = null

    /**
     * Start ringing, or do nothing at all.
     *
     * Volume is set on the player rather than on a stream, so the panel's own
     * volume — which someone set for the intercom's voices — is left alone.
     */
    fun start(sound: String, volumePercent: Int, quiet: Boolean = false) {
        stop()
        if (!shouldPlay(sound, quiet)) return
        val resource = RING_SOUNDS[sound] ?: return
        try {
            // Attributes are passed to create rather than set afterwards:
            // create() prepares the player, and attributes set after prepare
            // are ignored, which would put the ring on the wrong stream.
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val session = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .generateAudioSessionId()
            player = MediaPlayer.create(context, resource, attributes, session)?.apply {
                isLooping = true
                val level = volumeOf(volumePercent)
                setVolume(level, level)
                start()
            }
        } catch (error: Exception) {
            // A panel that cannot make a sound still has to show the call.
            Log.w("PanelRinger", "Could not play $sound", error)
            player = null
        }
    }

    fun stop() {
        val playing = player ?: return
        player = null
        try {
            playing.stop()
        } catch (error: IllegalStateException) {
            Log.w("PanelRinger", "Ring already stopped", error)
        }
        playing.release()
    }
}
