package com.ultramusicplayer.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.ultramusicplayer.app.UltraApp
import com.ultramusicplayer.app.data.AppSettings
import com.ultramusicplayer.app.data.LibrarySearch
import com.ultramusicplayer.app.data.SearchResults
import com.ultramusicplayer.app.data.ShakeAction
import com.ultramusicplayer.app.data.Song
import com.ultramusicplayer.app.data.SongSort
import com.ultramusicplayer.app.data.TagEdit
import com.ultramusicplayer.app.data.TagSaveResult
import com.ultramusicplayer.app.data.ThemeMode
import com.ultramusicplayer.app.data.LyricFile
import com.ultramusicplayer.app.lyrics.LyricsResult
import com.ultramusicplayer.app.playback.ArtworkLoader
import com.ultramusicplayer.app.playback.EqParams
import com.ultramusicplayer.app.playback.EqPresets
import com.ultramusicplayer.app.playback.PlayerConnection
import com.ultramusicplayer.app.ui.theme.Skins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlaylistRow(val rowId: Long, val fullIndex: Int, val song: Song)

/** Lyric panel state for one song. */
data class LyricsUi(val songId: Long? = null, val loading: Boolean = false, val result: LyricsResult? = null)

@OptIn(UnstableApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as UltraApp).container
    private val appContext = app.applicationContext

    val library = container.library.library
    val scanning = container.library.scanning
    val hasScanned = container.library.hasScanned

    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val playlists = container.playlists.summaries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recent: StateFlow<List<Song>> = combine(container.playlists.history, library) { history, lib ->
        history.mapNotNull { lib.byId[it.songId] }.distinctBy { it.id }.take(100)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val player = PlayerConnection(app, viewModelScope)

    val currentSong: StateFlow<Song?> = combine(
        player.state.map { it.current?.mediaId }.distinctUntilChanged(),
        library,
    ) { id, lib -> id?.toLongOrNull()?.let { lib.byId[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _hasPermission = MutableStateFlow(container.library.hasAudioPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val query = MutableStateFlow("")
    val searchResults: StateFlow<SearchResults> = combine(query.debounce(60), library, playlists) { q, lib, pl ->
        LibrarySearch.search(lib, pl, q)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, SearchResults())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    /** Songs waiting for the "Add to playlist" picker. */
    val addToPlaylistRequest = MutableStateFlow<List<Song>?>(null)

    private val _lyrics = MutableStateFlow(LyricsUi())
    val lyrics: StateFlow<LyricsUi> = _lyrics.asStateFlow()

    private val _tagResult = MutableStateFlow<TagSaveResult?>(null)
    val tagResult: StateFlow<TagSaveResult?> = _tagResult.asStateFlow()

    private val _photo = MutableStateFlow(findPhoto())
    val photo: StateFlow<File?> = _photo.asStateFlow()

    init {
        player.connect()
        // Keep the in-process EQ in sync even before the service has started.
        viewModelScope.launch {
            container.settings.settings.collect { EqParams.update(it.eqEnabled, it.eqGains) }
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    // ---- permissions & scanning -------------------------------------------------------------

    fun refreshPermission() {
        val granted = container.library.hasAudioPermission()
        val changed = granted != _hasPermission.value
        _hasPermission.value = granted
        if (changed && granted) container.library.requestRefresh()
    }

    fun rescan() {
        container.library.requestRefresh()
        toast("Refreshing library…")
    }

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                try {
                    appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e2: SecurityException) {
                    toast("Android did not grant access to that folder")
                    return@launch
                }
            }
            container.settings.addFolder(uri.toString())
            container.library.requestRefresh()
            toast("Folder added — scanning")
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            try {
                appContext.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            container.settings.removeFolder(uri)
            container.library.requestRefresh()
        }
    }

    fun isFolderAccessible(uri: String): Boolean =
        appContext.contentResolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isReadPermission }

    // ---- playback shortcuts -------------------------------------------------------------------

    fun play(songs: List<Song>, index: Int) = player.playSongs(songs, index)
    fun shuffle(songs: List<Song>) {
        if (songs.isNotEmpty()) player.playSongs(songs, songs.indices.random(), shuffle = true)
    }
    fun playNext(songs: List<Song>) {
        player.playNext(songs)
        toast(if (songs.size == 1) "“${songs[0].title}” will play next" else "${songs.size} songs will play next")
    }
    fun addToQueue(songs: List<Song>) {
        player.addToQueue(songs)
        toast(if (songs.size == 1) "Added “${songs[0].title}” to the queue" else "Added ${songs.size} songs to the queue")
    }

    // ---- sorting ----------------------------------------------------------------------------------

    fun setSort(sort: SongSort, descending: Boolean) = viewModelScope.launch { container.settings.setSongSort(sort, descending) }

    fun sortSongs(songs: List<Song>, sort: SongSort, descending: Boolean): List<Song> {
        val cmp: Comparator<Song> = when (sort) {
            SongSort.TITLE -> compareBy<Song> { it.title.lowercase() }
            SongSort.ARTIST -> compareBy<Song>({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber })
            SongSort.ALBUM -> compareBy<Song>({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() })
            SongSort.DATE_ADDED -> compareBy<Song>({ it.dateAddedSec }, { it.title.lowercase() })
            SongSort.DURATION -> compareBy<Song>({ it.durationMs }, { it.title.lowercase() })
        }
        return if (descending) songs.sortedWith(cmp.reversed()) else songs.sortedWith(cmp)
    }

    // ---- playlists --------------------------------------------------------------------------------

    fun playlistRows(id: Long): Flow<List<PlaylistRow>> =
        combine(container.playlists.songs(id), library) { rows, lib ->
            rows.mapIndexedNotNull { i, r -> lib.byId[r.songId]?.let { PlaylistRow(r.rowId, i, it) } }
        }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()) = viewModelScope.launch {
        val id = container.playlists.create(name)
        if (songs.isNotEmpty()) container.playlists.add(id, songs)
        toast(if (songs.isEmpty()) "Playlist “$name” created" else "Added ${songs.size} to “$name”")
    }

    fun addToPlaylist(playlistId: Long, name: String, songs: List<Song>) = viewModelScope.launch {
        container.playlists.add(playlistId, songs)
        toast("Added ${if (songs.size == 1) "“${songs[0].title}”" else "${songs.size} songs"} to “$name”")
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { container.playlists.rename(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { container.playlists.delete(id) }
    fun removeFromPlaylist(rowId: Long) = viewModelScope.launch { container.playlists.remove(rowId) }
    fun movePlaylistItem(id: Long, from: Int, to: Int) = viewModelScope.launch { container.playlists.move(id, from, to) }
    fun clearHistory() = viewModelScope.launch { container.playlists.clearHistory() }

    fun saveQueueAsPlaylist(name: String) {
        val lib = library.value
        val songs = player.state.value.queue.mapNotNull { e -> e.mediaId.toLongOrNull()?.let { lib.byId[it] } }
        if (songs.isEmpty()) toast("The queue is empty") else createPlaylist(name, songs)
    }

    // ---- equalizer ---------------------------------------------------------------------------------

    /** Live preview while a slider is dragged (no disk write). */
    fun previewEq(enabled: Boolean, gains: List<Float>) = EqParams.update(enabled, gains)

    fun commitEq(enabled: Boolean, preset: String, gains: List<Float>) {
        EqParams.update(enabled, gains)
        viewModelScope.launch { container.settings.setEq(enabled, preset, gains) }
    }

    fun saveEqCustom(gains: List<Float>) {
        val enabled = settings.value?.eqEnabled ?: true
        viewModelScope.launch {
            container.settings.setEqCustom(gains)
            container.settings.setEq(enabled, EqPresets.CUSTOM, gains)
        }
        toast("Custom EQ profile saved")
    }

    // ---- appearance & behaviour --------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { container.settings.setThemeMode(mode) }
    fun setAccent(argb: Long) = viewModelScope.launch { container.settings.setAccent(argb) }
    fun setSkin(id: String) = viewModelScope.launch { container.settings.setSkin(id) }
    fun setOverlay(v: Float) = viewModelScope.launch { container.settings.setOverlay(v) }
    fun setShake(enabled: Boolean, action: ShakeAction, sensitivity: Float) =
        viewModelScope.launch { container.settings.setShake(enabled, action, sensitivity) }
    fun setIncludeAllAudio(v: Boolean) = viewModelScope.launch {
        container.settings.setIncludeAllAudio(v)
        container.library.requestRefresh()
    }
    fun finishOnboarding() = viewModelScope.launch { container.settings.setOnboardingDone(true) }
    fun restartOnboarding() = viewModelScope.launch { container.settings.setOnboardingDone(false) }

    private fun photoDir() = appContext.filesDir

    private fun findPhoto(): File? =
        photoDir().listFiles { f -> f.name.startsWith("background_") && f.name.endsWith(".jpg") }
            ?.maxByOrNull { it.lastModified() }

    fun setPhotoBackground(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    photoDir().listFiles { f -> f.name.startsWith("background_") }?.forEach { it.delete() }
                    val target = File(photoDir(), "background_${System.currentTimeMillis()}.jpg")
                    appContext.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    target.exists() && target.length() > 0
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                _photo.value = findPhoto()
                container.settings.setSkin(Skins.PHOTO_ID)
            } else {
                toast("Couldn't use that photo")
            }
        }
    }

    // ---- lyrics -----------------------------------------------------------------------------------

    fun loadLyrics(song: Song, force: Boolean = false) {
        if (!force && _lyrics.value.songId == song.id && (_lyrics.value.loading || _lyrics.value.result != null)) return
        _lyrics.value = LyricsUi(song.id, loading = true)
        viewModelScope.launch {
            val result = container.lyrics.load(song)
            if (_lyrics.value.songId == song.id) _lyrics.value = LyricsUi(song.id, false, result)
        }
    }

    fun chooseLyricFile(song: Song, file: LyricFile) {
        _lyrics.value = LyricsUi(song.id, loading = true)
        viewModelScope.launch {
            val result = container.lyrics.link(song, file.uri, persist = false)
            _lyrics.value = LyricsUi(song.id, false, result)
        }
    }

    fun pickLyricUri(song: Song, uri: Uri) {
        _lyrics.value = LyricsUi(song.id, loading = true)
        viewModelScope.launch {
            val result = container.lyrics.link(song, uri, persist = true)
            _lyrics.value = LyricsUi(song.id, false, result)
            if (result is LyricsResult.NotFound) toast("That file could not be read as lyrics")
        }
    }

    fun lyricCandidates(song: Song): List<LyricFile> = container.lyrics.candidates(song)

    fun resetLyricChoice(song: Song) {
        viewModelScope.launch {
            container.lyrics.unlink(song)
            loadLyrics(song, force = true)
        }
    }

    // ---- tag editing --------------------------------------------------------------------------------

    fun saveTags(song: Song, edit: TagEdit, consentDenied: Boolean = false) {
        viewModelScope.launch {
            val result = container.tagEditor.save(song, edit, consentDenied)
            _tagResult.value = result
            if (result is TagSaveResult.WrittenToFile) {
                ArtworkLoader.forget(song.uri)
                container.library.requestRefresh()
            }
        }
    }

    fun clearTagResult() {
        _tagResult.value = null
    }

    fun revertOverride(song: Song) = viewModelScope.launch {
        container.tagEditor.clearOverride(song)
        toast("App-only changes removed")
    }

    fun songById(id: Long): Song? = library.value.byId[id]
}
