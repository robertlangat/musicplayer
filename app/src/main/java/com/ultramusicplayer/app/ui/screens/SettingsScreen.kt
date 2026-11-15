@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ultramusicplayer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.ultramusicplayer.app.data.AppSettings
import com.ultramusicplayer.app.data.PathUtils
import com.ultramusicplayer.app.data.ShakeAction
import com.ultramusicplayer.app.data.ThemeMode
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.Routes
import com.ultramusicplayer.app.ui.components.UltraTopBar
import com.ultramusicplayer.app.ui.theme.AccentChoices
import com.ultramusicplayer.app.ui.theme.SkinSwatch
import com.ultramusicplayer.app.ui.theme.Skins
import com.ultramusicplayer.app.video.VideoSearch

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavHostController, settings: AppSettings) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    Column(Modifier.fillMaxSize()) {
        UltraTopBar(title = "Settings")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsCard("Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        FilterChip(
                            selected = settings.themeMode == m,
                            onClick = { vm.setThemeMode(m) },
                            label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentChoices.forEachIndexed { i, argb ->
                        val selected = settings.accent == argb
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { vm.setAccent(argb) }
                                .semantics {
                                    contentDescription = "Accent colour ${i + 1}"
                                    this.selected = selected
                                },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate(Routes.SKINS) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkinSwatch(Skins.byId(settings.skinId), Modifier.size(48.dp).clip(CircleShape))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Background", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.skinId == Skins.PHOTO_ID) "Your photo" else Skins.byId(settings.skinId).name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Choose background")
                }
            }

            SettingsCard("Music folders") {
                Text(
                    "Ultra finds music through Android's media library automatically. Add folders here to include ones it skips, " +
                        "folders on an SD card, or to let Ultra find .lrc / .txt lyric files on Android 11 and newer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                settings.folders.forEach { uri ->
                    val accessible = vm.isFolderAccessible(uri)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (accessible) Icons.Filled.Folder else Icons.Filled.Warning, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            val docId = android.net.Uri.parse(uri).lastPathSegment.orEmpty()
                            Text(PathUtils.pretty(PathUtils.docIdToPath(docId)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!accessible) Text("Access lost — remove and add again", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { vm.removeFolder(uri) }) { Icon(Icons.Filled.Close, contentDescription = "Remove folder") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Text("  Add folder")
                    }
                    TextButton(onClick = { vm.rescan() }) { Text("Rescan library") }
                }
                SwitchRow(
                    "Include recordings & voice notes",
                    "Also list audio Android doesn't mark as music",
                    settings.includeAllAudio,
                ) { vm.setIncludeAllAudio(it) }
            }

            ShakeCard(vm, settings)

            SettingsCard("Notifications & lock screen") {
                if (notifGranted) {
                    Text("Playback controls appear in the notification shade and on the lock screen.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "Allow notifications so playback controls show in the notification shade. Music still plays in the background without it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                }
            }

            SettingsCard("Music videos") {
                Text(
                    if (VideoSearch.hasApiKey) {
                        "Video results load in the app using the YouTube Data API key this build was made with."
                    } else {
                        "Video searches open in the YouTube app or your browser. Nothing is downloaded."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { nav.navigate(Routes.videos("")) }) { Text("Search music videos") }
            }

            SettingsCard("Formats & lyrics") {
                Text(
                    "Plays MP3, AAC/M4A, WAV, FLAC, Ogg Vorbis, Opus and AMR using your phone's decoders. Files that are damaged " +
                        "or use a codec the phone can't decode are skipped with a message.\n\n" +
                        "Lyrics: Ultra looks for a .lrc or .txt file with the same name next to the song, then lyrics embedded in the " +
                        "file, then files whose name matches the title and artist. Timestamped .lrc files scroll in sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (com.ultramusicplayer.app.ads.Ads.privacyOptionsRequired) {
                SettingsCard("Ads & privacy") {
                    Text("Review or change your ad personalisation choices.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        (context as? android.app.Activity)?.let { com.ultramusicplayer.app.ads.Ads.showPrivacyOptions(it) }
                    }) { Text("Privacy options") }
                }
            }

            SettingsCard("About") {
                Text("Ultra Music Player 1.0 — offline music player", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.restartOnboarding() }) { Text("Show welcome screens again") }
            }
            Box(Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun ShakeCard(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val hasSensor = remember {
        context.getSystemService(android.hardware.SensorManager::class.java)
            ?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) != null
    }
    var sensitivity by remember(settings.shakeSensitivity) { mutableFloatStateOf(settings.shakeSensitivity) }
    SettingsCard("Shake to skip") {
        if (!hasSensor) {
            Text("This device has no accelerometer, so shake gestures aren't available.", style = MaterialTheme.typography.bodySmall)
            return@SettingsCard
        }
        SwitchRow("Shake to skip", "Off by default", settings.shakeEnabled) {
            vm.setShake(it, settings.shakeAction, settings.shakeSensitivity)
        }
        if (settings.shakeEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShakeAction.entries.forEach { a ->
                    FilterChip(
                        selected = settings.shakeAction == a,
                        onClick = { vm.setShake(true, a, settings.shakeSensitivity) },
                        label = { Text(a.label) },
                    )
                }
            }
            Text("Sensitivity", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Firm", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    onValueChangeFinished = { vm.setShake(true, settings.shakeAction, sensitivity) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Shake sensitivity" },
                )
                Text("Light", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            "Works only while music is playing. One shake skips one track, then waits 1.5 seconds. With the screen off, " +
                "some phones pause or slow motion sensors to save battery, so shakes may not register until the screen is on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
