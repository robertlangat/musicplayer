package com.ultramusicplayer.app.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ultramusicplayer.app.data.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object MediaItems {
    const val EXTRA_ALBUM_ID = "album_id"

    fun fromSong(song: Song): MediaItem = build(
        id = song.id.toString(),
        uri = song.uri,
        title = song.title,
        artist = song.artist,
        album = song.album,
        albumArtist = song.albumArtist,
        genre = song.genre,
        albumId = song.albumId,
    )

    fun build(
        id: String,
        uri: Uri,
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        genre: String,
        albumId: Long,
    ): MediaItem {
        val extras = Bundle().apply { putLong(EXTRA_ALBUM_ID, albumId) }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist)
            .setGenre(genre.ifBlank { null })
            // The session's bitmap loader turns the song URI into its embedded artwork.
            .setArtworkUri(uri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(metadata)
            .build()
    }

    /** Restores a playable item from what survives a controller → session hop. */
    fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri ?: return item
        return item.buildUpon().setUri(uri).build()
    }

    fun uriOf(item: MediaItem): Uri? = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri

    fun toJson(item: MediaItem): JSONObject {
        val m = item.mediaMetadata
        return JSONObject()
            .put("id", item.mediaId)
            .put("uri", uriOf(item)?.toString().orEmpty())
            .put("title", m.title?.toString().orEmpty())
            .put("artist", m.artist?.toString().orEmpty())
            .put("album", m.albumTitle?.toString().orEmpty())
            .put("albumArtist", m.albumArtist?.toString().orEmpty())
            .put("genre", m.genre?.toString().orEmpty())
            .put("albumId", m.extras?.getLong(EXTRA_ALBUM_ID, -1L) ?: -1L)
    }

    fun fromJson(o: JSONObject): MediaItem? {
        val uri = o.optString("uri").takeIf { it.isNotEmpty() } ?: return null
        return build(
            id = o.optString("id"),
            uri = Uri.parse(uri),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            albumArtist = o.optString("albumArtist"),
            genre = o.optString("genre"),
            albumId = o.optLong("albumId", -1L),
        )
    }
}

/** Persists the queue, position and shuffle state so playback resumes after the app restarts. */
class QueueStore(context: Context) {
    private val file = File(context.filesDir, "queue.json")

    data class Snapshot(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val repeatMode: Int,
        val shuffled: Boolean,
        val originalOrder: List<MediaItem>?,
    )

    @Synchronized
    fun save(s: Snapshot) {
        try {
            val json = JSONObject()
                .put("index", s.index)
                .put("position", s.positionMs)
                .put("repeat", s.repeatMode)
                .put("shuffled", s.shuffled)
                .put("items", JSONArray().apply { s.items.forEach { put(MediaItems.toJson(it)) } })
            s.originalOrder?.let { list -> json.put("original", JSONArray().apply { list.forEach { put(MediaItems.toJson(it)) } }) }
            val tmp = File(file.parentFile, "queue.json.tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.w("QueueStore", "Save failed", e)
        }
    }

    @Synchronized
    fun load(): Snapshot? = try {
        if (!file.exists()) {
            null
        } else {
            val json = JSONObject(file.readText())
            fun list(name: String): List<MediaItem>? {
                val arr = json.optJSONArray(name) ?: return null
                return (0 until arr.length()).mapNotNull { MediaItems.fromJson(arr.getJSONObject(it)) }
            }
            val items = list("items").orEmpty()
            if (items.isEmpty()) {
                null
            } else {
                Snapshot(
                    items = items,
                    index = json.optInt("index", 0).coerceIn(0, items.size - 1),
                    positionMs = json.optLong("position", 0L),
                    repeatMode = json.optInt("repeat", 0),
                    shuffled = json.optBoolean("shuffled", false),
                    originalOrder = list("original"),
                )
            }
        }
    } catch (e: Exception) {
        Log.w("QueueStore", "Load failed", e)
        null
    }

    fun hasQueue(): Boolean = file.exists() && file.length() > 20
}
