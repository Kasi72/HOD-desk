package com.hoddesk.app.reasoning

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.semantic.SourceSemanticHit

/** Builds a bounded, source-labelled evidence window for local reasoning. */
object EvidenceBundleBuilder {
    fun build(primary: SourceEntity, actions: List<String>, related: List<SourceSemanticHit>, conflictHints: List<String> = emptyList()): String {
        val out = StringBuilder()
        appendSource(out, primary, actions, 2200)
        related.take(3).forEach { hit -> appendSource(out, hit.source, actions, 1200) }
        conflictHints.distinct().forEach { out.append("CONFLICT_HINT: ").append(it).append('\n') }
        return out.toString().take(9000)
    }

    private fun appendSource(out: StringBuilder, source: SourceEntity, actions: List<String>, maxChars: Int) {
        val body = source.body.replace("\r\n", "\n").trim()
        val lines = body.split(Regex("(?<=[.!?。！？])\\s+|\\n+"))
        val selected = lines.filter { line -> actions.any { action -> overlap(line, action) >= 1 } }
        val chunks = (selected + lines).distinct().filter { it.isNotBlank() }.take(8)
        val text = chunks.joinToString(" ").take(maxChars)
        out.append("[SOURCE_ID:").append(source.id).append("] ")
            .append(source.sourceType).append(" · ").append(source.title).append('\n')
            .append(text).append("\n\n")
    }

    private fun overlap(a: String, b: String): Int {
        val left = Regex("[\\p{L}\\p{N}]{3,}").findAll(a.lowercase()).map { it.value }.toSet()
        val right = Regex("[\\p{L}\\p{N}]{3,}").findAll(b.lowercase()).map { it.value }.toSet()
        return left.intersect(right).size
    }
}
