package com.ultramusicplayer.app

import com.ultramusicplayer.app.playback.BiquadBank
import com.ultramusicplayer.app.playback.EqPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class EqualizerTest {
    private val rate = 44_100

    private fun rmsAfter(freq: Double, gains: FloatArray): Double {
        val bank = BiquadBank(rate, 1)
        bank.setGains(gains)
        var sum = 0.0
        val n = rate // one second
        val skip = rate / 10 // ignore the filter's settling time
        for (i in 0 until n) {
            val x = 0.1 * sin(2 * PI * freq * i / rate)
            val y = bank.process(x, 0)
            if (i >= skip) sum += y * y
        }
        return sqrt(sum / (n - skip))
    }

    private val flatRms = 0.1 / sqrt(2.0)

    @Test
    fun ships22Presets() {
        assertEquals(22, EqPresets.presets.size)
        EqPresets.presets.values.forEach { assertEquals(5, it.size) }
        listOf(
            "Flat", "Acoustic", "Bass Boost", "Bass Reducer", "Classical", "Dance", "Deep", "Electronic",
            "Hip-Hop", "Jazz", "Latin", "Loudness", "Lounge", "Piano", "Pop", "R&B", "Rock",
            "Small Speakers", "Spoken Word", "Treble Boost", "Treble Reducer", "Vocal Boost",
        ).forEach { assertTrue("missing $it", EqPresets.presets.containsKey(it)) }
    }

    @Test
    fun flatIsTransparent() {
        val rms = rmsAfter(1000.0, FloatArray(5))
        assertEquals(flatRms, rms, flatRms * 0.01)
    }

    @Test
    fun bassBoostRaisesLowFrequencies() {
        val gains = floatArrayOf(12f, 0f, 0f, 0f, 0f)
        val low = rmsAfter(60.0, gains)
        val high = rmsAfter(8000.0, gains)
        // +12 dB at 60 Hz minus 6 dB of headroom ≈ +6 dB relative to the input.
        assertTrue("60 Hz should be boosted: $low vs $flatRms", low > flatRms * 1.7)
        // Far from the band only the headroom reduction (-6 dB) applies.
        assertTrue("8 kHz should be mostly untouched: $high", high < flatRms * 0.6 && high > flatRms * 0.4)
    }

    @Test
    fun cutLowersTheBand() {
        val rms = rmsAfter(3600.0, floatArrayOf(0f, 0f, 0f, -12f, 0f))
        assertTrue("3.6 kHz should be cut by ~12 dB: $rms", rms < flatRms * 0.35)
    }

    @Test
    fun bandsAboveNyquistAreSkippedForLowSampleRates() {
        val bank = BiquadBank(8000, 1)
        bank.setGains(floatArrayOf(0f, 0f, 0f, 0f, 12f)) // 14 kHz cannot exist at 8 kHz
        val out = bank.process(0.5, 0)
        assertEquals(0.5, out, 1e-9)
    }

    @Test
    fun interleavedStereoKeepsChannelsSeparate() {
        val bank = BiquadBank(rate, 2)
        bank.setGains(floatArrayOf(0f, 0f, 0f, 0f, 0f))
        val samples = shortArrayOf(1000, -1000, 2000, -2000)
        bank.processInterleaved(samples)
        assertEquals(listOf<Short>(1000, -1000, 2000, -2000), samples.toList())
    }
}
