package com.hoddesk.app.evaluation

import android.content.Context
import org.json.JSONObject

data class EvaluationExample(
    val id: String,
    val inputType: String,
    val source: String,
    val expectedAction: String?,
    val expectedDate: String?,
    val expectedTime: String?,
    val expectedLocation: String?,
    val accepted: Boolean,
    val sourceQuote: String? = null,
)

data class EvaluationRun(
    val baseline: EvaluationReport,
    val current: EvaluationReport,
    val falseVerifiedRate: Double,
    val generatedAt: Long = System.currentTimeMillis(),
)

data class EvaluationGate(val passed: Boolean, val reasons: List<String>)

object EvaluationStore {
    private const val RUN_FILE = "evaluation-latest.json"
    private const val MAX_FALSE_VERIFIED = 0.02
    private const val MIN_RECALL = 0.90
    private const val MIN_FIELD_ACCURACY = 0.95
    private const val MIN_CITATION_COVERAGE = 0.98
    fun anonymize(source: String): String = source
        .replace(Regex("(?i)\\b[\\w.-]+@[\\w.-]+\\.[a-z]{2,}\\b"), "[EMAIL]")
        .replace(Regex("\\b\\+?\\d[\\d -]{7,}\\b"), "[PHONE]")

    fun report(examples: List<EvaluationExample>, predictions: List<EvaluationExample>): EvaluationReport {
        val byId = predictions.associateBy { it.id }
        val comparable = examples.mapNotNull { expected -> byId[expected.id]?.let { expected to it } }
        fun same(a: String?, b: String?): Boolean = when {
            a.isNullOrBlank() && b.isNullOrBlank() -> true
            a.isNullOrBlank() || b.isNullOrBlank() -> false
            else -> a.trim().replace(Regex("\\s+"), " ").equals(b.trim().replace(Regex("\\s+"), " "), ignoreCase = true)
        }
        val actionErrors = comparable.count { (expected, actual) -> !same(expected.expectedAction, actual.expectedAction) }
        val falseVerified = comparable.count { (expected, actual) -> !expected.accepted && actual.accepted }
        val fields = comparable.flatMap { (expected, actual) ->
            listOf(expected.expectedDate to actual.expectedDate, expected.expectedTime to actual.expectedTime, expected.expectedLocation to actual.expectedLocation)
                .filter { (e, _) -> !e.isNullOrBlank() }
        }
        val correctFields = fields.count { (expected, actual) -> same(expected, actual) }
        val cited = comparable.count { (_, actual) -> actual.accepted && !actual.sourceQuote.isNullOrBlank() }
        return EvaluationReport(
            caseCount = comparable.size,
            recallAt10 = if (comparable.isEmpty()) 1.0 else (comparable.size - actionErrors).toDouble() / comparable.size,
            cer = if (comparable.isEmpty()) null else falseVerified.toDouble() / comparable.size,
            wer = null,
            fieldAccuracy = if (fields.isEmpty()) 1.0 else correctFields.toDouble() / fields.size,
            citationCoverage = if (comparable.isEmpty()) 1.0 else cited.toDouble() / comparable.size,
        )
    }

    /** Release gate: a candidate must preserve recall and keep unsafe verified fields below 2%. */
    fun gate(baseline: EvaluationReport, current: EvaluationReport, currentFalseVerifiedRate: Double): EvaluationGate {
        val reasons = buildList {
            if (current.recallAt10 < MIN_RECALL) add("action recall below ${(MIN_RECALL * 100).toInt()}%")
            if (currentFalseVerifiedRate > MAX_FALSE_VERIFIED) add("false verified rate exceeds ${(MAX_FALSE_VERIFIED * 100).toInt()}%")
            if (current.recallAt10 + 0.001 < baseline.recallAt10) add("recall regressed against baseline")
            if (current.fieldAccuracy < MIN_FIELD_ACCURACY) add("date/time/location field accuracy below ${(MIN_FIELD_ACCURACY * 100).toInt()}%")
            if (current.citationCoverage < MIN_CITATION_COVERAGE) add("accepted actions are missing source citations")
        }
        return EvaluationGate(reasons.isEmpty(), reasons)
    }

    fun saveLatest(context: Context, run: EvaluationRun) {
        val root = JSONObject()
        fun report(value: EvaluationReport) = JSONObject().put("cases", value.caseCount).put("recall", value.recallAt10).put("cer", value.cer).put("wer", value.wer).put("fieldAccuracy", value.fieldAccuracy).put("citationCoverage", value.citationCoverage)
        root.put("baseline", report(run.baseline)).put("current", report(run.current)).put("falseVerified", run.falseVerifiedRate).put("generatedAt", run.generatedAt)
        context.getFileStreamPath(RUN_FILE).writeText(root.toString())
    }

    fun latest(context: Context): EvaluationRun? = runCatching {
        val root = JSONObject(context.openFileInput(RUN_FILE).bufferedReader().use { it.readText() })
        fun report(value: JSONObject) = EvaluationReport(value.getInt("cases"), value.getDouble("recall"), value.optDouble("cer").takeUnless { it.isNaN() }, value.optDouble("wer").takeUnless { it.isNaN() }, value.optDouble("fieldAccuracy", 1.0), value.optDouble("citationCoverage", 1.0))
        EvaluationRun(report(root.getJSONObject("baseline")), report(root.getJSONObject("current")), root.getDouble("falseVerified"), root.getLong("generatedAt"))
    }.getOrNull()
}
