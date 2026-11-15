package com.ultramusicplayer.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultramusicplayer.app.ui.MainViewModel

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val hasPermission by vm.hasPermission.collectAsStateWithLifecycle()
    var denied by rememberSaveable { mutableStateOf(false) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.refreshPermission()
        denied = res.values.any { !it }
        if (!denied) page = 2
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.finishOnboarding() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.addFolder(uri)
            page = 2
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Ultra Music Player", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        when (page) {
            0 -> {
                Text("Your music, offline and beautifully organised.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Feature(Icons.Filled.LibraryMusic, "Every song on your phone", "Browse by songs, albums, artists, genres, playlists and real folders. Instant search.")
                Feature(Icons.Filled.Headphones, "Plays everywhere", "Background playback, lock screen, headset and Bluetooth controls, three home-screen widgets.")
                Feature(Icons.Filled.Equalizer, "Five-band equalizer", "22 presets plus your own custom profile.")
                Feature(Icons.Filled.Palette, "Make it yours", "24 background skins or your own photo, light and dark modes, accent colours.")
                Feature(Icons.Filled.Widgets, "Lyrics, editing & ringtones", "Synced .lrc lyrics, tag editing and a trimmer for ringtones.")
                Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
            }
            1 -> {
                Text("Find your music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Ultra needs permission to read the audio files on this phone. It never uploads anything — " +
                        "the library, search, playlists, lyrics and editing all work offline.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (hasPermission) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Access granted", style = MaterialTheme.typography.bodyLarge)
                    }
                    Button(onClick = { page = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                } else {
                    Button(onClick = { audioLauncher.launch(audioPermissions()) }, modifier = Modifier.fillMaxWidth()) { Text("Allow access to music") }
                    if (denied) {
                        Text(
                            "Without this permission Ultra can still play music from folders you choose.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Choose a folder instead") }
                    TextButton(onClick = { page = 2 }) { Text("Skip for now") }
                }
            }
            else -> {
                Text("Playback controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Allow notifications to control music from the notification shade and lock screen. " +
                        "You can add the Slim Player, Album Player and Artwork Strip widgets from your home screen.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (Build.VERSION.SDK_INT >= 33) {
                    Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow notifications")
                    }
                    TextButton(onClick = { vm.finishOnboarding() }) { Text("Not now") }
                } else {
                    Button(onClick = { vm.finishOnboarding() }, modifier = Modifier.fillMaxWidth()) { Text("Start listening") }
                }
            }
        }
        Text(
            "Step ${page + 1} of 3",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
