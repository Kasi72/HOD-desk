package com.hoddesk.app.semantic

import java.security.MessageDigest
import kotlin.math.sqrt

data class VectorValidation(val valid: Boolean, val reason: String? = null)

object EmbeddingIntegrity {
    fun fingerprint(modelSha256: String, tokenizerSha256: String, dim: Int, chunkerVersion: Int = 1): String =
        sha256("$modelSha256|$tokenizerSha256|prompt=${EmbeddingPromptRegistry.VERSION}|dim=$dim|chunker=$chunkerVersion")

    fun validate(vector: FloatArray, expectedDimension: Int = 768): VectorValidation {
        if (vector.size != expectedDimension) return VectorValidation(false, "dimension=${vector.size}, expected=$expectedDimension")
        if (vector.any { !it.isFinite() }) return VectorValidation(false, "nan_or_inf")
        var norm = 0.0
        var mean = 0.0
        vector.forEach { norm += it * it; mean += it }
        norm = sqrt(norm)
        if (norm == 0.0) return VectorValidation(false, "zero_norm")
        if (kotlin.math.abs(norm - 1.0) > 1e-3) return VectorValidation(false, "not_normalized")
        mean /= vector.size
        val variance = vector.sumOf { ((it - mean) * (it - mean)).toDouble() } / vector.size
        if (variance < 1e-10) return VectorValidation(false, "collapsed_vector")
        return VectorValidation(true)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
