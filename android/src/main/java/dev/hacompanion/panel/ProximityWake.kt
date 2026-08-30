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
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
        ?: sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var listening = false

    /** Whether this panel has a proximity sensor to listen to at all. */
    val available: Boolean get() = sensor != null

    fun setEnabled(enabled: Boolean) {
        if (enabled == listening) return
        val target = sensor ?: return
        listening = enabled
        if (enabled) {
            sensors.registerListener(this, target, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i(TAG, "Waking on approach, range ${target.maximumRange}")
        } else {
            sensors.unregisterListener(this)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!near(event)) return
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
         * Near, for a sensor that may report centimetres or may report only
         * two values.
         *
         * Comparing against the sensor's own maximum rather than a fixed
         * distance is what makes this work on both: a binary sensor reports
         * its maximum for far and zero for near.
         */
        fun near(distance: Float, maximumRange: Float): Boolean =
            distance < maximumRange && distance < NEAR_CM

        private const val NEAR_CM = 10f
    }

    private fun near(event: SensorEvent): Boolean {
        val distance = event.values.firstOrNull() ?: return false
        return near(distance, event.sensor.maximumRange)
    }
}
