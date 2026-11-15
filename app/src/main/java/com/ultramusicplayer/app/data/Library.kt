package com.ultramusicplayer.app.data

/** Immutable snapshot of the music library, grouped every way the UI browses it. */
class Library private constructor(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<SongGroup>,
    val albumArtists: List<SongGroup>,
    val genres: List<SongGroup>,
    /** Top-level folders to show (volume contents when there is a single volume). */
    val folderRoots: List<FolderNode>,
    /** Every folder that directly contains songs, plus their ancestors. */
    val allFolders: List<FolderNode>,
    val byId: Map<Long, Song>,
) {
    private val folderIndex: Map<String, FolderNode> = allFolders.associateBy { it.path }
    private val albumIndex: Map<String, Album> = albums.associateBy { it.key }

    fun folder(path: String): FolderNode? = folderIndex[path]
    fun album(key: String): Album? = albumIndex[key]
    fun artist(name: String): SongGroup? = artists.firstOrNull { it.name == name }
    fun albumArtist(name: String): SongGroup? = albumArtists.firstOrNull { it.name == name }
    fun genre(name: String): SongGroup? = genres.firstOrNull { it.name == name }
    fun albumKeyOf(song: Song): String = albumKey(song)

    val isEmpty: Boolean get() = songs.isEmpty()

    companion object {
        private val collator = compareBy<String> { it.lowercase() }

        val EMPTY: Library by lazy { build(emptyList()) }

        fun albumKey(song: Song): String =
            (song.album.lowercase() + "\u0001" + song.albumArtist.ifBlank { song.artist }.lowercase())

        fun build(input: List<Song>): Library {
            val songs = input.sortedWith(compareBy({ it.title.lowercase() }, { it.id }))
            val albums = songs.groupBy { albumKey(it) }.map { (key, list) ->
                val sorted = list.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
                val first = sorted.first()
                Album(
                    key = key,
                    title = first.album,
                    artist = first.albumArtist.ifBlank { first.artist },
                    year = sorted.maxOf { it.year },
                    songs = sorted,
                )
            }.sortedWith(compareBy(collator) { it.title })

            fun groups(selector: (Song) -> String): List<SongGroup> =
                songs.groupBy { selector(it).trim().ifEmpty { "Unknown" } }
                    .map { (name, list) -> SongGroup(name, list) }
                    .sortedWith(compareBy(collator) { it.name })

            val artists = groups { it.artist }
            val albumArtists = groups { it.albumArtist.ifBlank { it.artist } }
            val genres = groups { it.genre.ifBlank { "Unknown genre" } }

            val (roots, all) = buildFolders(songs)
            return Library(songs, albums, artists, albumArtists, genres, roots, all, songs.associateBy { it.id })
        }

        private class NodeBuilder(val path: String, val name: String) {
            val children = LinkedHashMap<String, NodeBuilder>()
            val songs = ArrayList<Song>()
            fun build(sink: MutableList<FolderNode>): FolderNode {
                val node = FolderNode(
                    path = path,
                    name = name,
                    children = children.values.map { it.build(sink) }.sortedBy { it.name.lowercase() },
                    songs = songs.sortedBy { it.fileName.lowercase() },
                )
                sink.add(node)
                return node
            }
        }

        private fun buildFolders(songs: List<Song>): Pair<List<FolderNode>, List<FolderNode>> {
            val volumes = LinkedHashMap<String, NodeBuilder>()
            for (song in songs) {
                val folder = song.folderPath.ifBlank { "/Unknown" }
                val root = PathUtils.volumeRoot(folder)
                var node = volumes.getOrPut(root) { NodeBuilder(root, PathUtils.volumeLabel(root)) }
                val rest = folder.removePrefix(root).trim('/')
                if (rest.isNotEmpty()) {
                    var current = root.trimEnd('/')
                    for (segment in rest.split('/')) {
                        if (segment.isEmpty()) continue
                        current = "$current/$segment"
                        val path = current
                        node = node.children.getOrPut(segment) { NodeBuilder(path, segment) }
                    }
                }
                node.songs.add(song)
            }
            val all = ArrayList<FolderNode>()
            val built = volumes.values.map { it.build(all) }
            // With a single storage volume, start browsing inside it.
            val roots = if (built.size == 1 && built[0].songs.isEmpty()) built[0].children else built
            return roots to all
        }
    }
}
