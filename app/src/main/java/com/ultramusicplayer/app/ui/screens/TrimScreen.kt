@file:OptIn(ExperimentalMaterial3Api::class)

package com.ultramusicplayer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import com.ultramusicplayer.app.data.Song
import com.ultramusicplayer.app.trim.AudioTrimmer
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.components.EmptyState
import com.ultramusicplayer.app.ui.components.UltraTopBar
import com.ultramusicplayer.app.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TrimScreen(vm: MainViewModel, nav: NavHostController, songId: Long) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val song = lib.byId[songId]
    Column(Modifier.fillMaxSize()) {
        UltraTopBar("Trim & ringtone", subtitle = song?.title, onBack = { nav.popBackStack() })
        if (song == null) {
            EmptyState(Icons.Filled.ErrorOutline, "Song not found", "It may have been moved or deleted.")
        } else {
            Trimmer(vm, song)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Trimmer(vm: MainViewModel, song: Song) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var waveform by remember { mutableStateOf<FloatArray?>(null) }
    var waveLoading by remember { mutableStateOf(true) }
    val preview = remember { ExoPlayer.Builder(context).build() }
    var duration by remember { mutableLongStateOf(song.durationMs) }
    var range by remember { mutableStateOf(0f..1f) }
    var playing by remember { mutableStateOf(false) }
    var playhead by remember { mutableLongStateOf(-1L) }
    var busy by remember { mutableStateOf<String?>(null) }
    var askRingtonePermission by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && duration <= 0) duration = preview.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) playhead = -1
            }
        }
        preview.addListener(listener)
        onDispose {
            preview.removeListener(listener)
            preview.release()
        }
    }

    LaunchedEffect(song.id) {
        waveLoading = true
        waveform = AudioTrimmer.waveform(context, song.uri)
        waveLoading = false
        if (duration <= 0) {
            preview.setMediaItem(MediaItem.fromUri(song.uri))
            preview.prepare()
        }
    }
    LaunchedEffect(duration) {
        if (duration > 0) {
            // Default selection: the first 30 seconds (a typical ringtone length).
            range = 0f..(30_000f / duration).coerceAtMost(1f)
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            playhead = (range.start * duration).toLong() + preview.currentPosition
            delay(100)
        }
    }

    val startMs = (range.start * duration).toLong()
    val endMs = (range.endInclusive * duration).toLong()
    val lengthMs = endMs - startMs

    fun playSelection() {
        vm.player.pause()
        preview.setMediaItem(
            MediaItem.Builder()
                .setUri(song.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build(),
                )
                .build(),
        )
        preview.prepare()
        preview.play()
    }

    fun export(asRingtone: Boolean) {
        busy = if (asRingtone) "Creating ringtone…" else "Saving clip…"
        preview.pause()
        scope.launch {
            val out = File(context.cacheDir, "clip_${System.currentTimeMillis()}.m4a")
            val error = AudioTrimmer.exportClip(context, song.uri, startMs, endMs, out)
            if (error != null || !out.exists()) {
                busy = null
                resultMessage = "Export failed: this file's format couldn't be converted (${error?.message ?: "unknown error"}). The original is untouched."
                return@launch
            }
            val title = "${song.title} (clip)"
            val uri = AudioTrimmer.saveToMediaStore(context, out, title, asRingtone)
            out.delete()
            busy = null
            resultMessage = when {
                uri == null -> "Couldn't save the clip to shared storage."
                !asRingtone -> "Clip saved to Music/Ultra Clips. The original file is unchanged."
                AudioTrimmer.setAsRingtone(context, uri) -> "Ringtone set! The clip is saved in Ringtones."
                else -> "The clip was saved in Ringtones, but Android didn't let Ultra change the ringtone. Pick it in Settings → Sound."
            }
        }
    }

    val legacyWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) resultMessage = "Storage permission is needed to save clips on this Android version."
    }
    fun needsLegacyPermission(): Boolean =
        Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        val playheadColor = MaterialTheme.colorScheme.onSurface
        if (waveLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = "Waveform timeline. Selection ${formatDuration(startMs)} to ${formatDuration(endMs)}" },
        ) {
            val bars = waveform ?: FloatArray(80) { 0.35f }
            val barW = size.width / bars.size
            bars.forEachIndexed { i, v ->
                val x = i * barW
                val frac = (i + 0.5f) / bars.size
                val selected = frac >= range.start && frac <= range.endInclusive
                val h = v * size.height * 0.9f
                drawRoundRect(
                    color = if (selected) primary else dim,
                    topLeft = Offset(x + barW * 0.15f, (size.height - h) / 2),
                    size = Size(barW * 0.7f, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(barW * 0.3f),
                )
            }
            listOf(range.start, range.endInclusive).forEach { f ->
                drawLine(primary, Offset(f * size.width, 0f), Offset(f * size.width, size.height), strokeWidth = 4f)
            }
            if (playhead >= 0 && duration > 0) {
                val x = playhead.toFloat() / duration * size.width
                drawLine(playheadColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            }
        }
        RangeSlider(
            value = range,
            onValueChange = { r ->
                val minGap = if (duration > 0) 1000f / duration else 0.01f
                range = if (r.endInclusive - r.start < minGap) range else r
            },
            enabled = duration > 0 && busy == null,
            modifier = Modifier.semantics { contentDescription = "Clip start and end handles" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start ${formatDuration(startMs)}", style = MaterialTheme.typography.bodyMedium)
            Text("Length ${formatDuration(lengthMs)}", style = MaterialTheme.typography.bodyMedium, color = primary)
            Text("End ${formatDuration(endMs)}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fun nudge(startDelta: Float, endDelta: Float) {
                if (duration <= 0) return
                val step = 100f / duration // 0.1 s
                val s = (range.start + startDelta * step).coerceIn(0f, range.endInclusive - step)
                val e = (range.endInclusive + endDelta * step).coerceIn(s + step, 1f)
                range = s..e
            }
            OutlinedButton(onClick = { nudge(-1f, 0f) }) { Text("Start −0.1s") }
            OutlinedButton(onClick = { nudge(1f, 0f) }) { Text("+0.1s") }
            OutlinedButton(onClick = { nudge(0f, -1f) }) { Text("End −0.1s") }
            OutlinedButton(onClick = { nudge(0f, 1f) }) { Text("+0.1s") }
        }
        FilledTonalButton(
            onClick = { if (playing) preview.pause() else playSelection() },
            enabled = duration > 0 && busy == null,
        ) {
            Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Text(if (playing) "  Stop preview" else "  Preview selection")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (needsLegacyPermission()) legacyWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else export(false) },
                enabled = duration > 0 && busy == null,
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Text("  Save clip")
            }
            Button(
                onClick = {
                    when {
                        needsLegacyPermission() -> legacyWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        !AudioTrimmer.canSetRingtone(context) -> askRingtonePermission = true
                        else -> export(true)
                    }
                },
                enabled = duration > 0 && busy == null,
            ) {
                Icon(Icons.Filled.RingVolume, contentDescription = null)
                Text("  Set as ringtone")
            }
        }
        busy?.let { label ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.height(24.dp))
                Text(label)
            }
        }
        Text(
            "The clip is exported as a new AAC (.m4a) file; the original song is never modified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (askRingtonePermission) {
        AlertDialog(
            onDismissRequest = { askRingtonePermission = false },
            title = { Text("Allow changing the ringtone") },
            text = {
                Text(
                    "Android only lets apps change your ringtone after you turn on “Modify system settings” for Ultra. " +
                        "Turn it on, come back and tap “Set as ringtone” again — or just save the clip.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askRingtonePermission = false
                    context.startActivity(AudioTrimmer.writeSettingsIntent(context))
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askRingtonePermission = false
                    export(false)
                }) { Text("Save clip only") }
            },
        )
    }
    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { resultMessage = null }) { Text("OK") } },
        )
    }
}
