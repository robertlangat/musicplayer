package com.ultramusicplayer.app.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Media3 audio processor that applies the five-band EQ to the player's own PCM stream.
 * Because it runs inside the playback pipeline it behaves identically on every device,
 * regardless of how many bands the platform equalizer offers.
 */
@UnstableApi
class FiveBandEqProcessor : BaseAudioProcessor() {
    private var bank: BiquadBank? = null
    private var appliedVersion = -1
    private var wasEnabled = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Only 16-bit PCM is processed; anything else passes through untouched (inactive).
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun onFlush() {
        bank = BiquadBank(inputAudioFormat.sampleRate, inputAudioFormat.channelCount.coerceAtLeast(1))
        appliedVersion = -1
    }

    override fun onReset() {
        bank = null
        appliedVersion = -1
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        val params = EqParams.snapshot()
        val b = bank
        if (!params.enabled || b == null) {
            wasEnabled = false
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (params.version != appliedVersion || !wasEnabled) {
            b.setGains(params.gains)
            if (!wasEnabled) b.reset()
            appliedVersion = params.version
            wasEnabled = true
        }
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        var ch = 0
        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.short / 32768.0
            val y = b.process(sample, ch) * 32768.0
            out.putShort(y.coerceIn(-32768.0, 32767.0).toInt().toShort())
            ch++
            if (ch == channels) ch = 0
        }
        // An odd trailing byte cannot occur for 16-bit PCM, but never leave input unconsumed.
        if (inputBuffer.hasRemaining()) out.put(inputBuffer)
        out.flip()
    }
}
