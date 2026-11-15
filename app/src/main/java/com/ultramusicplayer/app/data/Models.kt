package com.ultramusicplayer.app.data

import android.net.Uri

/** One playable track, from MediaStore or from a user-added folder (Storage Access Framework). */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAddedSec: Long,
    val trackNumber: Int,
    val year: Int,
    /** Absolute-style path such as /storage/emulated/0/Music/a.mp3 ("" when unknown). */
    val filePath: String,
    /** Absolute-style folder path such as /storage/emulated/0/Music. */
    val folderPath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val fromUserFolder: Boolean = false,
    val hasOverride: Boolean = false,
) {
    val baseName: String get() = fileName.substringBeforeLast('.', fileName)
    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()

    /** Lower-cased haystack used by search; computed once per Song instance. */
    val searchKey: String by lazy {
        listOf(title, fileName, album, artist, albumArtist, genre, folderPath)
            .joinToString("\u0001")
            .lowercase()
    }
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val cover: Song get() = songs.first()
}

/** Artist, album artist or genre: a name with its songs. */
data class SongGroup(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.album.lowercase() }.distinct().size
}

class FolderNode(
    val path: String,
    val name: String,
    val children: List<FolderNode>,
    val songs: List<Song>,
) {
    val totalSongs: Int = songs.size + children.sumOf { it.totalSongs }

    fun allSongs(): List<Song> {
        val out = ArrayList<Song>(totalSongs)
        fun walk(n: FolderNode) {
            out.addAll(n.songs)
            n.children.forEach { walk(it) }
        }
        walk(this)
        return out
    }
}

/** A lyric file discovered in an accessible folder. */
data class LyricFile(val uri: Uri, val name: String, val folderPath: String) {
    val baseName: String get() = name.substringBeforeLast('.', name)
}

object PathUtils {
    private val emulated = Regex("^/storage/emulated/\\d+")
    private val volume = Regex("^/storage/([^/]+)")

    /** Root of the storage volume that contains [path]. */
    fun volumeRoot(path: String): String {
        emulated.find(path)?.let { return it.value }
        volume.find(path)?.let { m -> if (m.groupValues[1] != "emulated" && m.groupValues[1] != "self") return m.value }
        return "/"
    }

    fun volumeLabel(root: String): String = when {
        root.startsWith("/storage/emulated") -> "Internal storage"
        root == "/" -> "Device"
        else -> "SD card (${root.substringAfterLast('/')})"
    }

    /** Human readable folder location, e.g. "Internal storage/Music/Album". */
    fun pretty(path: String): String {
        val root = volumeRoot(path)
        val rest = path.removePrefix(root).trim('/')
        val label = volumeLabel(root)
        return if (rest.isEmpty()) label else "$label/$rest"
    }

    /** Converts a SAF document id such as "primary:Music/x.mp3" to an absolute-style path. */
    fun docIdToPath(docId: String): String {
        val volume = docId.substringBefore(':', "")
        val rel = docId.substringAfter(':', docId).trim('/')
        val base = when {
            volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0"
            volume.isEmpty() -> ""
            volume.startsWith("raw") || docId.startsWith("/") -> return docId.removePrefix("raw:")
            else -> "/storage/$volume"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    fun parent(path: String): String = path.substringBeforeLast('/', "")

    fun dedupeKey(path: String): String {
        // Compare paths case-insensitively and ignore which user profile alias was used.
        return path.lowercase().replace(emulated, "/storage/emulated/0")
    }
}

object AudioFormats {
    val audioExtensions = setOf(
        "mp3", "m4a", "m4b", "mp4", "aac", "wav", "flac", "ogg", "oga", "opus",
        "amr", "awb", "mka", "3ga", "weba",
    )
    val lyricExtensions = setOf("lrc", "txt")
    /** Formats whose tags jaudiotagger can rewrite. */
    val taggable = setOf("mp3", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "wav")
}
