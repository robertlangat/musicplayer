package com.ultramusicplayer.app

import com.ultramusicplayer.app.data.PathUtils
import com.ultramusicplayer.app.lyrics.LrcParser
import com.ultramusicplayer.app.lyrics.LyricMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test
    fun parsesTimestampsAndSorts() {
        val lrc = """
            [ti:Song]
            [ar:Artist]
            [00:12.50]Second line
            [00:01.00]First line
            [01:02.123]Third
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals("First line", lines[0].text)
        assertEquals(12_500L, lines[1].timeMs)
        assertEquals(62_123L, lines[2].timeMs)
    }

    @Test
    fun repeatedTimestampsAndOffset() {
        val lines = LrcParser.parse("[offset:500]\n[00:10.00][00:20.00]Chorus")
        assertEquals(listOf(9_500L, 19_500L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text == "Chorus" })
    }

    @Test
    fun enhancedWordStampsAreRemoved() {
        val lines = LrcParser.parse("[00:05.00]<00:05.00>Hello <00:05.50>world")
        assertEquals("Hello world", lines.single().text)
    }

    @Test
    fun plainTextIsNotSynced() {
        assertFalse(LrcParser.isSynced("Just some words\nand more words"))
        assertTrue(LrcParser.isSynced("[00:01.00]Line"))
    }

    @Test
    fun indexAtFindsCurrentLine() {
        val lines = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, LrcParser.indexAt(lines, 500))
        assertEquals(0, LrcParser.indexAt(lines, 1000))
        assertEquals(1, LrcParser.indexAt(lines, 8999))
        assertEquals(2, LrcParser.indexAt(lines, 60_000))
    }

    @Test
    fun matcherNormalisesTitleAndArtist() {
        assertTrue(LyricMatcher.matches("Daft Punk - One More Time", "One More Time", "Daft Punk"))
        assertTrue(LyricMatcher.matches("one_more_time", "One More Time (Radio Edit)", "Daft Punk"))
        assertTrue(LyricMatcher.matches("03 One More Time", "One More Time", "Unknown artist"))
        assertTrue(LyricMatcher.matches("One More Time - Daft Punk", "One More Time", "Daft Punk"))
        assertFalse(LyricMatcher.matches("Another Song", "One More Time", "Daft Punk"))
    }

    @Test
    fun pathHelpers() {
        assertEquals("/storage/emulated/0/Music/Rock", PathUtils.docIdToPath("primary:Music/Rock"))
        assertEquals("/storage/1A2B-3C4D/Music", PathUtils.docIdToPath("1A2B-3C4D:Music"))
        assertEquals("Internal storage/Music", PathUtils.pretty("/storage/emulated/0/Music"))
        assertEquals("SD card (1A2B-3C4D)/Songs", PathUtils.pretty("/storage/1A2B-3C4D/Songs"))
        assertEquals("/storage/1A2B-3C4D", PathUtils.volumeRoot("/storage/1A2B-3C4D/Songs/x"))
    }
}
