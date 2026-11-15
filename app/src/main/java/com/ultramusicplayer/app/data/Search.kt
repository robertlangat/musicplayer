package com.ultramusicplayer.app.data

import com.ultramusicplayer.app.data.db.PlaylistSummary

data class SearchResults(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<SongGroup> = emptyList(),
    val albumArtists: List<SongGroup> = emptyList(),
    val genres: List<SongGroup> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val folders: List<FolderNode> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && albumArtists.isEmpty() &&
            genres.isEmpty() && playlists.isEmpty() && folders.isEmpty()
}

/** Case-insensitive search across every browsable dimension, grouped by type. */
object LibrarySearch {
    const val MAX_SONGS = 300

    fun search(library: Library, playlists: List<PlaylistSummary>, rawQuery: String): SearchResults {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return SearchResults(query = rawQuery)
        val terms = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun matches(text: String): Boolean {
            val t = text.lowercase()
            return terms.all { t.contains(it) }
        }
        val songs = library.songs.asSequence()
            .filter { s -> terms.all { s.searchKey.contains(it) } }
            .sortedBy { rank(it.title.lowercase(), q) }
            .take(MAX_SONGS)
            .toList()
        return SearchResults(
            query = rawQuery,
            songs = songs,
            albums = library.albums.filter { matches(it.title + " " + it.artist) }.sortedBy { rank(it.title.lowercase(), q) },
            artists = library.artists.filter { matches(it.name) },
            albumArtists = library.albumArtists.filter { matches(it.name) },
            genres = library.genres.filter { matches(it.name) },
            playlists = playlists.filter { matches(it.name) },
            folders = library.allFolders.filter { matches(it.name) }.sortedBy { it.name.lowercase() },
        )
    }

    /** Exact and prefix matches first. */
    private fun rank(value: String, q: String): Int = when {
        value == q -> 0
        value.startsWith(q) -> 1
        else -> 2
    }
}
