package com.hoddesk.app.evaluation

import com.hoddesk.app.imports.ActionPointExtractor

/** Exercises extraction from synthetic text; does not claim to test actual OCR/audio/model inference. */
object EvaluationRunner {
    fun deterministic(): EvaluationRun {
        val examples = EvaluationCorpus.baseline
        val extractor = ActionPointExtractor()
        val predictions = examples.map { sample ->
            val action = extractor.extract(sample.source).firstOrNull()
            sample.copy(expectedAction = action?.action, expectedDate = action?.dateText, expectedTime = action?.timeText,
                expectedLocation = action?.locationText, accepted = action != null, sourceQuote = action?.evidence)
        }
        val reference = EvaluationStore.report(examples, examples.map { it.copy(sourceQuote = if (it.accepted) it.source else null) })
        val current = EvaluationStore.report(examples, predictions)
        return EvaluationRun(reference, current, current.falseVerifiedRate, scope = "deterministic-text")
    }
}
