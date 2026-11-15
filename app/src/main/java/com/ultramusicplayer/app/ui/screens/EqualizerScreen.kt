@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ultramusicplayer.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ultramusicplayer.app.data.AppSettings
import com.ultramusicplayer.app.playback.EqPresets
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.components.UltraTopBar
import kotlin.math.roundToInt

private const val MANUAL = "Manual"

@Composable
fun EqualizerScreen(vm: MainViewModel, settings: AppSettings) {
    // Local copy so sliders feel instant; persisted when a drag ends.
    val gains = remember { mutableStateListOf<Float>().apply { addAll(settings.eqGains) } }
    LaunchedEffect(settings.eqGains) {
        gains.clear()
        gains.addAll(settings.eqGains)
    }
    val enabled = settings.eqEnabled
    val presetNames = EqPresets.presets.keys.toList() + EqPresets.CUSTOM

    fun applyPreset(name: String) {
        val values = if (name == EqPresets.CUSTOM) settings.eqCustom else EqPresets.presets.getValue(name)
        gains.clear()
        gains.addAll(values)
        vm.commitEq(true, name, values)
    }

    Column(Modifier.fillMaxSize()) {
        UltraTopBar(title = "Equalizer", subtitle = "5-band · ${settings.eqPreset}")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (enabled) "On — applied to everything Ultra plays" else "Off — audio plays unmodified",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { vm.commitEq(it, settings.eqPreset, gains.toList()) },
                        modifier = Modifier.semantics { contentDescription = "Equalizer on or off" },
                    )
                }
            }

            Text("Presets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetNames.forEach { name ->
                    FilterChip(
                        selected = settings.eqPreset == name,
                        onClick = { applyPreset(name) },
                        label = { Text(name) },
                    )
                }
            }

            Text("Bands", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            EqPresets.BAND_LABELS.forEachIndexed { i, label ->
                val value = gains.getOrElse(i) { 0f }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = value,
                        onValueChange = { v ->
                            val snapped = (v * 2).roundToInt() / 2f
                            if (i < gains.size) gains[i] = snapped
                            vm.previewEq(true, gains.toList())
                        },
                        onValueChangeFinished = { vm.commitEq(true, MANUAL, gains.toList()) },
                        valueRange = EqPresets.MIN_DB..EqPresets.MAX_DB,
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = "$label gain"
                            stateDescription = "%+.1f decibels".format(value)
                        },
                    )
                    Text("%+.1f dB".format(value), modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { applyPreset("Flat") }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Text("  Reset")
                }
                OutlinedButton(onClick = { vm.saveEqCustom(gains.toList()) }) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("  Save as Custom")
                }
            }

            Text(
                "Ultra processes audio with its own five-band filter inside the player (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz), " +
                    "so the layout is identical on every phone and does not depend on the manufacturer's equalizer. " +
                    "It affects Ultra's playback only. Very low sample-rate files (such as 8 kHz AMR recordings) cannot carry " +
                    "the highest bands, which are skipped automatically. Large boosts reduce overall level slightly to avoid distortion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
