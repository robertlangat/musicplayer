package com.ultramusicplayer.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ultramusicplayer.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri?,
    val albumId: Long,
)

data class PlayerUiState(
    val connected: Boolean = false,
    val current: QueueEntry? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
)

/** UI-side bridge to the playback service through a Media3 MediaController. */
@UnstableApi
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
            refresh(queueChanged)
        }
    }

    init {
        scope.launch {
            PlaybackService.shuffleEnabled.collect { s -> _state.update { it.copy(shuffle = s) } }
        }
        scope.launch {
            while (isActive) {
                delay(250)
                val c = controller ?: continue
                if (c.isPlaying) {
                    _state.update { it.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0L)) }
                }
            }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh(queueChanged = true)
            val actions = pending.toList()
            pending.clear()
            actions.forEach { it(c) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    private fun refresh(queueChanged: Boolean) {
        val c = controller ?: return
        val queue = if (queueChanged) {
            (0 until c.mediaItemCount).map { entry(it, c.getMediaItemAt(it)) }
        } else {
            _state.value.queue
        }
        val index = c.currentMediaItemIndex
        _state.update {
            it.copy(
                connected = true,
                queue = queue,
                currentIndex = if (c.mediaItemCount == 0) -1 else index,
                current = queue.getOrNull(index)?.takeIf { c.mediaItemCount > 0 },
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition,
                durationMs = c.duration.coerceAtLeast(0L),
                repeatMode = c.repeatMode,
            )
        }
    }

    private fun entry(index: Int, item: MediaItem): QueueEntry {
        val m = item.mediaMetadata
        return QueueEntry(
            index = index,
            mediaId = item.mediaId,
            title = m.title?.toString().orEmpty(),
            artist = m.artist?.toString().orEmpty(),
            album = m.albumTitle?.toString().orEmpty(),
            uri = MediaItems.uriOf(item),
            albumId = m.extras?.getLong(MediaItems.EXTRA_ALBUM_ID, -1L) ?: -1L,
        )
    }

    // ---- commands ---------------------------------------------------------------------------

    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean? = null) = withController { c ->
        if (songs.isEmpty()) return@withController
        if (shuffle != null) setShuffle(c, shuffle)
        c.setMediaItems(songs.map { MediaItems.fromSong(it) }, startIndex.coerceIn(0, songs.size - 1), 0L)
        c.prepare()
        c.play()
    }

    /** Inserts [song] right after the current track, keeping the rest of the queue intact. */
    fun playNext(songs: List<Song>) = withController { c ->
        if (songs.isEmpty()) return@withController
        val items = songs.map { MediaItems.fromSong(it) }
        if (c.mediaItemCount == 0) {
            c.setMediaItems(items)
            c.prepare()
            c.play()
        } else {
            c.addMediaItems(c.currentMediaItemIndex + 1, items)
        }
    }

    fun addToQueue(songs: List<Song>) = withController { c ->
        if (songs.isEmpty()) return@withController
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItems(songs.map { MediaItems.fromSong(it) })
        if (wasEmpty) c.prepare()
    }

    fun removeFromQueue(index: Int) = withController { c -> if (index in 0 until c.mediaItemCount) c.removeMediaItem(index) }

    fun moveInQueue(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun skipTo(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun pause() = withController { it.pause() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(ms: Long) = withController { c ->
        c.seekTo(ms)
        _state.update { it.copy(positionMs = ms) }
    }

    fun toggleShuffle() = withController { c -> setShuffle(c, !_state.value.shuffle) }

    private fun setShuffle(c: MediaController, enabled: Boolean) {
        c.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SET_SHUFFLE, Bundle.EMPTY),
            Bundle().apply { putBoolean(PlaybackService.EXTRA_ENABLED, enabled) },
        )
    }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() = withController { c ->
        c.sendCustomCommand(SessionCommand(PlaybackService.CMD_STOP, Bundle.EMPTY), Bundle.EMPTY)
    }

    fun currentPosition(): Long = controller?.currentPosition ?: _state.value.positionMs
}
