package com.ultramusicplayer.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ultramusicplayer.app.ui.MainViewModel
import com.ultramusicplayer.app.ui.components.UltraTopBar
import com.ultramusicplayer.app.video.VideoResult
import com.ultramusicplayer.app.video.VideoSearch
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VideoSearchScreen(vm: MainViewModel, nav: NavHostController, initialQuery: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = vm.currentSong.value
    var query by rememberSaveable {
        mutableStateOf(initialQuery.ifBlank { current?.let { VideoSearch.queryFor(it.title, it.artist) }.orEmpty() })
    }
    var results by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch(trending: Boolean) {
        if (!VideoSearch.hasApiKey) {
            if (trending) VideoSearch.openTrending(context) else if (query.isNotBlank()) VideoSearch.openSearch(context, query)
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                results = if (trending) VideoSearch.trending(Locale.getDefault().country.ifBlank { "US" }) else VideoSearch.search(query)
            } catch (e: Exception) {
                error = "Couldn't reach YouTube (${e.message}). You can still open the search in YouTube."
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        UltraTopBar("Search Music Videos", subtitle = "Opens YouTube — nothing is downloaded", onBack = { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Song, artist or keyword") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(false) }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { runSearch(false) }, enabled = query.isNotBlank()) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text(if (VideoSearch.hasApiKey) "  Search" else "  Search in YouTube")
                }
                OutlinedButton(onClick = { VideoSearch.openInBrowser(context, query) }, enabled = query.isNotBlank()) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Text("  Browser")
                }
            }
            if (current != null) {
                OutlinedButton(onClick = {
                    query = VideoSearch.queryFor(current.title, current.artist)
                    runSearch(false)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Videos for “${current.title}”", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = { runSearch(true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.TrendingUp, contentDescription = null)
                Text("  Trending Music Videos")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        if (loading) {
            CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(results, key = { it.id }) { v ->
                Row(
                    Modifier.fillMaxWidth().clickable { VideoSearch.openVideo(context, v.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = v.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 120.dp, height = 68.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(v.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(v.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
