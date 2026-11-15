package com.ultramusicplayer.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ultramusicplayer.app.data.LibraryRepository
import com.ultramusicplayer.app.data.MediaScanner
import com.ultramusicplayer.app.data.PlaylistRepository
import com.ultramusicplayer.app.data.SettingsRepository
import com.ultramusicplayer.app.data.TagEditor
import com.ultramusicplayer.app.data.db.AppDatabase
import com.ultramusicplayer.app.lyrics.LyricsRepository
import com.ultramusicplayer.app.playback.ArtFetcher
import com.ultramusicplayer.app.playback.ArtKeyer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons shared by the UI and the playback service. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val scanner = MediaScanner(context)
    val library = LibraryRepository(context, scanner, settings, database.overrides(), appScope)
    val playlists = PlaylistRepository(database)
    val lyrics = LyricsRepository(context, library, database.lyricLinks())
    val tagEditor = TagEditor(context, database.overrides())
}

class UltraApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.library.startObserving()
        container.library.requestRefresh()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(ArtKeyer())
                add(ArtFetcher.Factory(this@UltraApp))
            }
            .crossfade(true)
            .build()
}
