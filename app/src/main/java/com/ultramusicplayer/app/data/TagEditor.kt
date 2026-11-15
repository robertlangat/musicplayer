package com.ultramusicplayer.app.data

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.ultramusicplayer.app.data.db.OverrideDao
import com.ultramusicplayer.app.data.db.TagOverrideEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

data class TagEdit(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val trackNumber: Int?,
    val year: Int?,
)

sealed interface TagSaveResult {
    /** Tags were written into the audio file itself. */
    data object WrittenToFile : TagSaveResult
    /** Android must ask the user before the app may modify this file. */
    data class NeedsUserConsent(val intentSender: IntentSender) : TagSaveResult
    /** Android 8/9: the storage write permission is required first. */
    data object NeedsLegacyPermission : TagSaveResult
    /** Saved inside the app only; the file was not modified. */
    data class SavedAsOverride(val reason: String) : TagSaveResult
    data class Failed(val reason: String) : TagSaveResult
}

/** Shared jaudiotagger helpers (tag writing and embedded lyric reading). */
object TagIo {
    private const val TAG = "TagIo"
    private const val MAX_COPY_BYTES = 80L * 1024 * 1024

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
        try {
            TagOptionSingleton.getInstance().isAndroid = true
        } catch (_: Throwable) {
        }
    }

    fun canWriteFormat(song: Song) = song.extension in AudioFormats.taggable

    /** Gives jaudiotagger a real file: the original path when readable, otherwise a cache copy. */
    fun readableFile(context: Context, song: Song): File? {
        val direct = File(song.filePath)
        try {
            if (song.filePath.isNotEmpty() && direct.isFile && direct.canRead()) return direct
        } catch (_: SecurityException) {
        }
        if (song.sizeBytes > MAX_COPY_BYTES) return null
        return copyToCache(context, song)
    }

    fun copyToCache(context: Context, song: Song): File? {
        return try {
            val ext = song.extension.ifEmpty { "mp3" }
            val tmp = File(context.cacheDir, "tag_${song.id.toString().replace('-', 'n')}.$ext")
            val input = context.contentResolver.openInputStream(song.uri) ?: return null
            input.use { stream -> tmp.outputStream().use { stream.copyTo(it) } }
            tmp
        } catch (e: Exception) {
            Log.w(TAG, "Copy failed for ${song.fileName}", e)
            null
        }
    }

    fun readEmbeddedLyrics(context: Context, song: Song): String? {
        if (!canWriteFormat(song)) return null
        val file = readableFile(context, song) ?: return null
        return try {
            AudioFileIO.read(file).tag?.getFirst(FieldKey.LYRICS)
        } catch (e: Throwable) {
            null
        } finally {
            if (file.parentFile == context.cacheDir) file.delete()
        }
    }
}

/**
 * Writes tags back to files where the format allows and the user grants access, otherwise
 * saves a clearly labelled app-only override.
 */
class TagEditor(private val context: Context, private val overrides: OverrideDao) {

    suspend fun save(song: Song, edit: TagEdit, consentDenied: Boolean = false): TagSaveResult = withContext(Dispatchers.IO) {
        if (consentDenied) return@withContext saveOverride(song, edit, "Write access was not granted")
        if (!TagIo.canWriteFormat(song)) {
            return@withContext saveOverride(song, edit, ".${song.extension.ifEmpty { "?" }} files can't store editable tags")
        }
        if (!song.fromUserFolder) {
            when {
                Build.VERSION.SDK_INT >= 30 -> {
                    val granted = context.checkUriPermission(
                        song.uri, Process.myPid(), Process.myUid(),
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        val request = MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri))
                        return@withContext TagSaveResult.NeedsUserConsent(request.intentSender)
                    }
                }
                Build.VERSION.SDK_INT <= 28 -> {
                    val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    if (!ok) return@withContext TagSaveResult.NeedsLegacyPermission
                }
            }
        }
        try {
            writeTags(song, edit)
            overrides.delete(song.id)
            if (song.filePath.isNotEmpty() && !song.fromUserFolder) {
                MediaScannerConnection.scanFile(context, arrayOf(song.filePath), null, null)
            }
            TagSaveResult.WrittenToFile
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException) {
                TagSaveResult.NeedsUserConsent(e.userAction.actionIntent.intentSender)
            } else {
                saveOverride(song, edit, "The file is read-only")
            }
        } catch (e: Throwable) {
            Log.w("TagEditor", "Tag write failed", e)
            saveOverride(song, edit, "The file's tags could not be rewritten (${e.javaClass.simpleName})")
        }
    }

    private fun writeTags(song: Song, edit: TagEdit) {
        // Work on a private copy, then stream it back over the original in one go.
        val tmp = TagIo.copyToCache(context, song) ?: throw IllegalStateException("Cannot read file")
        try {
            val audio = AudioFileIO.read(tmp)
            val tag = audio.tagOrCreateAndSetDefault
            fun put(key: FieldKey, value: String) {
                if (value.isBlank()) {
                    try {
                        tag.deleteField(key)
                    } catch (_: Exception) {
                    }
                } else {
                    tag.setField(key, value.trim())
                }
            }
            put(FieldKey.TITLE, edit.title)
            put(FieldKey.ARTIST, edit.artist)
            put(FieldKey.ALBUM, edit.album)
            put(FieldKey.ALBUM_ARTIST, edit.albumArtist)
            put(FieldKey.GENRE, edit.genre)
            put(FieldKey.TRACK, edit.trackNumber?.takeIf { it > 0 }?.toString().orEmpty())
            put(FieldKey.YEAR, edit.year?.takeIf { it > 0 }?.toString().orEmpty())
            audio.commit()
            val out = context.contentResolver.openOutputStream(song.uri, "wt") ?: throw IllegalStateException("No output stream")
            out.use { stream -> tmp.inputStream().use { it.copyTo(stream) } }
        } finally {
            tmp.delete()
        }
    }

    private suspend fun saveOverride(song: Song, edit: TagEdit, reason: String): TagSaveResult {
        overrides.upsert(
            TagOverrideEntity(
                songId = song.id,
                title = edit.title,
                artist = edit.artist,
                album = edit.album,
                albumArtist = edit.albumArtist,
                genre = edit.genre,
                trackNumber = edit.trackNumber,
                year = edit.year,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return TagSaveResult.SavedAsOverride(reason)
    }

    suspend fun clearOverride(song: Song) = overrides.delete(song.id)

    @Suppress("unused")
    private fun Uri.isMediaStore() = authority == MediaStore.AUTHORITY
}
