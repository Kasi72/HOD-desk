package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity

/** Single source of truth for EmbeddingGemma asymmetric task prompts. */
object EmbeddingPromptRegistry {
    const val VERSION = 3
    fun document(header: String, body: String) = "title: ${header.take(240)} | text: ${body.take(12000)}"
    fun action(action: String, evidence: String) = "task: official action retrieval | action: ${action.take(120)} | evidence: ${evidence.take(2400)}"
    fun search(query: String) = "task: search result | query: $query"
    fun question(query: String) = "task: question answering | query: $query"
    fun fact(query: String) = "task: fact checking | query: $query"
    fun classify(query: String) = "task: classification | query: $query"
    fun cluster(query: String) = "task: clustering | query: $query"
    fun similarity(query: String) = "task: sentence similarity | query: $query"

    fun header(source: SourceEntity, locator: String? = null): String = buildList {
        add(source.sourceType)
        source.title.takeIf { it.isNotBlank() }?.let { add(it) }
        source.capturedAt.let { add(java.time.Instant.ofEpochMilli(it).toString().take(10)) }
        locator?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" | ")
}
