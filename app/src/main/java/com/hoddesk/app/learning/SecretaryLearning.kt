package com.hoddesk.app.learning

import com.hoddesk.app.data.LearningCorrectionEntity
import com.hoddesk.app.semantic.HybridMatch

data class LearningProfile(
    val phraseWeights: Map<String, Float>,
    val fieldCorrectionCounts: Map<String, Int>,
    val sampleCount: Int,
)

object SecretaryLearning {
    fun profile(corrections: List<LearningCorrectionEntity>): LearningProfile {
        val usable = corrections.filter { !it.before.isNullOrBlank() || !it.after.isNullOrBlank() }
        val phrases = usable.mapNotNull { it.after?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().mapValues { (_, count) -> (count / 5f).coerceAtMost(.25f) }
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
