@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ultramusicplayer.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ultramusicplayer.app.data.PathUtils
import com.ultramusicplayer.app.data.SongGroup
import com.ultramusicplayer.app.ui.DetailType
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.components.EmptyState
import com.ultramusicplayer.app.ui.components.GroupRow
import com.ultramusicplayer.app.ui.components.SectionHeader
import com.ultramusicplayer.app.ui.components.SongRow
import com.ultramusicplayer.app.ui.openDetail

@Composable
fun SearchScreen(vm: MainViewModel, nav: NavHostController) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val current by vm.currentSong.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
            placeholder = { Text("Songs, files, albums, artists, genres, playlists, folders") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        // Search keeps working on the current snapshot while the library refreshes.
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (query.isBlank()) {
            EmptyState(Icons.Filled.Search, "Search your music", "Results appear as you type, grouped by type. Matching ignores upper and lower case.")
            return@Column
        }
        if (results.isEmpty && results.query == query) {
            EmptyState(Icons.Filled.SearchOff, "No matches", "Nothing matches “$query”.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (results.songs.isNotEmpty()) {
                item { SectionHeader("Songs (${results.songs.size})") }
                items(results.songs.size, key = { "s" + results.songs[it].id }) { i ->
                    val song = results.songs[i]
                    SongRow(song, onClick = { vm.play(results.songs, i) }, isCurrent = current?.id == song.id)
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums (${results.albums.size})") }
                items(results.albums, key = { "a" + it.key }) { a ->
                    GroupRow(a.title, "${a.artist} · ${a.songs.size} songs", { nav.openDetail(DetailType.ALBUM, a.key) }, a.cover.uri, a.cover.albumId)
                }
            }
            groupSection(this, "Artists", results.artists, Icons.Filled.Person) { nav.openDetail(DetailType.ARTIST, it) }
            groupSection(this, "Album artists", results.albumArtists, Icons.Filled.People) { nav.openDetail(DetailType.ALBUM_ARTIST, it) }
            groupSection(this, "Genres", results.genres, Icons.Filled.Category) { nav.openDetail(DetailType.GENRE, it) }
            if (results.playlists.isNotEmpty()) {
                item { SectionHeader("Playlists (${results.playlists.size})") }
                items(results.playlists, key = { "p" + it.id }) { p ->
                    GroupRow(p.name, "${p.songCount} songs", { nav.openDetail(DetailType.PLAYLIST, p.id.toString()) }, icon = Icons.Filled.QueueMusic)
                }
            }
            if (results.folders.isNotEmpty()) {
                item { SectionHeader("Folders (${results.folders.size})") }
                items(results.folders, key = { "f" + it.path }) { f ->
                    GroupRow(f.name, PathUtils.pretty(f.path), { nav.openDetail(DetailType.FOLDER, f.path) }, icon = Icons.Filled.Folder)
                }
            }
        }
    }
}

private fun groupSection(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    label: String,
    groups: List<SongGroup>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: (String) -> Unit,
) {
    if (groups.isEmpty()) return
    scope.item { SectionHeader("$label (${groups.size})") }
    scope.items(groups, key = { label + it.name }) { g ->
        GroupRow(g.name, "${g.songs.size} songs", { onOpen(g.name) }, icon = icon)
    }
}
