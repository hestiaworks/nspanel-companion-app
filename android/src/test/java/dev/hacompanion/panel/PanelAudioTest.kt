package dev.hacompanion.panel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Making the panel louder, and deciding whether it may make a sound at all.
 */
class PanelAudioTest {

    /** 16-bit little-endian, the way AudioRecord fills the buffer. */
    private fun pcm(vararg values: Int): ByteArray {
        val bytes = ByteArray(values.size * 2)
        values.forEachIndexed { index, value ->
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun samplesOf(bytes: ByteArray): List<Int> =
        (bytes.indices step 2).map {
            ((bytes[it + 1].toInt() shl 8) or (bytes[it].toInt() and 0xFF)).toShort().toInt()
        }

    @Test
    fun `full gain leaves the samples exactly as they were`() {
        val samples = pcm(0, 1000, -1000, 32767, -32768)
        val copy = samples.copyOf()
        applyGain(samples, samples.size, percent = 100)
        assertArrayEquals(copy, samples)
    }

    @Test
    fun `gain scales the samples`() {
        val samples = pcm(100, -250)
        applyGain(samples, samples.size, percent = 200)
        assertEquals(listOf(200, -500), samplesOf(samples))
    }

    @Test
    fun `a sample that would overflow clips instead of wrapping`() {
        // Wrapping flips the sample's polarity, so a raised voice becomes a
        // burst of noise — far worse than the flat top of a clipped one.
        val samples = pcm(20000, -20000)
        applyGain(samples, samples.size, percent = 300)
        assertEquals(listOf(32767, -32768), samplesOf(samples))
    }

    @Test
    fun `only the bytes that were read are touched`() {
        // AudioRecord fills a prefix of the buffer; the tail is last round's
        // audio, and amplifying it would replay a fragment of the past.
        val samples = pcm(100, 100, 100, 100)
        applyGain(samples, count = 4, percent = 200)
        assertEquals(listOf(200, 200, 100, 100), samplesOf(samples))
    }

    @Test
    fun `an odd byte count leaves the stray byte alone`() {
        // A read can end mid-sample. Treating the leftover byte as a whole
        // one would put a click in the stream every frame.
        val samples = pcm(100, 100)
        applyGain(samples, count = 3, percent = 200)
        assertEquals(listOf(200, 100), samplesOf(samples))
    }

    @Test
    fun `off means silent`() {
        assertFalse(shouldPlay("off", quiet = false))
    }

    @Test
    fun `a chosen sound plays`() {
        assertTrue(shouldPlay("chime", quiet = false))
    }

    @Test
    fun `a muted doorbell stays muted`() {
        // Quiet mode is what someone sets when the baby is asleep. A chime
        // that ignored it would be the loudest thing in the house.
        assertFalse(shouldPlay("bell", quiet = true))
    }

    @Test
    fun `a sound this build does not carry is silence, not a crash`() {
        assertFalse(shouldPlay("foghorn", quiet = false))
    }

    @Test
    fun `volume is a fraction of full scale`() {
        assertEquals(0f, volumeOf(0), 0.001f)
        assertEquals(0.7f, volumeOf(70), 0.001f)
        assertEquals(1f, volumeOf(100), 0.001f)
        // Anything the panel is handed out of range is pulled back in.
        assertEquals(1f, volumeOf(140), 0.001f)
    }
}
