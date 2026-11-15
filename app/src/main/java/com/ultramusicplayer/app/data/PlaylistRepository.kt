package com.ultramusicplayer.app.data

import androidx.room.withTransaction
import com.ultramusicplayer.app.data.db.AppDatabase
import com.ultramusicplayer.app.data.db.HistoryEntity
import com.ultramusicplayer.app.data.db.PlaylistEntity
import com.ultramusicplayer.app.data.db.PlaylistSongEntity
import com.ultramusicplayer.app.data.db.PlaylistSummary
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val db: AppDatabase) {
    private val dao = db.playlists()
    private val historyDao = db.history()

    val summaries: Flow<List<PlaylistSummary>> = dao.observeSummaries()
    val history: Flow<List<HistoryEntity>> = historyDao.observeRecent()

    fun songs(playlistId: Long): Flow<List<PlaylistSongEntity>> = dao.observeSongs(playlistId)

    suspend fun create(name: String): Long =
        dao.insert(PlaylistEntity(name = name.trim().ifEmpty { "New playlist" }, createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) {
        if (name.isNotBlank()) dao.rename(id, name.trim())
    }

    suspend fun delete(id: Long) = db.withTransaction {
        dao.clearSongs(id)
        dao.delete(id)
    }

    suspend fun add(playlistId: Long, songs: List<Song>) = db.withTransaction {
        var pos = dao.maxPosition(playlistId)
        dao.insertSongs(songs.map { PlaylistSongEntity(playlistId = playlistId, songId = it.id, songUri = it.uri.toString(), position = ++pos) })
    }

    suspend fun remove(rowId: Long) = dao.deleteSong(rowId)

    /** Moves the entry at [from] to [to] and renumbers positions. */
    suspend fun move(playlistId: Long, from: Int, to: Int) = db.withTransaction {
        val items = dao.songs(playlistId).toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return@withTransaction
        val moved = items.removeAt(from)
        items.add(to, moved)
        dao.updateSongs(items.mapIndexed { i, e -> e.copy(position = i) })
    }

    suspend fun recordPlay(song: Song) {
        historyDao.insert(HistoryEntity(songId = song.id, songUri = song.uri.toString(), playedAt = System.currentTimeMillis()))
        historyDao.trim()
    }

    suspend fun recordPlay(songId: Long, uri: String) {
        historyDao.insert(HistoryEntity(songId = songId, songUri = uri, playedAt = System.currentTimeMillis()))
        historyDao.trim()
    }

    suspend fun clearHistory() = historyDao.clear()
}
