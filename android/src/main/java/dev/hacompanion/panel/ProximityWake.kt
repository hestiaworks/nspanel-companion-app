package dev.hacompanion.panel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log

/**
 * Light the screen when someone comes to the panel.
 *
 * The proximity sensor on this hardware is a wake-up sensor, which is the
 * property the whole feature rests on: it keeps delivering while the device
 * is asleep, so the screen can be lit by an approach rather than a tap.
 *
 * Only registered while the setting is on. A sensor left listening costs
 * power for a panel that mostly does not care, and a screen that lights as
 * someone walks past is not what every wall wants.
 */
class ProximityWake(context: Context) : SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    /**
     * The ordinary sensor, not the wake-up variant.
     *
     * This driver streams about fifteen events a second whatever happens,
     * and a wake-up sensor holds the system up to deliver them — so asking
     * for that one kept the panel awake permanently and the screen never
     * timed out. The app keeps running while the display sleeps, which is
     * all this needs to light it again.
     */
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var listening = false
    private var waking = false
    private var baseline = Float.NaN
    private var margin = marginFor("medium")

    /**
     * Called when someone arrives, whether or not the screen is woken.
     *
     * An approach is an approach even on a panel that is already lit, and
     * Home Assistant wants to know either way.
     */
    var onApproach: (() -> Unit)? = null

    /** When the last approach was, so [nearby] can decay. */
    private var approachedAt = 0L

    /**
     * Whether someone is at the panel now.
     *
     * The sensor reports an arrival, not a presence, so this holds for a
     * while afterwards — an occupancy entity that was true for one sampling
     * interval would be unusable as a trigger.
     */
    val nearby: Boolean
        get() = approachedAt != 0L &&
            android.os.SystemClock.elapsedRealtime() - approachedAt < NEARBY_MS

    /** Whether this panel has a proximity sensor to listen to at all. */
    val available: Boolean get() = sensor != null

    /**
     * Whether an approach should light the screen.
     *
     * Separate from listening: the panel watches the sensor whenever it is
     * running, because Home Assistant's approach entity is worth having on
     * a panel that is configured not to wake. Fifteen comparisons a second
     * is not a cost worth a setting.
     */
    fun setEnabled(enabled: Boolean, sensitivity: String = "medium") {
        margin = marginFor(sensitivity)
        waking = enabled
    }

    /** Begin watching. Safe to call twice. */
    fun start() {
        if (listening) return
        val target = sensor ?: return
        listening = true
        baseline = Float.NaN
        sensors.registerListener(this, target, SensorManager.SENSOR_DELAY_NORMAL)
        Log.i(TAG, "Watching for approach, margin $margin")
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensors.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return
        val previous = baseline
        baseline = updatedBaseline(previous, value)
        // Nothing to compare against on the very first reading.
        if (previous.isNaN() || !approached(value, previous, margin)) return
        approachedAt = android.os.SystemClock.elapsedRealtime()
        onApproach?.invoke()
        // Waking is a separate question from noticing.
        if (!waking) return
        // Already awake: nothing to do, and taking a wake lock would only
        // fight the display timeout the panel is configured with.
        if (power.isInteractive) return
        @Suppress("DEPRECATION")
        power.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "nspanel:approach",
        ).acquire(WAKE_MS)
    }

    companion object {
        private const val TAG = "NSPanelProximity"

        /**
         * Long enough to light the screen and hand over to the display
         * timeout, short enough that a passer-by does not pin it on.
         */
        const val WAKE_MS = 3_000L

        /**
         * How long after an arrival the panel still counts as occupied.
         *
         * The sensor reports arrivals, not presence. Thirty seconds is long
         * enough to be a usable trigger and short enough that a room reads
         * empty soon after someone walks away.
         */
        const val NEARBY_MS = 30_000L

        /**
         * How far above the ambient reading counts as someone arriving.
         *
         * Measured on the panel: nothing in front reads about 2255, a hand
         * a few inches away about 2730. The sensor declares a 9 cm range
         * and then reports raw reflectance, so the declared range is not a
         * distance and cannot be compared against one — the number only
         * means anything relative to whatever the wall reads when empty.
         */
        fun marginFor(sensitivity: String): Float = when (sensitivity) {
            "high" -> 120f
            "low" -> 400f
            else -> 250f
        }

        /** Someone is there when the reading rises clear of the ambient. */
        fun approached(value: Float, baseline: Float, margin: Float): Boolean =
            value > baseline + margin

        /**
         * Follow the empty-wall reading, which drifts with daylight and with
         * whatever is parked in front of the panel.
         *
         * A drop is taken at once: that is the room getting quieter and the
         * new floor is real. A rise is followed slowly, so a hand held up
         * does not become the new normal and stop the panel noticing the
         * next one.
         */
        fun updatedBaseline(previous: Float, value: Float): Float = when {
            previous.isNaN() || value < previous -> value
            else -> previous + (value - previous) * RISE
        }

        private const val RISE = 0.002f
    }


}
