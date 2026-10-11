package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRetrieverTest {
    @Test fun exact_document_number_beats_unrelated_semantic_match() {
        val target = SourceEntity(id = 1, title = "Order 214", body = "DME review at Erode Collectorate", sourceType = "PDF")
        val other = SourceEntity(id = 2, title = "General circular", body = "Routine administrative information", sourceType = "PDF")
        val ranked = HybridRetriever.rank("order 214", listOf(target, other), emptyList())
        assertEquals(1L, ranked.first().source.id)
        assertTrue(ranked.first().reasons.any { it.contains("keyword") || it.contains("document") })
    }

    @Test fun tamil_suffix_and_character_overlap_are_supported() {
        val source = SourceEntity(id = 1, title = "கூட்டத்தில் பங்கேற்கவும்", body = "மாவட்ட கூட்டம் நாளை", sourceType = "WhatsApp")
        val ranked = HybridRetriever.rank("கூட்டம்", listOf(source), emptyList())
        assertEquals(1, ranked.size)
        assertTrue(ranked.first().keywordScore > 0f)
    }

    @Test fun short_character_overlap_recovers_ocr_typo() {
        val source = SourceEntity(id = 1, title = "Erode Collectorate", body = "Attend the DME review", sourceType = "Screenshot")
        val ranked = HybridRetriever.rank("Erode Collectrate", listOf(source), emptyList())
        assertEquals(1, ranked.size)
        assertTrue(ranked.first().keywordScore > 0f)
        assertTrue(ranked.first().reasons.any { it.contains("keyword") || it == "fuzzy text" })
    }
}
