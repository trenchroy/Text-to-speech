package com.example

import com.example.domain.ScriptChunker
import com.example.domain.VoiceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun voiceCatalog_hasExactlySevenDistinctVoices() {
        val voices = VoiceCatalog.voices
        assertEquals(7, voices.size)
        assertEquals(7, voices.map { it.id }.distinct().size)
        assertEquals(7, voices.map { it.geminiVoiceName }.distinct().size)
    }

    @Test
    fun scriptChunker_preservesSentencesAndHindiDanda() {
        val script = "यह पहला वाक्य है। Yeh doosra Hinglish sentence hai! And this is the third sentence?\n\nNaya paragraph shuru hota hai."
        val analysis = ScriptChunker.analyze(script)
        assertTrue(analysis.wordCount >= 18)
        assertTrue(analysis.parts.isNotEmpty())
        val allSpans = analysis.parts.flatMap { it.sentences }
        assertEquals(4, allSpans.size)
        assertTrue(allSpans[2].endsParagraph)
        for (span in allSpans) {
            val extracted = script.substring(span.startIndex, span.endIndex).trim()
            assertEquals(span.text, extracted)
        }
    }
}
