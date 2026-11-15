package com.ultramusicplayer.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ultramusicplayer.app.ads.Ads
import com.ultramusicplayer.app.ads.BannerAd
import com.ultramusicplayer.app.data.AppSettings
import com.ultramusicplayer.app.data.Library
import com.ultramusicplayer.app.ui.components.AddToPlaylistDialog
import com.ultramusicplayer.app.ui.components.LocalSongActions
import com.ultramusicplayer.app.ui.components.SongActions
import com.ultramusicplayer.app.ui.screens.DetailScreen
import com.ultramusicplayer.app.ui.screens.EditSongScreen
import com.ultramusicplayer.app.ui.screens.EqualizerScreen
import com.ultramusicplayer.app.ui.screens.LibraryScreen
import com.ultramusicplayer.app.ui.screens.MiniPlayer
import com.ultramusicplayer.app.ui.screens.NowPlayingScreen
import com.ultramusicplayer.app.ui.screens.OnboardingScreen
import com.ultramusicplayer.app.ui.screens.PlaylistsScreen
import com.ultramusicplayer.app.ui.screens.QueueScreen
import com.ultramusicplayer.app.ui.screens.SearchScreen
import com.ultramusicplayer.app.ui.screens.SettingsScreen
import com.ultramusicplayer.app.ui.screens.SkinsScreen
import com.ultramusicplayer.app.ui.screens.TrimScreen
import com.ultramusicplayer.app.ui.screens.VideoSearchScreen
import com.ultramusicplayer.app.ui.theme.Charcoal
import com.ultramusicplayer.app.ui.theme.SkinBackground
import com.ultramusicplayer.app.ui.theme.UltraTheme
import com.ultramusicplayer.app.ui.theme.isDarkTheme
import com.ultramusicplayer.app.video.VideoSearch
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val NOW = "now"
    const val QUEUE = "queue"
    const val SKINS = "skins"
    const val DETAIL = "detail/{type}/{key}"
    const val EDIT = "edit/{id}"
    const val TRIM = "trim/{id}"
    const val VIDEOS = "videos?q={q}"

    fun detail(type: String, key: String) = "detail/$type/${Uri.encode(key)}"
    fun edit(id: Long) = "edit/$id"
    fun trim(id: Long) = "trim/$id"
    fun videos(query: String) = "videos?q=${Uri.encode(query)}"
}

object DetailType {
    const val ALBUM = "album"
    const val ARTIST = "artist"
    const val ALBUM_ARTIST = "albumartist"
    const val GENRE = "genre"
    const val FOLDER = "folder"
    const val PLAYLIST = "playlist"
    const val RECENT = "recent"
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Charcoal))
        return
    }
    UltraTheme(s.themeMode, Color(s.accent)) {
        val dark = isDarkTheme(s.themeMode)
        val photo by vm.photo.collectAsStateWithLifecycle()
        SkinBackground(s.skinId, photo, s.overlay, dark) {
            if (!s.onboardingDone) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { OnboardingScreen(vm) }
            } else {
                MainNav(vm, s)
            }
        }
    }
}

@Composable
private fun MainNav(vm: MainViewModel, settings: AppSettings) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg)
            }
        }
    }
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val addRequest by vm.addToPlaylistRequest.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var homeTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    // Opening an album, artist, playlist, genre or folder is a natural break for an
    // interstitial; Ads applies the frequency cap.
    LaunchedEffect(backStack?.id) {
        if (backStack?.destination?.route == Routes.DETAIL) Ads.onNaturalBreak(context)
    }

    val actions = remember(nav) {
        SongActions(
            playNext = { vm.playNext(it) },
            addToQueue = { vm.addToQueue(it) },
            addToPlaylist = { vm.addToPlaylistRequest.value = it },
            edit = { nav.navigate(Routes.edit(it.id)) },
            trim = { nav.navigate(Routes.trim(it.id)) },
            videos = { nav.navigate(Routes.videos(VideoSearch.queryFor(it.title, it.artist))) },
            goToAlbum = { nav.navigate(Routes.detail(DetailType.ALBUM, Library.albumKey(it))) },
            goToArtist = { nav.navigate(Routes.detail(DetailType.ARTIST, it.artist)) },
        )
    }

    CompositionLocalProvider(LocalSongActions provides actions) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
                    composable(Routes.HOME) {
                        when (homeTab) {
                            0 -> LibraryScreen(vm, nav, settings)
                            1 -> SearchScreen(vm, nav)
                            2 -> PlaylistsScreen(vm, nav)
                            3 -> EqualizerScreen(vm, settings)
                            else -> SettingsScreen(vm, nav, settings)
                        }
                    }
                    composable(Routes.NOW) { NowPlayingScreen(vm, nav) }
                    composable(Routes.QUEUE) { QueueScreen(vm, nav) }
                    composable(Routes.SKINS) { SkinsScreen(vm, nav, settings) }
                    composable(Routes.DETAIL) { entry ->
                        DetailScreen(
                            vm = vm,
                            nav = nav,
                            type = entry.arguments?.getString("type").orEmpty(),
                            key = entry.arguments?.getString("key").orEmpty(),
                        )
                    }
                    composable(Routes.EDIT) { entry ->
                        EditSongScreen(vm, nav, entry.arguments?.getString("id")?.toLongOrNull() ?: 0L)
                    }
                    composable(Routes.TRIM) { entry ->
                        TrimScreen(vm, nav, entry.arguments?.getString("id")?.toLongOrNull() ?: 0L)
                    }
                    composable(
                        Routes.VIDEOS,
                        arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
                    ) { entry ->
                        VideoSearchScreen(vm, nav, entry.arguments?.getString("q").orEmpty())
                    }
                }
                val showBanner = route != Routes.NOW && route != Routes.TRIM && route != Routes.EDIT
                if (showBanner) BannerAd()
                val hideMini = route == Routes.NOW || route == Routes.TRIM
                if (playerState.current != null && !hideMini) {
                    MiniPlayer(playerState, vm, onOpen = { nav.navigate(Routes.NOW) })
                }
                if (route == Routes.HOME) {
                    HomeBar(homeTab) { homeTab = it }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp))
        }

        val pending = addRequest
        if (pending != null) {
            AddToPlaylistDialog(
                songs = pending,
                playlists = playlists,
                onAdd = { p ->
                    vm.addToPlaylist(p.id, p.name, pending)
                    vm.addToPlaylistRequest.value = null
                },
                onCreate = { name ->
                    vm.createPlaylist(name, pending)
                    vm.addToPlaylistRequest.value = null
                },
                onDismiss = { vm.addToPlaylistRequest.value = null },
            )
        }
    }
}

@Composable
private fun HomeBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Library" to Icons.Filled.LibraryMusic,
        "Search" to Icons.Filled.Search,
        "Playlists" to Icons.Filled.QueueMusic,
        "Equalizer" to Icons.Filled.Equalizer,
        "Settings" to Icons.Filled.Settings,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/** Convenience for screens. */
fun NavHostController.openDetail(type: String, key: String) = navigate(Routes.detail(type, key))
