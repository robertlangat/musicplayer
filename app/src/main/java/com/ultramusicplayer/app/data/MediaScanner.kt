package com.ultramusicplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Reads audio from MediaStore and walks folders the user granted through the system folder
 * picker (Storage Access Framework), which also covers SD cards and folders MediaStore skips.
 */
class MediaScanner(private val context: Context) {

    private val resolver get() = context.contentResolver

    data class DocEntry(
        val uri: Uri,
        val name: String,
        val mime: String,
        val folderPath: String,
        val size: Long,
        val lastModified: Long,
    )

    fun queryMediaStore(includeAllAudio: Boolean): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val base = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
        )
        val withAlbumArtist = base + "album_artist"
        val withGenre = if (Build.VERSION.SDK_INT >= 30) withAlbumArtist + MediaStore.Audio.Media.GENRE else withAlbumArtist
        val selection = if (includeAllAudio) {
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }

        val cursor: Cursor? = try {
            resolver.query(collection, withGenre.toTypedArray(), selection, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Extended projection failed, retrying with base columns", e)
            try {
                resolver.query(collection, base.toTypedArray(), selection, null, null)
            } catch (e2: SecurityException) {
                null
            }
        }
        val legacyGenres = if (Build.VERSION.SDK_INT < 30) legacyGenreMap() else emptyMap()
        val out = ArrayList<Song>()
        cursor?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val iDate = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val iTrack = c.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val iYear = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val iData = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val iName = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val iMime = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val iSize = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val iAlbumArtist = c.getColumnIndex("album_artist")
            val iGenre = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val data = c.str(iData)
                val name = c.str(iName).ifBlank { data.substringAfterLast('/') }
                val title = c.str(iTitle).ifBlank { name.substringBeforeLast('.') }
                val artist = cleanUnknown(c.str(iArtist))
                out += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    artist = artist,
                    album = cleanUnknown(c.str(iAlbum), "Unknown album"),
                    albumArtist = cleanUnknown(c.str(iAlbumArtist), "").ifBlank { artist },
                    genre = if (iGenre >= 0) c.str(iGenre) else legacyGenres[id].orEmpty(),
                    albumId = if (iAlbumId >= 0) c.getLong(iAlbumId) else -1L,
                    durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                    dateAddedSec = if (iDate >= 0) c.getLong(iDate) else 0L,
                    trackNumber = if (iTrack >= 0) c.getInt(iTrack) % 1000 else 0,
                    year = if (iYear >= 0) c.getInt(iYear) else 0,
                    filePath = data,
                    folderPath = PathUtils.parent(data),
                    fileName = name,
                    mimeType = c.str(iMime),
                    sizeBytes = if (iSize >= 0) c.getLong(iSize) else 0L,
                )
            }
        }
        return out
    }

    private fun legacyGenreMap(): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            resolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { g ->
                while (g.moveToNext()) {
                    val genreId = g.getLong(0)
                    val name = g.getString(1) ?: continue
                    resolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null,
                    )?.use { m -> while (m.moveToNext()) map[m.getLong(0)] = name }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Genre lookup failed", e)
        }
        return map
    }

    /** Walks a granted folder tree, reporting audio and lyric documents. */
    fun walkTree(treeUri: Uri, onEntry: (DocEntry) -> Unit) {
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return
        }
        val stack = ArrayDeque<String>()
        stack.addLast(rootId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_FOLDERS) {
            val docId = stack.removeLast()
            visited++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val folderPath = PathUtils.docIdToPath(docId)
            try {
                resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val childId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) stack.addLast(childId)
                            continue
                        }
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in AudioFormats.audioExtensions || ext in AudioFormats.lyricExtensions || mime.startsWith("audio/")) {
                            onEntry(
                                DocEntry(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                                    name = name,
                                    mime = mime,
                                    folderPath = folderPath,
                                    size = if (c.isNull(3)) 0L else c.getLong(3),
                                    lastModified = if (c.isNull(4)) 0L else c.getLong(4),
                                ),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot list $docId", e)
            }
        }
    }

    /** Builds a Song for a document that MediaStore does not know about. */
    fun songFromDocument(doc: DocEntry): Song? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, doc.uri)
            fun meta(key: Int) = retriever.extractMetadata(key).orEmpty().trim()
            val artist = cleanUnknown(meta(MediaMetadataRetriever.METADATA_KEY_ARTIST))
            val track = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).substringBefore('/').toIntOrNull() ?: 0
            Song(
                id = stableNegativeId(doc.uri.toString()),
                uri = doc.uri,
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { doc.name.substringBeforeLast('.') },
                artist = artist,
                album = cleanUnknown(meta(MediaMetadataRetriever.METADATA_KEY_ALBUM), "Unknown album"),
                albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).ifBlank { artist },
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                albumId = -1L,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L,
                dateAddedSec = doc.lastModified / 1000,
                trackNumber = track,
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR).take(4).toIntOrNull() ?: 0,
                filePath = "${doc.folderPath}/${doc.name}",
                folderPath = doc.folderPath,
                fileName = doc.name,
                mimeType = doc.mime,
                sizeBytes = doc.size,
                fromUserFolder = true,
            )
        } catch (e: Exception) {
            // Damaged or undecodable file: skip it rather than failing the whole scan.
            Log.w(TAG, "Unreadable audio ${doc.name}", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Lyric files sitting next to songs in folders readable through the file API. */
    fun lyricFilesInFolders(folders: Collection<String>): List<LyricFile> {
        val out = ArrayList<LyricFile>()
        for (folder in folders) {
            val files = try {
                File(folder).listFiles()
            } catch (e: SecurityException) {
                null
            } ?: continue
            for (f in files) {
                if (f.isFile && f.extension.lowercase() in AudioFormats.lyricExtensions && f.canRead()) {
                    out += LyricFile(Uri.fromFile(f), f.name, folder)
                }
            }
        }
        return out
    }

    private fun Cursor.str(index: Int): String =
        if (index < 0 || isNull(index)) "" else getString(index).orEmpty()

    companion object {
        private const val TAG = "MediaScanner"
        private const val MAX_FOLDERS = 20_000

        fun cleanUnknown(value: String, fallback: String = "Unknown artist"): String {
            val v = value.trim()
            return if (v.isEmpty() || v == "<unknown>") fallback else v
        }

        fun stableNegativeId(key: String): Long {
            var h = 1125899906842597L
            for (ch in key) h = 31 * h + ch.code
            return -(h and Long.MAX_VALUE) - 1
        }
    }
}
