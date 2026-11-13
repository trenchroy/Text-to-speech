package com.example.domain

data class SentenceSpan(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val endsParagraph: Boolean
)

data class AudioPart(
    val index: Int,
    val text: String,
    val sentences: List<SentenceSpan>,
    val startIndex: Int,
    val endIndex: Int,
    val endsWithParagraph: Boolean,
    val estimatedDurationMs: Long
)

data class ScriptAnalysis(
    val wordCount: Int,
    val charCount: Int,
    val parts: List<AudioPart>,
    val estimatedTotalMs: Long
)

enum class SampleLanguage(val label: String) {
    HINGLISH("Hinglish"),
    HINDI("Hindi"),
    ENGLISH("English")
}

data class SampleScript(
    val language: SampleLanguage,
    val content: String
)

object ScriptChunker {

    private const val FIRST_PART_TARGET_CHARS = 220
    private const val NORMAL_PART_TARGET_CHARS = 380
    private const val MAX_SINGLE_SENTENCE_CHARS = 520

    val sampleScripts: List<SampleScript> = listOf(
        SampleScript(
            language = SampleLanguage.HINGLISH,
            content = "Zindagi mein har bada badlav ek chhote step se shuru hota hai. Jab hum roz thoda-thoda seekhte hain, toh ek din wahi chhoti koshish hamari sabse badi taqat ban jaati hai.\n\nAaj kal technology itni fast badal rahi hai ki sirf hard work nahi, smart storytelling bhi zaroori hai. Apni awaaz aur ideas ko duniya tak pahunchane ka yahi sahi waqt hai!"
        ),
        SampleScript(
            language = SampleLanguage.HINDI,
            content = "सफलता कभी एक रात में नहीं मिलती, बल्कि यह हर दिन की शांत मेहनत और धैर्य का परिणाम होती है। जब रास्ते कठिन लगने लगें, तब भी अपने लक्ष्य पर भरोसा बनाए रखना सबसे ज़रूरी है।\n\nहर नई सुबह हमें एक नया अवसर देती है कि हम कल से बेहतर बन सकें। बस पहला कदम पूरे विश्वास के साथ उठाइए।"
        ),
        SampleScript(
            language = SampleLanguage.ENGLISH,
            content = "Every great story begins in silence, waiting for the right voice to bring it to life. Whether you are narrating a deep mystery or explaining a breakthrough idea, rhythm and emotion make listeners stay until the very last word.\n\nTake a breath, press play, and let your words flow naturally without interruption."
        )
    )

    fun analyze(rawText: String): ScriptAnalysis {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return ScriptAnalysis(
                wordCount = 0,
                charCount = 0,
                parts = emptyList(),
                estimatedTotalMs = 0L
            )
        }

        val charCount = rawText.length
        val wordCount = trimmed.split(Regex("\\s+")).count { it.isNotBlank() }
        val sentences = extractSentences(rawText)
        val parts = groupIntoParts(sentences)
        val totalMs = parts.sumOf { it.estimatedDurationMs }

        return ScriptAnalysis(
            wordCount = wordCount,
            charCount = charCount,
            parts = parts,
            estimatedTotalMs = totalMs
        )
    }

    private fun extractSentences(text: String): List<SentenceSpan> {
        val result = mutableListOf<SentenceSpan>()
        val length = text.length
        var i = 0

        // Skip leading whitespace
        while (i < length && text[i].isWhitespace()) i++
        var sentenceStart = i

        while (i < length) {
            val ch = text[i]
            val isLineBreak = ch == '\n'
            val isTerminator = ch == '.' || ch == '!' || ch == '?' || ch == '।' || ch == '॥'

            if (isTerminator) {
                // Consume consecutive terminators / closing quotes / brackets
                var end = i + 1
                while (end < length && (text[end] == '.' || text[end] == '!' || text[end] == '?' ||
                            text[end] == '।' || text[end] == '"' || text[end] == '\'' ||
                            text[end] == ')' || text[end] == '”' || text[end] == '’')
                ) {
                    end++
                }
                // Check if followed by whitespace or end-of-text (avoid splitting decimals like 1.5 or Mr.)
                val nextChar = if (end < length) text[end] else ' '
                val looksLikeDecimal = ch == '.' && i > 0 && text[i - 1].isDigit() &&
                    (i + 1 < length && text[i + 1].isDigit())

                if (!looksLikeDecimal && (nextChar.isWhitespace() || end == length)) {
                    var wsEnd = end
                    var sawNewline = false
                    while (wsEnd < length && text[wsEnd].isWhitespace()) {
                        if (text[wsEnd] == '\n') sawNewline = true
                        wsEnd++
                    }
                    val snippet = text.substring(sentenceStart, end).trim()
                    if (snippet.isNotEmpty()) {
                        addPossiblySplitSentence(result, snippet, sentenceStart, end, sawNewline)
                    }
                    sentenceStart = wsEnd
                    i = wsEnd
                    continue
                }
            } else if (isLineBreak) {
                val snippet = text.substring(sentenceStart, i).trim()
                var wsEnd = i + 1
                while (wsEnd < length && text[wsEnd].isWhitespace()) {
                    wsEnd++
                }
                if (snippet.isNotEmpty()) {
                    addPossiblySplitSentence(result, snippet, sentenceStart, i, endsParagraph = true)
                }
                sentenceStart = wsEnd
                i = wsEnd
                continue
            }
            i++
        }

        if (sentenceStart < length) {
            val tail = text.substring(sentenceStart, length).trim()
            if (tail.isNotEmpty()) {
                addPossiblySplitSentence(result, tail, sentenceStart, length, endsParagraph = false)
            }
        }

        return result
    }

    private fun addPossiblySplitSentence(
        out: MutableList<SentenceSpan>,
        snippet: String,
        origStart: Int,
        origEnd: Int,
        endsParagraph: Boolean
    ) {
        if (snippet.length <= MAX_SINGLE_SENTENCE_CHARS) {
            out.add(SentenceSpan(snippet, origStart, origEnd, endsParagraph))
            return
        }
        // Only if a single sentence without periods is extraordinarily long, split safely on comma/space
        var offset = 0
        while (offset < snippet.length) {
            val remaining = snippet.length - offset
            if (remaining <= MAX_SINGLE_SENTENCE_CHARS) {
                val sub = snippet.substring(offset).trim()
                if (sub.isNotEmpty()) {
                    out.add(
                        SentenceSpan(
                            text = sub,
                            startIndex = origStart + offset,
                            endIndex = origEnd,
                            endsParagraph = endsParagraph
                        )
                    )
                }
                break
            }
            val limit = offset + MAX_SINGLE_SENTENCE_CHARS
            var cut = snippet.lastIndexOf(',', limit)
            if (cut <= offset + 120) {
                cut = snippet.lastIndexOf(' ', limit)
            }
            if (cut <= offset + 80) {
                cut = limit
            } else if (snippet[cut] == ',') {
                cut += 1
            }
            val sub = snippet.substring(offset, cut).trim()
            if (sub.isNotEmpty()) {
                out.add(
                    SentenceSpan(
                        text = sub,
                        startIndex = origStart + offset,
                        endIndex = (origStart + cut).coerceAtMost(origEnd),
                        endsParagraph = false
                    )
                )
            }
            offset = cut
            while (offset < snippet.length && snippet[offset].isWhitespace()) offset++
        }
    }

    private fun groupIntoParts(sentences: List<SentenceSpan>): List<AudioPart> {
        if (sentences.isEmpty()) return emptyList()
        val parts = mutableListOf<AudioPart>()
        var currentSentences = mutableListOf<SentenceSpan>()
        var currentChars = 0

        for (sentence in sentences) {
            val targetChars = if (parts.isEmpty()) FIRST_PART_TARGET_CHARS else NORMAL_PART_TARGET_CHARS
            val wouldExceed = currentSentences.isNotEmpty() &&
                (currentChars + sentence.text.length + 1 > targetChars)

            if (wouldExceed) {
                parts.add(buildPart(parts.size, currentSentences))
                currentSentences = mutableListOf()
                currentChars = 0
            }

            currentSentences.add(sentence)
            currentChars += sentence.text.length + 1

            // Break cleanly on paragraph end if we already have substantial content
            if (sentence.endsParagraph && currentChars >= 140) {
                parts.add(buildPart(parts.size, currentSentences))
                currentSentences = mutableListOf()
                currentChars = 0
            }
        }

        if (currentSentences.isNotEmpty()) {
            parts.add(buildPart(parts.size, currentSentences))
        }

        return parts
    }

    private fun buildPart(index: Int, sentences: List<SentenceSpan>): AudioPart {
        val combinedText = sentences.joinToString(" ") { it.text }
        val start = sentences.first().startIndex
        val end = sentences.last().endIndex
        val endsParagraph = sentences.last().endsParagraph
        // Rough natural speech estimate: ~13.5 chars/sec (~74 ms/char) + pause
        val pauseMs = if (endsParagraph) 450L else 200L
        val estimatedMs = (combinedText.length * 74L).coerceAtLeast(1200L) + pauseMs
        return AudioPart(
            index = index,
            text = combinedText,
            sentences = sentences,
            startIndex = start,
            endIndex = end,
            endsWithParagraph = endsParagraph,
            estimatedDurationMs = estimatedMs
        )
    }
}
