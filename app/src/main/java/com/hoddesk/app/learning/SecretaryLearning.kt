package com.hoddesk.app.learning

import com.hoddesk.app.data.LearningCorrectionEntity
import com.hoddesk.app.semantic.HybridMatch
import kotlin.math.exp

data class LearningProfile(
    val phraseWeights: Map<String, Float>,
    val fieldCorrectionCounts: Map<String, Int>,
    val sampleCount: Int,
)

object SecretaryLearning {
    private val sensitive = Regex("(?i)(patient|mrn|uhid|病例|நோயாளி|\\b\\d{10}\\b|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,})")
    fun profile(corrections: List<LearningCorrectionEntity>, now: Long = System.currentTimeMillis()): LearningProfile {
        val usable = corrections.filter { correction ->
            (!correction.before.isNullOrBlank() || !correction.after.isNullOrBlank()) &&
                listOfNotNull(correction.before, correction.after).none { sensitive.containsMatchIn(it) }
        }
        val grouped = usable.mapNotNull { correction -> correction.after?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let { it to correction } }.groupBy { it.first }
        val phrases = grouped.filterValues { it.size >= 2 }.mapValues { (_, values) ->
            val weighted = values.sumOf { (_, correction) ->
                val ageDays = ((now - correction.createdAt).coerceAtLeast(0L) / 86_400_000L).toDouble()
                exp(-ageDays / 180.0)
            }
            (weighted / 5.0).toFloat().coerceAtMost(.25f)
        }
        return LearningProfile(phrases, usable.groupingBy { it.field }.eachCount(), usable.size)
    }

    fun rerank(candidate: HybridMatch, profile: LearningProfile): HybridMatch {
        if (profile.sampleCount < 2) return candidate
        val sourceText = candidate.source.title + " " + candidate.source.body
        val text = sourceText.lowercase()
        val boost = profile.phraseWeights.filterKeys { it.length >= 3 && text.contains(it) }.values.sum().coerceAtMost(.25f)
        if (boost <= 0f) return candidate
        return candidate.copy(score = (candidate.score + boost).coerceIn(0f, 1f), reasons = candidate.reasons + "learned local preference")
    }
}
