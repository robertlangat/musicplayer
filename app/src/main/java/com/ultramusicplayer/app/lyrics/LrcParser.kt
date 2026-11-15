package com.ultramusicplayer.app.lyrics

data class LrcLine(val timeMs: Long, val text: String)

/**
 * Parser for LRC lyric files: supports multiple timestamps per line ([00:12.30][01:40.00]),
 * centisecond or millisecond fractions, the [offset:±ms] tag and enhanced word stamps (<mm:ss.xx>).
 */
object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("^\\[offset:\\s*([+-]?\\d+)\\s*]$", RegexOption.IGNORE_CASE)
    private val wordStamp = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")

    fun isSynced(text: String): Boolean = text.lineSequence().any { line ->
        val t = line.trim()
        timeTag.find(t)?.range?.first == 0
    }

    fun parse(text: String): List<LrcLine> {
        var offset = 0L
        val out = ArrayList<LrcLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            val off = offsetTag.find(line)
            if (off != null) {
                offset = off.groupValues[1].toLongOrNull() ?: 0L
                continue
            }
            var index = 0
            val times = ArrayList<Long>(2)
            while (true) {
                val m = timeTag.find(line, index) ?: break
                if (m.range.first != index) break
                times += toMillis(m)
                index = m.range.last + 1
            }
            if (times.isEmpty()) continue
            val content = line.substring(index).replace(wordStamp, "").trim()
            for (t in times) out += LrcLine(maxOf(0L, t - offset), content)
        }
        out.sortBy { it.timeMs }
        return out
    }

    private fun toMillis(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3]
        val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
        return min * 60_000L + sec * 1000L + ms
    }

    /** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
    fun indexAt(lines: List<LrcLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

/** Name normalisation used to match lyric files against title/artist. */
object LyricMatcher {
    private val brackets = Regex("[(\\[{].*?[)\\]}]")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val leadingTrack = Regex("^\\d{1,3}\\s+")

    fun normalize(value: String): String =
        value.lowercase()
            .replace(brackets, " ")
            .replace(nonWord, " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    /** True when a lyric file's base name plausibly belongs to the given title and artist. */
    fun matches(lyricBaseName: String, title: String, artist: String): Boolean {
        val n = normalize(lyricBaseName).replace(leadingTrack, "")
        val t = normalize(title)
        val a = normalize(artist)
        if (t.isEmpty()) return false
        if (n == t) return true
        if (a.isNotEmpty() && a != "unknown artist") {
            if (n == "$a $t" || n == "$t $a") return true
            if (t.length >= 3 && n.contains(t) && n.contains(a)) return true
        }
        return false
    }
}
