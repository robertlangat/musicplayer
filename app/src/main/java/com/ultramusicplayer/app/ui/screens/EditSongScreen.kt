package com.ultramusicplayer.app.ui.screens

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ultramusicplayer.app.data.AudioFormats
import com.ultramusicplayer.app.data.Song
import com.ultramusicplayer.app.data.TagEdit
import com.ultramusicplayer.app.data.TagSaveResult
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.components.EmptyState
import com.ultramusicplayer.app.ui.components.UltraTopBar

@Composable
fun EditSongScreen(vm: MainViewModel, nav: NavHostController, songId: Long) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val song = lib.byId[songId]
    if (song == null) {
        Column(Modifier.fillMaxSize()) {
            UltraTopBar("Edit song details", onBack = { nav.popBackStack() })
            EmptyState(Icons.Filled.ErrorOutline, "Song not found", "It may have been moved or deleted.")
        }
        return
    }
    EditSongForm(vm, nav, song)
}

@Composable
private fun EditSongForm(vm: MainViewModel, nav: NavHostController, song: Song) {
    var title by rememberSaveable { mutableStateOf(song.title) }
    var artist by rememberSaveable { mutableStateOf(song.artist) }
    var album by rememberSaveable { mutableStateOf(song.album) }
    var albumArtist by rememberSaveable { mutableStateOf(song.albumArtist) }
    var genre by rememberSaveable { mutableStateOf(song.genre) }
    var track by rememberSaveable { mutableStateOf(song.trackNumber.takeIf { it > 0 }?.toString().orEmpty()) }
    var year by rememberSaveable { mutableStateOf(song.year.takeIf { it > 0 }?.toString().orEmpty()) }
    val result by vm.tagResult.collectAsStateWithLifecycle()

    fun edit() = TagEdit(title, artist, album, albumArtist, genre, track.toIntOrNull(), year.toIntOrNull())

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.clearTagResult()
        vm.saveTags(song, edit(), consentDenied = r.resultCode != Activity.RESULT_OK)
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.clearTagResult()
        vm.saveTags(song, edit(), consentDenied = !granted)
    }

    // Drop any result left over from a previous edit screen.
    LaunchedEffect(song.id) { vm.clearTagResult() }
    LaunchedEffect(result) {
        when (val r = result) {
            is TagSaveResult.NeedsUserConsent -> consentLauncher.launch(IntentSenderRequest.Builder(r.intentSender).build())
            is TagSaveResult.NeedsLegacyPermission -> legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            is TagSaveResult.WrittenToFile -> {
                vm.clearTagResult()
                vm.toast("Saved to the file")
                nav.popBackStack()
            }
            else -> Unit
        }
    }

    val writable = song.extension in AudioFormats.taggable
    Column(Modifier.fillMaxSize()) {
        UltraTopBar("Edit song details", subtitle = song.fileName, onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))) {
                Text(
                    if (writable) {
                        "Changes are written into the file's tags. Android will ask you to allow the edit."
                    } else {
                        "“.${song.extension}” files can't store these tags, so changes are saved inside Ultra only (marked “edited in app”)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (song.hasOverride) {
                Text(
                    "This song currently shows app-only edits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Field("Title", title) { title = it }
            Field("Artist", artist) { artist = it }
            Field("Album", album) { album = it }
            Field("Album artist", albumArtist) { albumArtist = it }
            Field("Genre", genre) { genre = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Track #", track, Modifier.weight(1f), numeric = true) { track = it.filter(Char::isDigit).take(3) }
                Field("Year", year, Modifier.weight(1f), numeric = true) { year = it.filter(Char::isDigit).take(4) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                Button(
                    onClick = {
                        vm.saveTags(song, edit())
                    },
                    enabled = title.isNotBlank(),
                ) { Text("Save") }
                if (song.hasOverride) {
                    OutlinedButton(onClick = {
                        vm.revertOverride(song)
                        nav.popBackStack()
                    }) { Text("Remove app-only edits") }
                }
            }
        }
    }

    when (val r = result) {
        is TagSaveResult.SavedAsOverride -> AlertDialog(
            onDismissRequest = { vm.clearTagResult() },
            title = { Text("Saved in Ultra only") },
            text = { Text("${r.reason}. Your changes are shown throughout Ultra but the file itself was not modified.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearTagResult()
                    nav.popBackStack()
                }) { Text("OK") }
            },
        )
        is TagSaveResult.Failed -> AlertDialog(
            onDismissRequest = { vm.clearTagResult() },
            title = { Text("Couldn't save") },
            text = { Text(r.reason) },
            confirmButton = { TextButton(onClick = { vm.clearTagResult() }) { Text("OK") } },
        )
        else -> Unit
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}
