package com.hoddesk.app.reasoning

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.semantic.SourceSemanticHit
import com.hoddesk.app.privacy.SensitivityClassifier

/** Builds a bounded, source-labelled evidence window for local reasoning. */
object EvidenceBundleBuilder {
    fun build(primary: SourceEntity, actions: List<String>, related: List<SourceSemanticHit>, conflictHints: List<String> = emptyList()): String {
        val out = StringBuilder()
        require(eligible(primary)) { "Clinical or inactive sources cannot enter ordinary reasoning" }
        appendSource(out, primary, actions, 6000)
        related.filter { eligible(it.source) && it.source.id != primary.id }.distinctBy { it.source.id }.take(3)
            .forEach { hit -> appendSource(out, hit.source, actions, 1400, hit.modality, hit.citation) }
        conflictHints.distinct().forEach { out.append("CONFLICT_HINT: ").append(it).append('\n') }
        return out.toString().take(12000)
    }

    private fun appendSource(out: StringBuilder, source: SourceEntity, actions: List<String>, maxChars: Int, modality: String? = null, locator: String? = null) {
        val body = originalText(source.body)
        // Preserve contiguous source order so a list's instruction and a meeting's
        // conflicting invitation remain together. Do not fabricate quotes by reordering lines.
        val best = actions.maxByOrNull { overlap(body, it) }
        val words = best?.let { Regex("[\\p{L}\\p{N}]{4,}").findAll(it).map { match -> match.value }.toList() }.orEmpty()
        val anchor = words.map { body.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = if (body.length <= maxChars) 0 else (anchor - maxChars / 3).coerceAtLeast(0)
        val text = body.substring(start, (start + maxChars).coerceAtMost(body.length))
            .replace("[SOURCE_ID:", "[SOURCE_LABEL:").replace("CONFLICT_HINT:", "SOURCE_CONFLICT_TEXT:")
        out.append("[SOURCE_ID:").append(source.id).append("]\nSOURCE_META: ")
            .append(source.sourceType).append(" · ").append(source.title.replace('\n', ' ').replace("[SOURCE_ID:", "[SOURCE_LABEL:"))
            .append(modality?.let { " [MODALITY:$it]" } ?: "")
            .append(locator?.let { " [EVIDENCE:$it]" } ?: "").append("\nSOURCE_TEXT:\n")
            .append(text).append("\n\n")
    }

    fun originalText(body: String): String = body.substringBefore("--- HOD Desk review cues")
        .substringBefore("--- HOD Desk action points").replace("\r\n", "\n").trim()

    private fun eligible(source: SourceEntity): Boolean = source.processingStatus !in setOf("deleted", "archived") &&
        source.sensitivity !in setOf("T3", "T4", "clinical", "special") &&
        SensitivityClassifier.classify(source.title, originalText(source.body)).tier.code !in setOf("T3", "T4")

    private fun overlap(a: String, b: String): Int {
        val left = Regex("[\\p{L}\\p{N}]{3,}").findAll(a.lowercase()).map { it.value }.toSet()
        val right = Regex("[\\p{L}\\p{N}]{3,}").findAll(b.lowercase()).map { it.value }.toSet()
        return left.intersect(right).size
    }
}
