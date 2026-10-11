package com.hoddesk.app.evaluation

/** Deterministic metrics used to compare retrieval and transcription changes. */
object EvaluationMetrics {
    fun cer(reference: String, hypothesis: String): Double =
        distance(reference.filterNot(Char::isWhitespace), hypothesis.filterNot(Char::isWhitespace)).toDouble() /
            reference.filterNot(Char::isWhitespace).length.coerceAtLeast(1)

    fun wer(reference: String, hypothesis: String): Double {
        val ref = reference.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val hyp = hypothesis.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return distance(ref, hyp).toDouble() / ref.size.coerceAtLeast(1)
    }

    fun recallAtK(relevant: Set<String>, ranked: List<String>, k: Int): Double {
        if (relevant.isEmpty()) return 1.0
        return ranked.take(k).count { it in relevant }.toDouble() / relevant.size
    }

    private fun distance(a: String, b: String): Int = distance(a.toList(), b.toList())

    private fun <T> distance(a: List<T>, b: List<T>): Int {
        var previous = IntArray(b.size + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.size + 1)
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = current
        }
        return previous[b.size]
    }
}

data class EvaluationCase(val id: String, val query: String, val relevantSourceIds: Set<String>)

data class EvaluationReport(
    val caseCount: Int,
    val recallAt10: Double,
    val cer: Double? = null,
    val wer: Double? = null,
    val fieldAccuracy: Double = 1.0,
    val citationCoverage: Double = 1.0,
    val generatedAt: Long = System.currentTimeMillis()
)
