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

object EvaluationStore {
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
}
