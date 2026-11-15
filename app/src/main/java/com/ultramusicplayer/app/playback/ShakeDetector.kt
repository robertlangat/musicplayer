package com.ultramusicplayer.app.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Detects a deliberate shake: two strong peaks within half a second, followed by a cooldown
 * so a single movement can never skip more than one track.
 */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var running = false
    private var lastTrigger = 0L
    private var windowStart = 0L
    private var peaks = 0

    /** 0 = needs a hard shake, 1 = reacts to a light shake. */
    var sensitivity: Float = 0.5f

    val isAvailable: Boolean get() = accelerometer != null

    fun start() {
        if (running || accelerometer == null) return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
        peaks = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(x * x + y * y + z * z)
        if (gForce < threshold()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTrigger < COOLDOWN_MS) return
        if (now - windowStart > WINDOW_MS) {
            windowStart = now
            peaks = 0
        }
        peaks++
        if (peaks >= 2) {
            peaks = 0
            lastTrigger = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun threshold(): Float = 2.8f - 1.5f * sensitivity.coerceIn(0f, 1f)

    companion object {
        const val COOLDOWN_MS = 1500L
        const val WINDOW_MS = 500L
    }
}
