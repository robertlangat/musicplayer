package com.ultramusicplayer.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_songs", indices = [Index("playlistId")])
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val songUri: String,
    val position: Int,
)

@Entity(tableName = "history", indices = [Index("playedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val songUri: String,
    val playedAt: Long,
)

/** App-only tag values for files that cannot (or may not) be rewritten. */
@Entity(tableName = "tag_overrides")
data class TagOverrideEntity(
    @PrimaryKey val songId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val trackNumber: Int?,
    val year: Int?,
    val updatedAt: Long,
)

/** A lyric file the user picked manually for a song. */
@Entity(tableName = "lyric_links")
data class LyricLinkEntity(
    @PrimaryKey val songId: Long,
    val lyricUri: String,
)

data class PlaylistSummary(val id: Long, val name: String, val songCount: Int)

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, " +
            "(SELECT COUNT(*) FROM playlist_songs s WHERE s.playlistId = p.id) AS songCount " +
            "FROM playlists p ORDER BY p.name COLLATE NOCASE",
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun clearSongs(id: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position")
    fun observeSongs(id: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position")
    suspend fun songs(id: Long): List<PlaylistSongEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert
    suspend fun insertSongs(items: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE rowId = :rowId")
    suspend fun deleteSong(rowId: Long)

    @Update
    suspend fun updateSongs(items: List<PlaylistSongEntity>)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(item: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 400")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT 400)")
    suspend fun trim()

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM tag_overrides")
    fun observeAll(): Flow<List<TagOverrideEntity>>

    @Query("SELECT * FROM tag_overrides WHERE songId = :songId")
    suspend fun get(songId: Long): TagOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TagOverrideEntity)

    @Query("DELETE FROM tag_overrides WHERE songId = :songId")
    suspend fun delete(songId: Long)
}

@Dao
interface LyricLinkDao {
    @Query("SELECT * FROM lyric_links WHERE songId = :songId")
    suspend fun get(songId: Long): LyricLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LyricLinkEntity)

    @Query("DELETE FROM lyric_links WHERE songId = :songId")
    suspend fun delete(songId: Long)
}

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class,
        TagOverrideEntity::class,
        LyricLinkEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun history(): HistoryDao
    abstract fun overrides(): OverrideDao
    abstract fun lyricLinks(): LyricLinkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ultra.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
