package com.ultramusicplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataRenderer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ultramusicplayer.app.MainActivity
import com.ultramusicplayer.app.R
import com.ultramusicplayer.app.UltraApp
import com.ultramusicplayer.app.data.ShakeAction
import com.ultramusicplayer.app.widget.WidgetState
import com.ultramusicplayer.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background playback: ExoPlayer with the in-app five-band EQ, a Media3 session (notification,
 * lock screen, headset and Bluetooth controls), queue persistence, history, widgets and shake.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    companion object {
        const val CMD_STOP = "ultra.cmd.STOP"
        const val CMD_SET_SHUFFLE = "ultra.cmd.SET_SHUFFLE"
        const val EXTRA_ENABLED = "enabled"

        private val _shuffle = MutableStateFlow(false)
        /** In-process shuffle state (the app shuffles the real queue so "Play next" stays next). */
        val shuffleEnabled: StateFlow<Boolean> = _shuffle.asStateFlow()
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var queueStore: QueueStore
    private lateinit var shake: ShakeDetector
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var originalOrder: List<MediaItem>? = null
    private var restoring = false
    private var saveJob: Job? = null
    private var shakeEnabled = false
    private var shakeAction = ShakeAction.NEXT
    private var lastWidgetMediaId: String? = null

    private val container get() = (application as UltraApp).container

    override fun onCreate() {
        super.onCreate()
        queueStore = QueueStore(this)
        val eq = FiveBandEqProcessor()
        val renderersFactory = RenderersFactory { handler, _, audioListener, _, metadataOutput ->
            val sink = DefaultAudioSink.Builder(this)
                .setAudioProcessors(arrayOf<AudioProcessor>(eq))
                .build()
            arrayOf<Renderer>(
                MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener, sink),
                MetadataRenderer(metadataOutput, handler.looper),
            )
        }
        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones disconnect
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(playerListener)

        val stopButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.stop))
            .setIconResId(R.drawable.ic_stop)
            .setSessionCommand(SessionCommand(CMD_STOP, Bundle.EMPTY))
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setBitmapLoader(ArtBitmapLoader(this))
            .setCustomLayout(ImmutableList.of(stopButton))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )

        shake = ShakeDetector(this) { onShake() }
        observeSettings()
        restoreQueue()
        startPositionSaver()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueueNow()
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueueNow()
        shake.stop()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ---- settings: EQ + shake -------------------------------------------------------------

    private fun observeSettings() {
        scope.launch {
            container.settings.settings.collect { s ->
                EqParams.update(s.eqEnabled, s.eqGains)
                shakeEnabled = s.shakeEnabled
                shakeAction = s.shakeAction
                shake.sensitivity = s.shakeSensitivity
                updateShakeRegistration()
            }
        }
    }

    private fun updateShakeRegistration() {
        // Only listen while music is actually playing, to save battery.
        if (shakeEnabled && player.isPlaying) shake.start() else shake.stop()
    }

    private fun onShake() {
        if (!player.isPlaying) return
        when (shakeAction) {
            ShakeAction.NEXT -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            ShakeAction.PREVIOUS -> player.seekToPreviousMediaItem()
        }
    }

    // ---- queue persistence ----------------------------------------------------------------

    private fun restoreQueue() {
        restoring = true
        scope.launch {
            val snapshot = withContext(Dispatchers.IO) { queueStore.load() }
            if (snapshot != null && player.mediaItemCount == 0) {
                player.setMediaItems(snapshot.items, snapshot.index, snapshot.positionMs)
                player.repeatMode = snapshot.repeatMode
                originalOrder = if (snapshot.shuffled) snapshot.originalOrder else null
                _shuffle.value = snapshot.shuffled
                player.prepare()
            }
            restoring = false
            pushWidgets(force = true)
        }
    }

    private fun snapshot(): QueueStore.Snapshot = QueueStore.Snapshot(
        items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) },
        index = player.currentMediaItemIndex.coerceAtLeast(0),
        positionMs = player.currentPosition.coerceAtLeast(0L),
        repeatMode = player.repeatMode,
        shuffled = _shuffle.value,
        originalOrder = originalOrder,
    )

    private fun scheduleSave() {
        if (restoring) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(800)
            val snap = snapshot()
            withContext(Dispatchers.IO) { queueStore.save(snap) }
        }
    }

    private fun saveQueueNow() {
        if (!::player.isInitialized || restoring) return
        if (player.mediaItemCount == 0) return
        queueStore.save(snapshot())
    }

    private fun startPositionSaver() {
        scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) scheduleSave()
            }
        }
    }

    // ---- shuffle: physically reorders the queue around the current track -------------------

    private fun setShuffle(enable: Boolean) {
        if (enable == _shuffle.value) return
        val count = player.mediaItemCount
        val cur = player.currentMediaItemIndex
        if (enable) {
            val all = (0 until count).map { player.getMediaItemAt(it) }
            originalOrder = all
            if (count > 1 && cur >= 0) {
                val others = all.filterIndexed { i, _ -> i != cur }.shuffled()
                if (cur + 1 < count) player.removeMediaItems(cur + 1, count)
                if (cur > 0) player.removeMediaItems(0, cur)
                player.addMediaItems(others)
            }
        } else {
            val original = originalOrder
            originalOrder = null
            if (original != null && count > 1 && cur >= 0) {
                val present = (0 until count).map { player.getMediaItemAt(it) }
                val current = present[cur]
                val remaining = present.toMutableList()
                remaining.removeAt(cur)
                val ordered = ArrayList<MediaItem>()
                var currentPos = -1
                for (item in original) {
                    if (currentPos < 0 && item.mediaId == current.mediaId) {
                        currentPos = ordered.size
                        ordered.add(current)
                        continue
                    }
                    val i = remaining.indexOfFirst { it.mediaId == item.mediaId }
                    if (i >= 0) ordered.add(remaining.removeAt(i))
                }
                if (currentPos < 0) {
                    currentPos = ordered.size
                    ordered.add(current)
                }
                ordered.addAll(remaining) // items queued while shuffled go to the end
                if (cur + 1 < count) player.removeMediaItems(cur + 1, count)
                if (cur > 0) player.removeMediaItems(0, cur)
                if (currentPos > 0) player.addMediaItems(0, ordered.subList(0, currentPos))
                if (currentPos + 1 < ordered.size) player.addMediaItems(ordered.subList(currentPos + 1, ordered.size))
            }
        }
        _shuffle.value = enable
        scheduleSave()
    }

    // ---- player events ---------------------------------------------------------------------

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Record real plays only (not the silent restore of a saved queue).
            val counts = reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED || player.playWhenReady
            if (mediaItem != null && counts && !restoring) {
                val id = mediaItem.mediaId.toLongOrNull()
                val uri = MediaItems.uriOf(mediaItem)
                if (id != null && uri != null) scope.launch(Dispatchers.IO) { container.playlists.recordPlay(id, uri.toString()) }
            }
            pushWidgets()
            scheduleSave()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            scheduleSave()
            pushWidgets()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateShakeRegistration()
            pushWidgets()
            if (!isPlaying) scheduleSave()
        }

        override fun onRepeatModeChanged(repeatMode: Int) = scheduleSave()

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            // External controllers (Bluetooth, Android Auto) toggle ExoPlayer's shuffle flag;
            // translate that into the app's queue shuffle.
            if (shuffleModeEnabled) {
                player.shuffleModeEnabled = false
                setShuffle(!_shuffle.value)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val index = player.currentMediaItemIndex
            val title = player.currentMediaItem?.mediaMetadata?.title ?: "this track"
            Toast.makeText(
                this@PlaybackService,
                "Can't play “$title”: the file is missing, damaged or unsupported. Skipping.",
                Toast.LENGTH_LONG,
            ).show()
            if (index >= 0 && player.mediaItemCount > 1) {
                player.removeMediaItem(index)
                player.prepare()
                player.play()
            } else if (index >= 0) {
                player.removeMediaItem(index)
            }
        }
    }

    private fun pushWidgets(force: Boolean = false) {
        val item = player.currentMediaItem
        val mediaId = item?.mediaId
        val artChanged = force || mediaId != lastWidgetMediaId
        lastWidgetMediaId = mediaId
        val title = item?.mediaMetadata?.title?.toString() ?: getString(R.string.nothing_playing)
        val artist = item?.mediaMetadata?.artist?.toString() ?: getString(R.string.tap_to_open)
        val isPlaying = player.isPlaying
        val hasQueue = player.mediaItemCount > 0
        val uri = item?.let { MediaItems.uriOf(it) }
        val albumId = item?.mediaMetadata?.extras?.getLong(MediaItems.EXTRA_ALBUM_ID, -1L) ?: -1L
        val appContext = applicationContext
        scope.launch(Dispatchers.IO) {
            val art = if (artChanged && uri != null) ArtworkLoader.load(appContext, uri, albumId, 300) else null
            val state = WidgetState(title, artist, isPlaying, hasQueue, art ?: if (artChanged) null else WidgetUpdater.saved(appContext).art)
            WidgetUpdater.push(appContext, state, artChanged)
        }
    }

    // ---- session callback --------------------------------------------------------------------

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val commands = base.availableSessionCommands.buildUpon()
                .add(SessionCommand(CMD_STOP, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_SHUFFLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(commands, base.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_STOP -> {
                    saveQueueNow()
                    player.pause()
                    player.stop() // idle player: Media3 removes the notification
                }
                CMD_SET_SHUFFLE -> setShuffle(args.getBoolean(EXTRA_ENABLED, !_shuffle.value))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { MediaItems.resolve(it) }.toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resolved = mediaItems.map { MediaItems.resolve(it) }
            if (!_shuffle.value || resolved.size <= 1) {
                originalOrder = if (_shuffle.value) resolved else null
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs))
            }
            // Shuffle is on: selected track first, the rest in random order.
            originalOrder = resolved
            val start = if (startIndex in resolved.indices) startIndex else 0
            val reordered = listOf(resolved[start]) + resolved.filterIndexed { i, _ -> i != start }.shuffled()
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(reordered, 0, startPositionMs))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val snapshot = queueStore.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("No saved queue"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(snapshot.items, snapshot.index, snapshot.positionMs),
            )
        }
    }
}
