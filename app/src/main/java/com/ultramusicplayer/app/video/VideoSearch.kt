package com.ultramusicplayer.app.video

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ultramusicplayer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class VideoResult(val id: String, val title: String, val channel: String, val thumbnail: String)

/**
 * Music video discovery. Without an API key everything opens in the YouTube app or the browser.
 * With a YouTube Data API key (supplied at build time, never committed) results show in-app.
 * Nothing is ever downloaded.
 */
object VideoSearch {
    const val TRENDING_URL = "https://charts.youtube.com/"

    val hasApiKey: Boolean get() = BuildConfig.YOUTUBE_API_KEY.isNotBlank()

    fun queryFor(title: String, artist: String): String =
        listOf(artist.takeUnless { it.equals("Unknown artist", true) }, title, "official music video")
            .filterNotNull()
            .joinToString(" ")

    fun openSearch(context: Context, query: String) {
        val app = Intent(Intent.ACTION_SEARCH)
            .setPackage("com.google.android.youtube")
            .putExtra("query", query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(app)
        } catch (e: ActivityNotFoundException) {
            openUrl(context, "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
        }
    }

    fun openInBrowser(context: Context, query: String) =
        openUrl(context, "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))

    fun openVideo(context: Context, id: String) = openUrl(context, "https://www.youtube.com/watch?v=$id")

    fun openTrending(context: Context) = openUrl(context, TRENDING_URL)

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    suspend fun search(query: String): List<VideoResult> = fetch(
        "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoCategoryId=10&maxResults=25&q=" +
            URLEncoder.encode(query, "UTF-8"),
        isSearch = true,
    )

    suspend fun trending(regionCode: String): List<VideoResult> = fetch(
        "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&videoCategoryId=10&maxResults=25&regionCode=$regionCode",
        isSearch = false,
    )

    private suspend fun fetch(url: String, isSearch: Boolean): List<VideoResult> = withContext(Dispatchers.IO) {
        val conn = URL("$url&key=${BuildConfig.YOUTUBE_API_KEY}").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) throw IllegalStateException("YouTube API error ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val items = JSONObject(body).optJSONArray("items") ?: return@withContext emptyList()
            (0 until items.length()).mapNotNull { i ->
                val o = items.getJSONObject(i)
                val id = if (isSearch) o.optJSONObject("id")?.optString("videoId") else o.optString("id")
                val snippet = o.optJSONObject("snippet") ?: return@mapNotNull null
                if (id.isNullOrEmpty()) return@mapNotNull null
                VideoResult(
                    id = id,
                    title = android.text.Html.fromHtml(snippet.optString("title"), android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
                    channel = snippet.optString("channelTitle"),
                    thumbnail = snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty(),
                )
            }
        } finally {
            conn.disconnect()
        }
    }
}
