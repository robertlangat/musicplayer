package com.ultramusicplayer.app.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Five-band layout and the 22 built-in presets (gains in dB for 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz). */
object EqPresets {
    val FREQUENCIES_HZ = intArrayOf(60, 230, 910, 3600, 14000)
    val BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val CUSTOM = "Custom"

    val presets: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Acoustic" to listOf(4f, 2f, 1f, 3f, 3f),
        "Bass Boost" to listOf(7f, 4f, 0f, 0f, 0f),
        "Bass Reducer" to listOf(-7f, -4f, 0f, 0f, 0f),
        "Classical" to listOf(4f, 3f, -2f, 3f, 4f),
        "Dance" to listOf(6f, 3f, 0f, 3f, 2f),
        "Deep" to listOf(5f, 3f, 1f, -2f, -4f),
        "Electronic" to listOf(5f, 2f, -1f, 2f, 5f),
        "Hip-Hop" to listOf(6f, 4f, -1f, 1f, 3f),
        "Jazz" to listOf(3f, 2f, -1f, 2f, 4f),
        "Latin" to listOf(4f, 0f, -2f, 1f, 5f),
        "Loudness" to listOf(6f, 2f, -1f, 2f, 6f),
        "Lounge" to listOf(-2f, 1f, 3f, 1f, -1f),
        "Piano" to listOf(2f, 1f, 2f, 3f, 2f),
        "Pop" to listOf(-1f, 2f, 4f, 2f, -1f),
        "R&B" to listOf(5f, 4f, -1f, 2f, 3f),
        "Rock" to listOf(5f, 2f, -2f, 2f, 5f),
        "Small Speakers" to listOf(6f, 4f, 1f, -1f, -3f),
        "Spoken Word" to listOf(-3f, -1f, 4f, 4f, 1f),
        "Treble Boost" to listOf(0f, 0f, 0f, 4f, 7f),
        "Treble Reducer" to listOf(0f, 0f, 0f, -4f, -7f),
        "Vocal Boost" to listOf(-2f, -1f, 4f, 4f, 1f),
    )
}

/** Thread-safe hand-off of EQ settings from the UI/service to the audio thread. */
object EqParams {
    class Snapshot(val enabled: Boolean, val gains: FloatArray, val version: Int)

    @Volatile
    private var current = Snapshot(false, FloatArray(5), 0)

    fun update(enabled: Boolean, gains: List<Float>) {
        val g = FloatArray(5) { i -> gains.getOrElse(i) { 0f }.coerceIn(EqPresets.MIN_DB, EqPresets.MAX_DB) }
        synchronized(this) { current = Snapshot(enabled, g, current.version + 1) }
    }

    fun snapshot(): Snapshot = current
}

/**
 * Five cascaded RBJ peaking biquads (transposed direct form II), one state per channel.
 * Pure Kotlin so it can be unit-tested on the JVM.
 */
class BiquadBank(private val sampleRate: Int, private val channels: Int) {
    private val bands = EqPresets.FREQUENCIES_HZ.size
    private val b0 = DoubleArray(bands)
    private val b1 = DoubleArray(bands)
    private val b2 = DoubleArray(bands)
    private val a1 = DoubleArray(bands)
    private val a2 = DoubleArray(bands)
    private val active = BooleanArray(bands)
    private val z1 = Array(channels) { DoubleArray(bands) }
    private val z2 = Array(channels) { DoubleArray(bands) }
    var preGain = 1.0
        private set

    fun setGains(gainsDb: FloatArray, q: Double = 1.0) {
        var maxBoost = 0f
        for (b in 0 until bands) {
            val f = EqPresets.FREQUENCIES_HZ[b].toDouble()
            val db = gainsDb.getOrElse(b) { 0f }
            // Bands too close to Nyquist (e.g. 14 kHz on an 8 kHz AMR file) are skipped.
            if (kotlin.math.abs(db) < 0.05f || f >= sampleRate * 0.45) {
                active[b] = false
                continue
            }
            if (db > maxBoost) maxBoost = db
            val a = 10.0.pow(db / 40.0)
            val w0 = 2.0 * PI * f / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosw = cos(w0)
            val a0 = 1.0 + alpha / a
            b0[b] = (1.0 + alpha * a) / a0
            b1[b] = (-2.0 * cosw) / a0
            b2[b] = (1.0 - alpha * a) / a0
            a1[b] = (-2.0 * cosw) / a0
            a2[b] = (1.0 - alpha / a) / a0
            active[b] = true
        }
        // Leave some headroom when boosting so loud masters clip less.
        preGain = 10.0.pow(-(maxBoost * 0.5) / 20.0)
    }

    fun reset() {
        for (c in 0 until channels) {
            z1[c].fill(0.0)
            z2[c].fill(0.0)
        }
    }

    fun process(sample: Double, channel: Int): Double {
        var x = sample * preGain
        val s1 = z1[channel]
        val s2 = z2[channel]
        for (b in 0 until bands) {
            if (!active[b]) continue
            val y = b0[b] * x + s1[b]
            s1[b] = b1[b] * x - a1[b] * y + s2[b]
            s2[b] = b2[b] * x - a2[b] * y
            x = y
        }
        return x
    }

    /** Processes interleaved 16-bit PCM in place. */
    fun processInterleaved(samples: ShortArray) {
        var ch = 0
        for (i in samples.indices) {
            val y = process(samples[i] / 32768.0, ch) * 32768.0
            samples[i] = y.coerceIn(-32768.0, 32767.0).toInt().toShort()
            ch++
            if (ch == channels) ch = 0
        }
    }
}
