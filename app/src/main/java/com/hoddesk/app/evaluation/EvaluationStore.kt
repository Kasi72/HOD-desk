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

    fun saveLatest(context: Context, run: EvaluationRun) {
        val root = JSONObject()
        fun report(value: EvaluationReport) = JSONObject().put("cases", value.caseCount).put("recall", value.recallAt10).put("cer", value.cer).put("wer", value.wer)
        root.put("baseline", report(run.baseline)).put("current", report(run.current)).put("falseVerified", run.falseVerifiedRate).put("generatedAt", run.generatedAt)
        context.getFileStreamPath(RUN_FILE).writeText(root.toString())
    }

    fun latest(context: Context): EvaluationRun? = runCatching {
        val root = JSONObject(context.openFileInput(RUN_FILE).bufferedReader().use { it.readText() })
        fun report(value: JSONObject) = EvaluationReport(value.getInt("cases"), value.getDouble("recall"), value.optDouble("cer").takeUnless { it.isNaN() }, value.optDouble("wer").takeUnless { it.isNaN() })
        EvaluationRun(report(root.getJSONObject("baseline")), report(root.getJSONObject("current")), root.getDouble("falseVerified"), root.getLong("generatedAt"))
    }.getOrNull()
}
