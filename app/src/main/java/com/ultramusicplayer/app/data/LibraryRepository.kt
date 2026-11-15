package com.ultramusicplayer.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.ultramusicplayer.app.data.db.OverrideDao
import com.ultramusicplayer.app.data.db.TagOverrideEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the library: scans MediaStore plus user-added folders, merges them without duplicates,
 * applies app-only tag overrides and refreshes automatically when MediaStore changes.
 */
class LibraryRepository(
    private val context: Context,
    private val scanner: MediaScanner,
    private val settings: SettingsRepository,
    overrideDao: OverrideDao,
    private val scope: CoroutineScope,
) {
    private val rawSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _hasScanned = MutableStateFlow(false)
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()
    private val _lyricFiles = MutableStateFlow<List<LyricFile>>(emptyList())
    val lyricFiles: StateFlow<List<LyricFile>> = _lyricFiles.asStateFlow()

    val library: StateFlow<Library> = combine(rawSongs, overrideDao.observeAll()) { songs, overrides ->
        Library.build(applyOverrides(songs, overrides))
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, Library.EMPTY)

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var debounceJob: Job? = null
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // MediaStore fires bursts of change events while files are copied; debounce them.
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(1500)
                requestRefresh()
            }
        }
    }

    init {
        scope.launch {
            for (r in requests) {
                runScan()
            }
        }
    }

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Queue a rescan. Concurrent requests collapse into one follow-up scan. */
    fun requestRefresh() {
        requests.trySend(Unit)
    }

    fun startObserving() {
        if (observerRegistered) return
        try {
            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            observerRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Observer registration failed", e)
        }
    }

    private suspend fun runScan() {
        _scanning.value = true
        try {
            val result = withContext(Dispatchers.IO) { scanAll() }
            rawSongs.value = result.first
            _lyricFiles.value = result.second
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
        } finally {
            _scanning.value = false
            _hasScanned.value = true
        }
    }

    private suspend fun scanAll(): Pair<List<Song>, List<LyricFile>> {
        val prefs = settings.current()
        val mediaStoreSongs = if (hasAudioPermission()) scanner.queryMediaStore(prefs.includeAllAudio) else emptyList()
        // Publish MediaStore results early so the UI is responsive during folder walks.
        if (rawSongs.value.isEmpty() && mediaStoreSongs.isNotEmpty()) rawSongs.value = mediaStoreSongs

        val known = HashSet<String>(mediaStoreSongs.size * 2)
        mediaStoreSongs.forEach { if (it.filePath.isNotEmpty()) known += PathUtils.dedupeKey(it.filePath) }

        val songs = ArrayList(mediaStoreSongs)
        val lyrics = ArrayList<LyricFile>()
        val persisted = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        for (folder in prefs.folders) {
            if (folder !in persisted) continue // permission revoked; settings screen shows it as unavailable
            val docs = ArrayList<MediaScanner.DocEntry>()
            scanner.walkTree(Uri.parse(folder)) { docs += it }
            for (doc in docs) {
                val ext = doc.name.substringAfterLast('.', "").lowercase()
                if (ext in AudioFormats.lyricExtensions) {
                    lyrics += LyricFile(doc.uri, doc.name, doc.folderPath)
                    continue
                }
                val key = PathUtils.dedupeKey("${doc.folderPath}/${doc.name}")
                if (!known.add(key)) continue // already in MediaStore or another added folder
                scanner.songFromDocument(doc)?.let { songs += it }
            }
        }
        // Lyric files directly readable next to songs (works on Android 10 and older, and
        // anywhere the file API is permitted).
        val songFolders = songs.asSequence().filter { !it.fromUserFolder }.map { it.folderPath }.toSet()
        if (Build.VERSION.SDK_INT <= 29) lyrics += scanner.lyricFilesInFolders(songFolders)
        return songs to lyrics.distinctBy { it.uri }
    }

    private fun applyOverrides(songs: List<Song>, overrides: List<TagOverrideEntity>): List<Song> {
        if (overrides.isEmpty()) return songs
        val map = overrides.associateBy { it.songId }
        return songs.map { s ->
            val o = map[s.id] ?: return@map s
            s.copy(
                title = o.title?.takeIf { it.isNotBlank() } ?: s.title,
                artist = o.artist?.takeIf { it.isNotBlank() } ?: s.artist,
                album = o.album?.takeIf { it.isNotBlank() } ?: s.album,
                albumArtist = o.albumArtist?.takeIf { it.isNotBlank() } ?: s.albumArtist,
                genre = o.genre ?: s.genre,
                trackNumber = o.trackNumber ?: s.trackNumber,
                year = o.year ?: s.year,
                hasOverride = true,
            )
        }
    }

    companion object {
        private const val TAG = "LibraryRepository"
    }
}
