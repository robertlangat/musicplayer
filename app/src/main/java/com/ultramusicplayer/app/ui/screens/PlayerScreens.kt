@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultramusicplayer.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import com.ultramusicplayer.app.data.Song
import com.ultramusicplayer.app.lyrics.LrcLine
import com.ultramusicplayer.app.lyrics.LrcParser
import com.ultramusicplayer.app.lyrics.LyricsResult
import com.ultramusicplayer.app.playback.PlayerUiState
import com.ultramusicplayer.app.ui.DetailType
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.Routes
import com.ultramusicplayer.app.ui.components.Artwork
import com.ultramusicplayer.app.ui.components.EmptyState
import com.ultramusicplayer.app.ui.components.LocalSongActions
import com.ultramusicplayer.app.ui.components.TextInputDialog
import com.ultramusicplayer.app.ui.components.UltraTopBar
import com.ultramusicplayer.app.ui.components.formatDuration
import com.ultramusicplayer.app.ui.openDetail

@Composable
fun MiniPlayer(state: PlayerUiState, vm: MainViewModel, onOpen: () -> Unit) {
    val entry = state.current ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f), tonalElevation = 3.dp) {
        Column {
            val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open Now Playing", onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(entry.uri, entry.albumId, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        entry.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { vm.player.togglePlay() }) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = { vm.player.next() }) { Icon(Icons.Filled.SkipNext, contentDescription = "Next track") }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val song by vm.currentSong.collectAsStateWithLifecycle()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val actions = LocalSongActions.current
    val entry = state.current

    Column(Modifier.fillMaxSize()) {
        UltraTopBar(
            title = "Now Playing",
            subtitle = entry?.album,
            onBack = { nav.popBackStack() },
            actions = {
                IconButton(onClick = { nav.navigate(Routes.QUEUE) }) { Icon(Icons.Filled.QueueMusic, contentDescription = "Queue") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val s = song
                        if (s != null) {
                            DropdownMenuItem(text = { Text("Add to playlist…") }, leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) }, onClick = { menu = false; actions.addToPlaylist(listOf(s)) })
                            DropdownMenuItem(text = { Text("Edit song details") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; actions.edit(s) })
                            DropdownMenuItem(text = { Text("Trim / set as ringtone") }, leadingIcon = { Icon(Icons.Filled.ContentCut, null) }, onClick = { menu = false; actions.trim(s) })
                            DropdownMenuItem(text = { Text("Search music videos") }, leadingIcon = { Icon(Icons.Filled.OndemandVideo, null) }, onClick = { menu = false; actions.videos(s) })
                        }
                        DropdownMenuItem(text = { Text("Stop playback") }, leadingIcon = { Icon(Icons.Filled.Stop, null) }, onClick = { menu = false; vm.player.stop() })
                    }
                }
            },
        )
        if (entry == null) {
            EmptyState(Icons.Filled.MusicNote, "Nothing playing", "Pick a song from your library to start listening.")
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            val s = song
            if (showLyrics && s != null) {
                LyricsPanel(vm, s, state.positionMs)
            } else {
                Artwork(
                    entry.uri, entry.albumId,
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                    corner = 20.dp,
                    description = "Artwork for ${entry.title}",
                )
            }
        }

        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(entry.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                entry.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClickLabel = "Open artist") { nav.openDetail(DetailType.ARTIST, entry.artist) },
            )
            SeekBar(state) { vm.player.seekTo(it) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.player.toggleShuffle() }, modifier = Modifier.semantics { stateDescription = if (state.shuffle) "On" else "Off" }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = { vm.player.togglePlay() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                ) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next track", modifier = Modifier.size(36.dp))
                }
                val repeatLabel = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                }
                IconButton(onClick = { vm.player.cycleRepeat() }, modifier = Modifier.semantics { contentDescription = repeatLabel }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = repeatLabel,
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (showLyrics) "  Artwork" else "  Lyrics")
                }
                TextButton(onClick = { nav.navigate(Routes.QUEUE) }) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Up next")
                }
                song?.let { s ->
                    TextButton(onClick = { actions.videos(s) }) {
                        Icon(Icons.Filled.OndemandVideo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Videos")
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekBar(state: PlayerUiState, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = if (dragging) dragValue else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownPosition = (fraction * duration).toLong()
    Slider(
        value = fraction,
        onValueChange = {
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            onSeek((dragValue * duration).toLong())
            dragging = false
        },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Seek bar, ${formatDuration(shownPosition)} of ${formatDuration(state.durationMs)}"
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(shownPosition), style = MaterialTheme.typography.bodySmall)
        Text("-" + formatDuration((state.durationMs - shownPosition).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LyricsPanel(vm: MainViewModel, song: Song, positionMs: Long) {
    LaunchedEffect(song.id) { vm.loadLyrics(song) }
    val ui by vm.lyrics.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickLyricUri(song, uri)
    }
    val pickFile = { picker.launch(arrayOf("text/*", "application/octet-stream")) }
    val result = ui.result
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.songId != song.id || ui.loading || result == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                result is LyricsResult.Synced -> SyncedLyrics(result.lines, positionMs) { vm.player.seekTo(it) }
                result is LyricsResult.Plain -> Text(
                    result.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                )
                result is LyricsResult.Choose -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text("Several lyric files match this song. Pick one:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                    result.candidates.forEach { file ->
                        OutlinedButton(onClick = { vm.chooseLyricFile(song, file) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${file.name}\n${file.folderPath}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(onClick = pickFile) { Text("Choose another file…") }
                }
                else -> EmptyState(
                    icon = Icons.Filled.Description,
                    title = "No lyrics found",
                    message = "Place “${song.baseName}.lrc” (or .txt) next to the song, or add your music folder under " +
                        "Settings → Music folders so lyric files can be found automatically.",
                    modifier = Modifier.align(Alignment.Center),
                ) { Button(onClick = pickFile) { Text("Choose lyric file") } }
            }
        }
        val source = when (result) {
            is LyricsResult.Synced -> result.source
            is LyricsResult.Plain -> result.source
            else -> null
        }
        if (source != null && ui.songId == song.id) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lyrics: $source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = pickFile) { Icon(Icons.Filled.FolderOpen, contentDescription = "Choose a different lyric file") }
                if (source == "Chosen file") {
                    IconButton(onClick = { vm.resetLyricChoice(song) }) { Icon(Icons.Filled.Close, contentDescription = "Forget chosen lyric file") }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LrcLine>, positionMs: Long, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val current = LrcParser.indexAt(lines, positionMs)
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 24.dp)) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            Text(
                line.text.ifBlank { "♪" },
                style = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Jump to this line") { onSeek(line.timeMs) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
fun QueueScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (state.currentIndex > 0) listState.scrollToItem(state.currentIndex)
    }
    Column(Modifier.fillMaxSize()) {
        UltraTopBar(
            title = "Queue",
            subtitle = "${state.queue.size} songs" + if (state.shuffle) " · shuffled" else "",
            onBack = { nav.popBackStack() },
            actions = {
                IconButton(onClick = { saving = true }, enabled = state.queue.isNotEmpty()) {
                    Icon(Icons.Filled.Save, contentDescription = "Save queue as playlist")
                }
            },
        )
        if (state.queue.isEmpty()) {
            EmptyState(Icons.Filled.QueueMusic, "The queue is empty", "Use “Play next” or “Add to queue” from any song.")
            return@Column
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.queue, key = { i, e -> "$i:${e.mediaId}" }) { i, e ->
                val isCurrent = i == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(onClickLabel = "Play") { vm.player.skipTo(i) }
                        .heightIn(min = 60.dp)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(e.uri, e.albumId, Modifier.size(44.dp), corner = 6.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            e.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (isCurrent) "Now playing · ${e.artist}" else e.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.player.moveInQueue(i, i - 1) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${e.title} up")
                    }
                    IconButton(onClick = { vm.player.moveInQueue(i, i + 1) }, enabled = i < state.queue.size - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${e.title} down")
                    }
                    IconButton(onClick = { vm.player.removeFromQueue(i) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${e.title} from queue")
                    }
                }
            }
        }
    }
    if (saving) {
        TextInputDialog("Save queue as playlist", "Queue", "Save", onConfirm = { vm.saveQueueAsPlaylist(it); saving = false }, onDismiss = { saving = false })
    }
}

@Suppress("unused")
private val lyricShape = RoundedCornerShape(12.dp)
