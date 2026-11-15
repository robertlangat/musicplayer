package com.ultramusicplayer.app.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ultramusicplayer.app.data.LibraryRepository
import com.ultramusicplayer.app.data.LyricFile
import com.ultramusicplayer.app.data.Song
import com.ultramusicplayer.app.data.TagIo
import com.ultramusicplayer.app.data.db.LyricLinkDao
import com.ultramusicplayer.app.data.db.LyricLinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface LyricsResult {
    data class Synced(val lines: List<LrcLine>, val source: String) : LyricsResult
    data class Plain(val text: String, val source: String) : LyricsResult
    /** Several files matched by title/artist; the user picks one. */
    data class Choose(val candidates: List<LyricFile>) : LyricsResult
    data object NotFound : LyricsResult
}

/**
 * Lyric lookup order:
 * 1. a file the user picked manually for this song,
 * 2. <same basename>.lrc / .txt in the song's own folder,
 * 3. lyrics embedded in the audio file's tags,
 * 4. files in accessible folders whose normalised name matches title + artist.
 */
class LyricsRepository(
    private val context: Context,
    private val library: LibraryRepository,
    private val links: LyricLinkDao,
) {
    suspend fun load(song: Song, allowChooser: Boolean = true): LyricsResult = withContext(Dispatchers.IO) {
        links.get(song.id)?.let { link ->
            readUri(Uri.parse(link.lyricUri))?.let { return@withContext toResult(it, "Chosen file") }
        }

        exactMatch(song)?.let { file ->
            readUri(file.uri)?.let { return@withContext toResult(it, file.name) }
        }

        TagIo.readEmbeddedLyrics(context, song)?.takeIf { it.isNotBlank() }?.let {
            return@withContext toResult(it, "Embedded in file")
        }

        val candidates = candidates(song)
        when {
            candidates.size == 1 -> readUri(candidates[0].uri)?.let { toResult(it, candidates[0].name) } ?: LyricsResult.NotFound
            candidates.size > 1 && allowChooser -> LyricsResult.Choose(candidates)
            candidates.size > 1 -> readUri(candidates[0].uri)?.let { toResult(it, candidates[0].name) } ?: LyricsResult.NotFound
            else -> LyricsResult.NotFound
        }
    }

    /** Exact basename in the same folder. */
    private fun exactMatch(song: Song): LyricFile? {
        for (ext in listOf("lrc", "LRC", "txt", "TXT")) {
            val f = File(song.folderPath, "${song.baseName}.$ext")
            try {
                if (f.isFile && f.canRead()) return LyricFile(Uri.fromFile(f), f.name, song.folderPath)
            } catch (_: SecurityException) {
            }
        }
        val folderKey = song.folderPath.lowercase()
        return library.lyricFiles.value
            .filter { it.folderPath.lowercase() == folderKey && it.baseName.equals(song.baseName, ignoreCase = true) }
            .minByOrNull { if (it.name.endsWith(".lrc", true)) 0 else 1 }
    }

    /** Normalised title/artist matches anywhere in accessible folders, same folder first. */
    fun candidates(song: Song): List<LyricFile> =
        library.lyricFiles.value
            .filter { LyricMatcher.matches(it.baseName, song.title, song.artist) || it.baseName.equals(song.baseName, true) }
            .sortedWith(compareBy({ if (it.folderPath.equals(song.folderPath, true)) 0 else 1 }, { if (it.name.endsWith(".lrc", true)) 0 else 1 }))

    suspend fun loadFile(file: LyricFile): LyricsResult = withContext(Dispatchers.IO) {
        readUri(file.uri)?.let { toResult(it, file.name) } ?: LyricsResult.NotFound
    }

    /** Remembers a manually chosen lyric file for the song. */
    suspend fun link(song: Song, uri: Uri, persist: Boolean): LyricsResult = withContext(Dispatchers.IO) {
        if (persist) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not persist permission for $uri", e)
            }
        }
        links.upsert(LyricLinkEntity(song.id, uri.toString()))
        readUri(uri)?.let { toResult(it, "Chosen file") } ?: LyricsResult.NotFound
    }

    suspend fun unlink(song: Song) = links.delete(song.id)

    private fun toResult(text: String, source: String): LyricsResult =
        if (LrcParser.isSynced(text)) {
            val lines = LrcParser.parse(text)
            if (lines.isEmpty()) LyricsResult.Plain(text, source) else LyricsResult.Synced(lines, source)
        } else {
            LyricsResult.Plain(text.trim(), source)
        }

    private fun readUri(uri: Uri): String? = try {
        val bytes = if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).readBytes()
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        bytes?.let { decode(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot read lyrics $uri", e)
        null
    }

    private fun decode(bytes: ByteArray): String {
        // UTF-8 (with or without BOM) and UTF-16 with BOM; fall back to Latin-1 for legacy files.
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        val utf8 = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        return if (utf8.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf8
    }

    companion object {
        private const val TAG = "LyricsRepository"
    }
}
