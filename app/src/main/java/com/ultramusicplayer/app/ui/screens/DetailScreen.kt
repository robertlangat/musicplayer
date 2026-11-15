@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultramusicplayer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ultramusicplayer.app.data.FolderNode
import com.ultramusicplayer.app.data.PathUtils
import com.ultramusicplayer.app.data.Song
import com.ultramusicplayer.app.ui.DetailType
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.PlaylistRow
import com.ultramusicplayer.app.ui.components.Artwork
import com.ultramusicplayer.app.ui.components.ConfirmDialog
import com.ultramusicplayer.app.ui.components.EmptyState
import com.ultramusicplayer.app.ui.components.ExtraMenuItem
import com.ultramusicplayer.app.ui.components.GroupRow
import com.ultramusicplayer.app.ui.components.LocalSongActions
import com.ultramusicplayer.app.ui.components.SongRow
import com.ultramusicplayer.app.ui.components.TextInputDialog
import com.ultramusicplayer.app.ui.components.UltraTopBar
import com.ultramusicplayer.app.ui.components.formatDuration
import com.ultramusicplayer.app.ui.openDetail
import kotlinx.coroutines.flow.flowOf

@Composable
fun DetailScreen(vm: MainViewModel, nav: NavHostController, type: String, key: String) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val current by vm.currentSong.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val playlistId = if (type == DetailType.PLAYLIST) key.toLongOrNull() else null
    val rowsFlow = remember(playlistId) { playlistId?.let { vm.playlistRows(it) } ?: flowOf(emptyList<PlaylistRow>()) }
    val playlistRows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList<PlaylistRow>())
    val actions = LocalSongActions.current

    var folder: FolderNode? = null
    val title: String
    val songs: List<Song>
    var numbered = false
    when (type) {
        DetailType.ALBUM -> {
            val album = lib.album(key)
            title = album?.title ?: "Album"
            songs = album?.songs.orEmpty()
            numbered = true
        }
        DetailType.ARTIST -> {
            title = key
            songs = lib.artist(key)?.songs.orEmpty().sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.trackNumber }))
        }
        DetailType.ALBUM_ARTIST -> {
            title = key
            songs = lib.albumArtist(key)?.songs.orEmpty().sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.trackNumber }))
        }
        DetailType.GENRE -> {
            title = key
            songs = lib.genre(key)?.songs.orEmpty()
        }
        DetailType.FOLDER -> {
            folder = lib.folder(key)
            title = folder?.name ?: PathUtils.pretty(key).substringAfterLast('/')
            songs = folder?.songs.orEmpty()
        }
        DetailType.PLAYLIST -> {
            title = playlists.firstOrNull { it.id == playlistId }?.name ?: "Playlist"
            songs = playlistRows.map { it.song }
        }
        else -> {
            title = "Recently Played"
            songs = recent
        }
    }
    // Folder actions (play / add) cover the folder and everything below it.
    val allSongs = folder?.allSongs() ?: songs
    val subtitle = when (type) {
        DetailType.FOLDER -> PathUtils.pretty(key)
        else -> "${allSongs.size} songs · ${formatDuration(allSongs.sumOf { it.durationMs })}"
    }

    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        UltraTopBar(title = title, subtitle = subtitle, onBack = { nav.popBackStack() }, actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) },
                        onClick = { menu = false; actions.playNext(allSongs) },
                    )
                    DropdownMenuItem(
                        text = { Text("Add all to queue") },
                        leadingIcon = { Icon(Icons.Filled.QueueMusic, null) },
                        onClick = { menu = false; actions.addToQueue(allSongs) },
                    )
                    DropdownMenuItem(
                        text = { Text("Add all to playlist…") },
                        leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                        onClick = { menu = false; actions.addToPlaylist(allSongs) },
                    )
                    if (type == DetailType.PLAYLIST) {
                        DropdownMenuItem(
                            text = { Text("Rename playlist") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                    if (type == DetailType.RECENT) {
                        DropdownMenuItem(
                            text = { Text("Clear history") },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                            onClick = { menu = false; clearing = true },
                        )
                    }
                }
            }
        })

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (type == DetailType.ALBUM && songs.isNotEmpty()) {
                item {
                    Artwork(
                        songs.first().uri, songs.first().albumId,
                        Modifier.padding(16.dp).size(200.dp),
                        corner = 16.dp,
                        description = "Album artwork",
                    )
                }
            }
            if (allSongs.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { vm.play(allSongs, 0) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Play")
                        }
                        FilledTonalButton(onClick = { vm.shuffle(allSongs) }) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Shuffle")
                        }
                    }
                }
            }
            folder?.let { f ->
                items(f.children, key = { "dir:" + it.path }) { child ->
                    GroupRow(
                        title = child.name,
                        subtitle = "${child.totalSongs} songs",
                        icon = Icons.Filled.Folder,
                        onClick = { nav.openDetail(DetailType.FOLDER, child.path) },
                    )
                }
            }
            if (type == DetailType.PLAYLIST) {
                items(playlistRows, key = { it.rowId }) { row ->
                    val index = playlistRows.indexOf(row)
                    SongRow(
                        song = row.song,
                        onClick = { vm.play(songs, index) },
                        isCurrent = current?.id == row.song.id,
                        extraMenu = listOf(
                            ExtraMenuItem("Remove from playlist", Icons.Filled.RemoveCircleOutline) { vm.removeFromPlaylist(row.rowId) },
                        ),
                        trailing = {
                            IconButton(
                                onClick = { playlistId?.let { vm.movePlaylistItem(it, row.fullIndex, playlistRows[index - 1].fullIndex) } },
                                enabled = index > 0,
                            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${row.song.title} up") }
                            IconButton(
                                onClick = { playlistId?.let { vm.movePlaylistItem(it, row.fullIndex, playlistRows[index + 1].fullIndex) } },
                                enabled = index < playlistRows.size - 1,
                            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${row.song.title} down") }
                        },
                    )
                }
            } else {
                items(songs.size, key = { "song:" + songs[it].id + ":" + it }) { i ->
                    val song = songs[i]
                    SongRow(
                        song = song,
                        onClick = { vm.play(songs, i) },
                        isCurrent = current?.id == song.id,
                        showArt = !numbered,
                        leadingNumber = if (numbered) song.trackNumber.takeIf { it > 0 } ?: (i + 1) else null,
                    )
                }
            }
            if (allSongs.isEmpty() && (folder?.children?.isEmpty() != false)) {
                item {
                    EmptyState(
                        icon = Icons.Filled.MusicOff,
                        title = "Nothing here yet",
                        message = when (type) {
                            DetailType.PLAYLIST -> "Use “Add to playlist” from a song's menu, or “Add all to playlist” on an album, artist, genre or folder."
                            DetailType.RECENT -> "Songs you play will appear here."
                            else -> "These songs may have been moved or deleted."
                        },
                    )
                }
            }
        }
    }

    if (renaming && playlistId != null) {
        TextInputDialog("Rename playlist", title, "Rename", onConfirm = { vm.renamePlaylist(playlistId, it); renaming = false }, onDismiss = { renaming = false })
    }
    if (deleting && playlistId != null) {
        ConfirmDialog(
            "Delete “$title”?", "Your music files are not touched.", "Delete",
            onConfirm = {
                deleting = false
                vm.deletePlaylist(playlistId)
                nav.popBackStack()
            },
            onDismiss = { deleting = false },
        )
    }
    if (clearing) {
        ConfirmDialog(
            "Clear listening history?", "Recently Played will be emptied.", "Clear",
            onConfirm = { vm.clearHistory(); clearing = false },
            onDismiss = { clearing = false },
        )
    }
}
