package com.ultramusicplayer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.widget.RemoteViews
import com.ultramusicplayer.app.MainActivity
import com.ultramusicplayer.app.R
import com.ultramusicplayer.app.playback.PlaybackService
import java.io.File

data class WidgetState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasQueue: Boolean,
    val art: Bitmap?,
)

enum class WidgetKind(val layout: Int, val provider: Class<out AppWidgetProvider>, val showsArt: Boolean) {
    SLIM(R.layout.widget_slim, SlimWidgetProvider::class.java, false),
    ALBUM(R.layout.widget_album, AlbumWidgetProvider::class.java, true),
    STRIP(R.layout.widget_strip, StripWidgetProvider::class.java, true),
}

/** Renders all three widget variants from the current playback state. */
object WidgetUpdater {
    private const val PREFS = "widget_state"
    private const val ART_FILE = "widget_art.png"

    fun push(context: Context, state: WidgetState, artChanged: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", state.title)
            .putString("artist", state.artist)
            .putBoolean("hasQueue", state.hasQueue)
            .apply()
        if (artChanged) saveArt(context, state.art)
        val manager = AppWidgetManager.getInstance(context)
        for (kind in WidgetKind.entries) {
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, build(context, kind, state))
        }
    }

    fun saved(context: Context): WidgetState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val artFile = File(context.filesDir, ART_FILE)
        val art = if (artFile.exists()) BitmapFactory.decodeFile(artFile.path) else null
        return WidgetState(
            title = p.getString("title", null) ?: context.getString(R.string.nothing_playing),
            artist = p.getString("artist", null) ?: context.getString(R.string.tap_to_open),
            isPlaying = false,
            hasQueue = p.getBoolean("hasQueue", false),
            art = art,
        )
    }

    private fun saveArt(context: Context, art: Bitmap?) {
        val f = File(context.filesDir, ART_FILE)
        if (art == null) {
            f.delete()
            return
        }
        try {
            f.outputStream().use { art.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
        }
    }

    fun build(context: Context, kind: WidgetKind, state: WidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, kind.layout)
        views.setTextViewText(R.id.w_title, state.title)
        if (kind == WidgetKind.STRIP) views.setTextViewText(R.id.w_artist, state.artist)
        if (kind.showsArt) {
            if (state.art != null) views.setImageViewBitmap(R.id.w_art, state.art) else views.setImageViewResource(R.id.w_art, R.drawable.ic_w_note)
        }
        views.setImageViewResource(R.id.w_play, if (state.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        views.setContentDescription(R.id.w_play, context.getString(if (state.isPlaying) R.string.pause else R.string.play))

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.w_root, open)
        // Starting playback must go through a foreground-service start (allowed because the
        // user tapped a widget). Pause / next / previous are delivered to the provider, which
        // binds a MediaController: binding is always allowed, even from the background.
        val play = when {
            state.isPlaying -> action(context, kind, ACTION_PAUSE)
            state.hasQueue -> playKey(context)
            else -> open
        }
        views.setOnClickPendingIntent(R.id.w_play, play)
        views.setOnClickPendingIntent(R.id.w_next, if (state.hasQueue) action(context, kind, ACTION_NEXT) else open)
        if (kind != WidgetKind.SLIM) {
            views.setOnClickPendingIntent(R.id.w_prev, if (state.hasQueue) action(context, kind, ACTION_PREVIOUS) else open)
        }
        return views
    }

    const val ACTION_PAUSE = "com.ultramusicplayer.app.widget.PAUSE"
    const val ACTION_NEXT = "com.ultramusicplayer.app.widget.NEXT"
    const val ACTION_PREVIOUS = "com.ultramusicplayer.app.widget.PREVIOUS"

    private fun playKey(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        return PendingIntent.getForegroundService(
            context, KeyEvent.KEYCODE_MEDIA_PLAY, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(context: Context, kind: WidgetKind, action: String): PendingIntent {
        val intent = Intent(context, kind.provider).setAction(action)
        return PendingIntent.getBroadcast(
            context, action.hashCode() + kind.ordinal, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

abstract class BaseWidgetProvider : AppWidgetProvider() {
    abstract val kind: WidgetKind

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, WidgetUpdater.build(context, kind, WidgetUpdater.saved(context)))
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action: String = intent.action.orEmpty()
        if (action == WidgetUpdater.ACTION_PAUSE || action == WidgetUpdater.ACTION_NEXT || action == WidgetUpdater.ACTION_PREVIOUS) {
            val pending = goAsync()
            WidgetCommands.run(context.applicationContext, action) { pending.finish() }
        } else {
            super.onReceive(context, intent)
        }
    }
}

/** Executes a widget button through a short-lived MediaController. */
private object WidgetCommands {
    fun run(context: Context, action: String, done: () -> Unit) {
        val token = androidx.media3.session.SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = androidx.media3.session.MediaController.Builder(context, token).buildAsync()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        future.addListener({
            val controller = try {
                future.get()
            } catch (e: Exception) {
                done()
                return@addListener
            }
            var attempts = 0
            fun execute() {
                // Right after the service starts, the saved queue may still be loading.
                if (controller.mediaItemCount == 0 && attempts++ < 15) {
                    handler.postDelayed({ execute() }, 100)
                    return
                }
                when (action) {
                    WidgetUpdater.ACTION_PAUSE -> controller.pause()
                    WidgetUpdater.ACTION_NEXT -> controller.seekToNextMediaItem()
                    WidgetUpdater.ACTION_PREVIOUS -> controller.seekToPrevious()
                }
                handler.postDelayed({
                    controller.release()
                    done()
                }, 300)
            }
            execute()
        }, { handler.post(it) })
    }
}

class SlimWidgetProvider : BaseWidgetProvider() {
    override val kind = WidgetKind.SLIM
}

class AlbumWidgetProvider : BaseWidgetProvider() {
    override val kind = WidgetKind.ALBUM
}

class StripWidgetProvider : BaseWidgetProvider() {
    override val kind = WidgetKind.STRIP
}
