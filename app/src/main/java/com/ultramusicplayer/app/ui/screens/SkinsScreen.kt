package com.ultramusicplayer.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ultramusicplayer.app.data.AppSettings
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.components.UltraTopBar
import com.ultramusicplayer.app.ui.theme.SkinSwatch
import com.ultramusicplayer.app.ui.theme.Skins
import com.ultramusicplayer.app.ui.theme.isDarkTheme

@Composable
fun SkinsScreen(vm: MainViewModel, nav: NavHostController, settings: AppSettings) {
    val photo by vm.photo.collectAsStateWithLifecycle()
    // Tapping a tile previews it; "Use this background" (or tapping again) applies it.
    var previewId by remember { mutableStateOf(settings.skinId) }
    var overlay by remember(settings.overlay) { mutableFloatStateOf(settings.overlay) }
    val dark = isDarkTheme(settings.themeMode)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.setPhotoBackground(uri)
            previewId = Skins.PHOTO_ID
        }
    }

    Column(Modifier.fillMaxSize()) {
        UltraTopBar(title = "Background", subtitle = "${Skins.all.size} skins + your photo", onBack = { nav.popBackStack() })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // Live preview with the readability overlay applied.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    val p = photo
                    if (previewId == Skins.PHOTO_ID && p != null) {
                        AsyncImage(model = p, contentDescription = "Your photo", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    } else {
                        SkinSwatch(Skins.byId(previewId), Modifier.matchParentSize())
                    }
                    Box(Modifier.matchParentSize().background((if (dark) Color.Black else Color.White).copy(alpha = overlay)))
                    Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
                        Text("Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            if (previewId == Skins.PHOTO_ID) "Your photo" else Skins.byId(previewId).name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Song title · Artist name", style = MaterialTheme.typography.bodyMedium)
                    }
                    val applied = previewId == settings.skinId
                    Text(
                        if (applied) "In use" else "Use this background",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = !applied) { vm.setSkin(previewId) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Readability overlay", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = overlay,
                        onValueChange = { overlay = it },
                        onValueChangeFinished = { vm.setOverlay(overlay) },
                        valueRange = 0f..0.85f,
                        modifier = Modifier.semantics { contentDescription = "Background overlay strength" },
                    )
                }
            }
            item {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.8f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                if (previewId == Skins.PHOTO_ID) 3.dp else 0.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val p = photo
                        if (p != null) {
                            AsyncImage(model = p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        }
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        if (photo != null) "Change photo" else "Your photo",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            if (photo != null) {
                item {
                    SkinTile(name = "Use my photo", selected = previewId == Skins.PHOTO_ID, onClick = {
                        if (previewId == Skins.PHOTO_ID) vm.setSkin(Skins.PHOTO_ID) else previewId = Skins.PHOTO_ID
                    }) {
                        AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
            }
            items(Skins.all, key = { it.id }) { skin ->
                SkinTile(name = skin.name, selected = previewId == skin.id, onClick = {
                    if (previewId == skin.id) vm.setSkin(skin.id) else previewId = skin.id
                }) { SkinSwatch(skin, Modifier.matchParentSize()) }
            }
        }
    }
}

@Composable
private fun SkinTile(name: String, selected: Boolean, onClick: () -> Unit, art: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Background $name"
                this.selected = selected
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(12.dp))
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
        ) {
            art()
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Row(Modifier.padding(4.dp)) { Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
    }
}
