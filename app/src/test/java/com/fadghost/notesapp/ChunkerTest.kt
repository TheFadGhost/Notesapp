package com.fadghost.notesapp

import com.fadghost.notesapp.data.ai.text.Chunker
import com.fadghost.notesapp.data.ai.text.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    @Test fun shortTextIsASingleChunk() {
        val text = "Just a short note."
        assertEquals(listOf(text), Chunker.chunk(text, maxTokens = 1000))
    }

    @Test fun blankTextYieldsNoChunks() {
        assertTrue(Chunker.chunk("   ", maxTokens = 100).isEmpty())
    }

    @Test fun longTextSplitsIntoMultipleChunksUnderBudget() {
        val para = "Sentence number %d in a fairly long paragraph that keeps going. "
        val text = (1..200).joinToString("\n\n") { para.format(it) }
        val maxTokens = 200
        val chunks = Chunker.chunk(text, maxTokens)
        assertTrue("expected multiple chunks", chunks.size > 1)
        val maxChars = (maxTokens * 3.5).toInt()
        chunks.forEach { assertTrue("chunk too big: ${it.length}", it.length <= maxChars + 200) }
    }

    @Test fun everyOriginalWordSurvivesChunking() {
        val text = (1..50).joinToString("\n\n") { "Paragraph $it has some unique-word-$it inside it." }
        val chunks = Chunker.chunk(text, maxTokens = 60)
        val joined = chunks.joinToString(" ")
        for (i in 1..50) assertTrue("missing unique-word-$i", joined.contains("unique-word-$i"))
    }

    @Test fun estimatorIsPessimistic() {
        // ~3.5 chars/token → 70 chars estimates 20 tokens.
        assertEquals(20, TokenEstimator.estimate("a".repeat(70)))
    }

    @Test fun exceedsBudgetTrueForLargeInput() {
        assertTrue(TokenEstimator.exceedsBudget("x".repeat(100_000), contextTokens = 8000))
    }
}
