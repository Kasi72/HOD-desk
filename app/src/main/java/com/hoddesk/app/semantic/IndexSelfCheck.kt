package com.hoddesk.app.semantic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fixed, on-device smoke corpus used before enabling semantic retrieval. It
 * deliberately covers English, Tamil and Tanglish so a tokenizer/model drift
 * cannot silently produce an apparently healthy index. */
object IndexSelfCheck {
    private val corpus = listOf(
        "attend the DME review meeting at 2 PM",
        "submit the revised resident duty roster before Friday",
        "Erode Collectorate கூட்டம் 19 September 2026",
        "நாளை காலை 10 மணிக்கு கூட்டத்தில் கலந்து கொள்ளவும்",
        "DME meeting-ku 2 mani-ku join pannunga",
        "ward rounds at 9 AM",
        "அறிக்கையை அனுப்பவும்",
        "follow up with stores about the quotation"
    )

    suspend fun run(embedder: EmbeddingGemmaService, expectedDimension: Int = 768): Result<Unit> = try {
        val vectors = corpus.map {
            currentCoroutineContext().ensureActive()
            embedder.embed(EmbeddingPromptRegistry.document("self-check", it))
        }
        require(vectors.all { EmbeddingIntegrity.validate(it, expectedDimension).valid }) { "self-check vector validation failed" }
        val repeat = embedder.embed(EmbeddingPromptRegistry.document("self-check", corpus.first()))
        require(EmbeddingIntegrity.validate(repeat, expectedDimension).valid)
        require(cosine(vectors.first(), repeat) > .98f) { "self-check is not deterministic" }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    } catch (missingRuntime: LinkageError) {
        Result.failure(missingRuntime)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var aa = 0f; var bb = 0f
        a.indices.forEach { i -> dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i] }
        return if (aa == 0f || bb == 0f) 0f else dot / (kotlin.math.sqrt(aa) * kotlin.math.sqrt(bb))
    }
}
