package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.imports.ActionPointExtractor

/**
 * Side-by-side "latest evidence" view for a newer source that may revise the one under review.
 * The newer source's fields come from the same on-device extractor, matched to this action by
 * wording; nothing is changed until the reviewer chooses to use the newer values and confirms.
 */
object SourceComparison {
    data class Row(val field: String, val label: String, val current: String?, val newer: String?) {
        val differs: Boolean get() = !newer.isNullOrBlank() && normalise(current) != normalise(newer)
    }

    data class Comparison(val newerSource: SourceEntity, val rows: List<Row>, val cancels: Boolean, val matchedAction: String?) {
        val changedRows: List<Row> get() = rows.filter { it.differs }
        val hasChanges: Boolean get() = changedRows.isNotEmpty() || cancels
    }

    private val cancellation = Regex("(?iu)\\b(cancel(?:led|ed)?|withdrawn?|postponed|deferred|stands? cancelled|not be held|rescheduled)\\b|ரத்து|ஒத்திவைக்கப்")
    private val stop = setOf("the", "a", "an", "to", "of", "and", "for", "on", "in", "at", "by", "is", "are", "be", "with", "kindly", "please", "all")

    fun compare(current: ActionPointEntity, newer: SourceEntity, extractor: ActionPointExtractor = ActionPointExtractor()): Comparison {
        val candidates = extractor.extract(newer.body)
        val target = words(current.action)
        val best = candidates.maxByOrNull { similarity(target, words(it.action)) }
            ?.takeIf { similarity(target, words(it.action)) >= 0.2 }
        // With no matching action, compare document-level values only when the newer source states one.
        val newerDate = best?.dateText ?: TemporalEvidence.uniqueDates(newer.body).singleOrNull()?.text
        val newerTime = best?.timeText ?: TemporalEvidence.uniqueTimes(newer.body).singleOrNull()?.text
        val rows = listOf(
            Row("date", "Date", current.dateText, newerDate),
            Row("time", "Time", current.timeText, newerTime),
            Row("location", "Where", current.locationText, best?.locationText),
            Row("owner", "Owner", current.ownerText, best?.ownerText),
            Row("people", "People", current.peopleText, best?.peopleText),
        ).filter { !it.current.isNullOrBlank() || !it.newer.isNullOrBlank() }
        return Comparison(newer, rows, cancellation.containsMatchIn("${newer.title} ${newer.body}"), best?.action)
    }

    /** The edit-field values after taking every newer value that differs; others stay as they are. */
    fun applyNewer(comparison: Comparison): Map<String, String> =
        comparison.changedRows.associate { it.field to it.newer!!.trim() }

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in stop }.toSet()
    private fun similarity(a: Set<String>, b: Set<String>): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
    private fun normalise(value: String?) = value.orEmpty().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
