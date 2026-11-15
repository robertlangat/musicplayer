package com.ultramusicplayer.app.playback

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Loads embedded/album artwork for a song URI, scaled down to [maxPx]. */
object ArtworkLoader {
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun load(context: Context, uri: Uri, albumId: Long, maxPx: Int): Bitmap? {
        val key = uri.toString()
        if (key in missing) return null
        if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(maxPx, maxPx), null)
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT < 29 && albumId > 0) {
            try {
                val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                val bytes = context.contentResolver.openInputStream(art)?.use { it.readBytes() }
                val bitmap = bytes?.let { decodeSampled(it, maxPx) }
                if (bitmap != null) return bitmap
            } catch (_: Exception) {
            }
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            val bitmap = picture?.let { decodeSampled(it, maxPx) }
            if (bitmap != null) return bitmap
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        missing += key
        return null
    }

    fun forget(uri: Uri) {
        missing -= uri.toString()
    }

    fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Coil model for song artwork. */
data class ArtRequest(val uri: Uri, val albumId: Long)

class ArtKeyer : Keyer<ArtRequest> {
    override fun key(data: ArtRequest, options: Options): String = "art:${data.uri}"
}

class ArtFetcher(
    private val context: Context,
    private val data: ArtRequest,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val px = maxOf(options.size.width.pxOr(512), options.size.height.pxOr(512)).coerceIn(96, 1024)
        val bitmap = ArtworkLoader.load(context, data.uri, data.albumId, px) ?: throw IOException("No artwork")
        return DrawableResult(BitmapDrawable(context.resources, bitmap), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun coil.size.Dimension.pxOr(default: Int): Int =
        if (this is coil.size.Dimension.Pixels) px else default

    class Factory(private val context: Context) : Fetcher.Factory<ArtRequest> {
        override fun create(data: ArtRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtFetcher(context.applicationContext, data, options)
    }
}

/** Media3 bitmap loader so the notification, lock screen and Bluetooth get embedded artwork. */
@UnstableApi
class ArtBitmapLoader(context: Context) : BitmapLoader {
    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit(
        Callable { ArtworkLoader.decodeSampled(data, 512) ?: throw IOException("Cannot decode artwork") },
    )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit(
        Callable { ArtworkLoader.load(appContext, uri, -1L, 512) ?: throw IOException("No artwork") },
    )
}
