package com.ultramusicplayer.app.trim

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.abs

/** Waveform extraction, clip export (AAC/M4A via Media3 Transformer) and ringtone helpers. */
@UnstableApi
object AudioTrimmer {
    private const val TAG = "AudioTrimmer"

    /**
     * Builds a coarse waveform by seeking to [buckets] evenly spaced points and decoding one
     * buffer at each, which is fast even for long files. Values are normalised to 0..1.
     */
    suspend fun waveform(context: Context, uri: Uri, buckets: Int = 160): FloatArray? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs <= 0) return@withContext null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            val peaks = FloatArray(buckets)
            for (i in 0 until buckets) {
                extractor.seekTo(durationUs * i / buckets, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                decoder.flush()
                var found = 0
                var guard = 0
                var inputEnded = false
                while (found < 2 && guard++ < 60) {
                    if (!inputEnded) {
                        val inIndex = decoder.dequeueInputBuffer(2_000)
                        if (inIndex >= 0) {
                            val buf = decoder.getInputBuffer(inIndex)
                            val size = if (buf == null) -1 else extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = decoder.dequeueOutputBuffer(info, 2_000)
                    if (outIndex >= 0) {
                        if (info.size > 0) {
                            decoder.getOutputBuffer(outIndex)?.let { out ->
                                out.position(info.offset)
                                out.limit(info.offset + info.size)
                                val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                                var peak = 0
                                while (shorts.hasRemaining()) {
                                    val v = abs(shorts.get().toInt())
                                    if (v > peak) peak = v
                                }
                                peaks[i] = maxOf(peaks[i], peak / 32768f)
                                found++
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }
            val max = peaks.maxOrNull()?.takeIf { it > 0f } ?: 1f
            FloatArray(buckets) { (peaks[it] / max).coerceIn(0.03f, 1f) }
        } catch (e: Exception) {
            Log.w(TAG, "Waveform failed", e)
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    /** Exports [startMs]..[endMs] of [uri] to an AAC .m4a file. Must be called on the main thread. */
    suspend fun exportClip(context: Context, uri: Uri, startMs: Long, endMs: Long, output: File): Throwable? =
        suspendCancellableCoroutine { cont ->
            output.delete()
            val item = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build(),
                )
                .build()
            val edited = EditedMediaItem.Builder(item).setRemoveVideo(true).build()
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (cont.isActive) cont.resume(exportException)
                    }
                })
                .build()
            cont.invokeOnCancellation { transformer.cancel() }
            transformer.start(edited, output.absolutePath)
        }

    /** Copies an exported clip into shared storage (Music/Ultra Clips or Ringtones). */
    suspend fun saveToMediaStore(context: Context, file: File, title: String, asRingtone: Boolean): Uri? = withContext(Dispatchers.IO) {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "Clip" }
        val displayName = "$safe.m4a"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, safe)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_RINGTONE, asRingtone)
            put(MediaStore.Audio.Media.IS_MUSIC, !asRingtone)
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    if (asRingtone) Environment.DIRECTORY_RINGTONES else "${Environment.DIRECTORY_MUSIC}/Ultra Clips",
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(
                    if (asRingtone) Environment.DIRECTORY_RINGTONES else Environment.DIRECTORY_MUSIC,
                )
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Audio.Media.DATA, File(dir, "${safe}_${System.currentTimeMillis() / 1000}.m4a").absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        try {
            val uri = resolver.insert(collection, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "Saving clip failed", e)
            null
        }
    }

    fun canSetRingtone(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun setAsRingtone(context: Context, uri: Uri): Boolean = try {
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Setting ringtone failed", e)
        false
    }
}
