package com.hoddesk.app.evaluation

data class EvaluationExample(
    val id: String,
    val inputType: String,
    val source: String,
    val expectedAction: String?,
    val expectedDate: String?,
    val expectedTime: String?,
    val expectedLocation: String?,
    val accepted: Boolean,
)

data class EvaluationRun(
    val baseline: EvaluationReport,
    val current: EvaluationReport,
    val falseVerifiedRate: Double,
    val generatedAt: Long = System.currentTimeMillis(),
)

data class EvaluationGate(val passed: Boolean, val reasons: List<String>)

object EvaluationStore {
    private const val MAX_FALSE_VERIFIED = 0.02
    private const val MIN_RECALL = 0.90
    fun anonymize(source: String): String = source
        .replace(Regex("(?i)\\b[\\w.-]+@[\\w.-]+\\.[a-z]{2,}\\b"), "[EMAIL]")
        .replace(Regex("\\b\\+?\\d[\\d -]{7,}\\b"), "[PHONE]")

    fun report(examples: List<EvaluationExample>, predictions: List<EvaluationExample>): EvaluationReport {
        val byId = predictions.associateBy { it.id }
        val comparable = examples.mapNotNull { expected -> byId[expected.id]?.let { expected to it } }
        val actionErrors = comparable.count { (expected, actual) -> expected.expectedAction != actual.expectedAction }
        val falseVerified = comparable.count { (expected, actual) -> !expected.accepted && actual.accepted }
        return EvaluationReport(
            caseCount = comparable.size,
            recallAt10 = if (comparable.isEmpty()) 1.0 else (comparable.size - actionErrors).toDouble() / comparable.size,
            cer = if (comparable.isEmpty()) null else falseVerified.toDouble() / comparable.size,
            wer = null,
        )
    }

    /** Release gate: a candidate must preserve recall and keep unsafe verified fields below 2%. */
    fun gate(baseline: EvaluationReport, current: EvaluationReport, currentFalseVerifiedRate: Double): EvaluationGate {
        val reasons = buildList {
            if (current.recallAt10 < MIN_RECALL) add("action recall below ${(MIN_RECALL * 100).toInt()}%")
            if (currentFalseVerifiedRate > MAX_FALSE_VERIFIED) add("false verified rate exceeds ${(MAX_FALSE_VERIFIED * 100).toInt()}%")
            if (current.recallAt10 + 0.001 < baseline.recallAt10) add("recall regressed against baseline")
        }
        return EvaluationGate(reasons.isEmpty(), reasons)
    }
}
