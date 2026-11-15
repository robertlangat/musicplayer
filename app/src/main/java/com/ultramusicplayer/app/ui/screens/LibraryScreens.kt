@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultramusicplayer.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ultramusicplayer.app.data.AppSettings
import com.ultramusicplayer.app.data.FolderNode
import com.ultramusicplayer.app.data.Library
import com.ultramusicplayer.app.data.Song
import com.ultramusicplayer.app.data.SongGroup
import com.ultramusicplayer.app.data.SongSort
import com.ultramusicplayer.app.ui.DetailType
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.components.Artwork
import com.ultramusicplayer.app.ui.components.ConfirmDialog
import com.ultramusicplayer.app.ui.components.EmptyState
import com.ultramusicplayer.app.ui.components.GroupRow
import com.ultramusicplayer.app.ui.components.SongRow
import com.ultramusicplayer.app.ui.components.TextInputDialog
import com.ultramusicplayer.app.ui.components.UltraTopBar
import com.ultramusicplayer.app.ui.openDetail

private val libraryTabs = listOf("Songs", "Albums", "Artists", "Album Artists", "Genres", "Folders")

fun audioPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

@Composable
fun LibraryScreen(vm: MainViewModel, nav: NavHostController, settings: AppSettings) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasScanned by vm.hasScanned.collectAsStateWithLifecycle()
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        UltraTopBar(
            title = "Library",
            subtitle = "${lib.songs.size} songs · ${lib.albums.size} albums" + if (scanning) " · scanning…" else "",
            actions = {
                if (tab == 0) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, contentDescription = "Sort songs") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SongSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    trailingIcon = { if (settings.songSort == s) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                                    onClick = {
                                        vm.setSort(s, settings.songSortDescending)
                                        sortMenu = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (settings.songSortDescending) "Descending" else "Ascending") },
                                onClick = {
                                    vm.setSort(settings.songSort, !settings.songSortDescending)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { vm.rescan() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh library") }
            },
        )
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Color.Transparent) {
            libraryTabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
            }
        }
        when {
            !hasPermission && lib.isEmpty -> PermissionNeeded(vm)
            lib.isEmpty && (!hasScanned || scanning) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            lib.isEmpty -> EmptyLibrary(vm)
            else -> when (tab) {
                0 -> SongsTab(vm, vm.sortSongs(lib.songs, settings.songSort, settings.songSortDescending))
                1 -> AlbumsGrid(lib) { nav.openDetail(DetailType.ALBUM, it) }
                2 -> GroupList(lib.artists, Icons.Filled.Person) { nav.openDetail(DetailType.ARTIST, it) }
                3 -> GroupList(lib.albumArtists, Icons.Filled.People) { nav.openDetail(DetailType.ALBUM_ARTIST, it) }
                4 -> GroupList(lib.genres, Icons.Filled.Category) { nav.openDetail(DetailType.GENRE, it) }
                else -> FolderList(vm, lib.folderRoots) { nav.openDetail(DetailType.FOLDER, it) }
            }
        }
    }
}

@Composable
fun PermissionNeeded(vm: MainViewModel) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        vm.refreshPermission()
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    EmptyState(
        icon = Icons.Filled.Lock,
        title = "Allow access to your music",
        message = if (asked) {
            "Access was denied. Ultra Music Player only reads audio files to build your library. " +
                "You can allow it in system settings, or pick a single folder instead."
        } else {
            "Ultra Music Player needs permission to find the songs on this phone. Nothing leaves your device."
        },
    ) {
        Button(onClick = { launcher.launch(audioPermissions()) }) { Text("Allow access") }
        if (asked) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Open app settings") }
        }
        OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Choose a music folder") }
    }
}

@Composable
private fun EmptyLibrary(vm: MainViewModel) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    EmptyState(
        icon = Icons.Filled.MusicOff,
        title = "No music found",
        message = "Copy audio files (MP3, M4A, FLAC, OGG, Opus, WAV, AMR…) to your phone, " +
            "or add a folder such as one on your SD card.",
    ) {
        Button(onClick = { folderPicker.launch(null) }) { Text("Add a folder") }
        OutlinedButton(onClick = { vm.rescan() }) { Text("Scan again") }
    }
}

@Composable
private fun SongsTab(vm: MainViewModel, songs: List<Song>) {
    val current by vm.currentSong.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { vm.shuffle(songs) }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Shuffle all")
                }
                OutlinedButton(onClick = { vm.play(songs, 0) }) { Text("Play all") }
            }
        }
        items(songs.size, key = { songs[it].id }) { i ->
            val song = songs[i]
            SongRow(song, onClick = { vm.play(songs, i) }, isCurrent = current?.id == song.id)
        }
    }
}

@Composable
private fun AlbumsGrid(lib: Library, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(lib.albums, key = { it.key }) { album ->
            Column(Modifier.clickable { onOpen(album.key) }) {
                Artwork(
                    album.cover.uri, album.cover.albumId,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    corner = 12.dp,
                    description = "Album ${album.title}",
                )
                Text(
                    album.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "${album.artist} · ${album.songs.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GroupList(groups: List<SongGroup>, icon: androidx.compose.ui.graphics.vector.ImageVector, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.name }) { g ->
            val cover = g.songs.first()
            GroupRow(
                title = g.name,
                subtitle = "${g.albumCount} albums · ${g.songs.size} songs",
                artUri = cover.uri,
                albumId = cover.albumId,
                icon = if (g.songs.size > 0) null else icon,
                onClick = { onOpen(g.name) },
            )
        }
    }
}

@Composable
private fun FolderList(vm: MainViewModel, roots: List<FolderNode>, onOpen: (String) -> Unit) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            GroupRow(
                title = "Add a folder…",
                subtitle = "Include a folder or SD card the library is missing",
                icon = Icons.Filled.CreateNewFolder,
                onClick = { folderPicker.launch(null) },
            )
        }
        items(roots, key = { it.path }) { node ->
            GroupRow(
                title = node.name,
                subtitle = "${node.children.size} folders · ${node.totalSongs} songs",
                icon = Icons.Filled.Folder,
                onClick = { onOpen(node.path) },
            )
        }
    }
}

@Composable
fun PlaylistsScreen(vm: MainViewModel, nav: NavHostController) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        UltraTopBar(
            title = "Playlists",
            actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "New playlist") } },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                GroupRow(
                    title = "Recently Played",
                    subtitle = "${recent.size} songs",
                    icon = Icons.Filled.History,
                    onClick = { nav.openDetail(DetailType.RECENT, "recent") },
                )
            }
            if (playlists.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.QueueMusic,
                        title = "No playlists yet",
                        message = "Create one here, or use “Add to playlist” from any song, album, artist, genre or folder.",
                    ) { Button(onClick = { creating = true }) { Text("New playlist") } }
                }
            }
            items(playlists, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                GroupRow(
                    title = p.name,
                    subtitle = "${p.songCount} songs",
                    icon = Icons.Filled.QueueMusic,
                    onClick = { nav.openDetail(DetailType.PLAYLIST, p.id.toString()) },
                    trailing = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${p.name}") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = { menu = false; renaming = p.id to p.name },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = { menu = false; deleting = p.id to p.name },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    if (creating) {
        TextInputDialog("New playlist", "", "Create", onConfirm = { vm.createPlaylist(it); creating = false }, onDismiss = { creating = false })
    }
    renaming?.let { (id, name) ->
        TextInputDialog("Rename playlist", name, "Rename", onConfirm = { vm.renamePlaylist(id, it); renaming = null }, onDismiss = { renaming = null })
    }
    deleting?.let { (id, name) ->
        ConfirmDialog(
            "Delete “$name”?",
            "The playlist is removed. Your music files are not touched.",
            "Delete",
            onConfirm = { vm.deletePlaylist(id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}
