package com.hoddesk.app.reasoning

import java.util.Locale

/** A conservative, explainable dependency graph for action candidates. */
data class ActionDependency(val prerequisite: String, val dependent: String, val reason: String)

object ActionDependencyAnalyzer {
    fun analyze(actions: List<String>): List<ActionDependency> {
        if (actions.size < 2) return emptyList()
        val result = mutableListOf<ActionDependency>()
        actions.distinct().forEach { left ->
            actions.distinct().filter { it != left }.forEach { right ->
                val leftPrepares = preparation.containsMatchIn(left)
                val rightConsumes = execution.containsMatchIn(right)
                val shared = subjects(left).intersect(subjects(right)).filterNot { it in genericSubjects }
                // A pair of verbs alone cannot prove that two tasks are linked.
                // Keep a proposed edge only when both actions identify the same
                // concrete work item, regardless of their source sentence order.
                if (leftPrepares && rightConsumes && shared.isNotEmpty())
                    result += ActionDependency(left, right, "Possible prerequisite: shared subject ${shared.sorted().joinToString(", ")}")
            }
        }
        return result.distinctBy { it.prerequisite to it.dependent }
    }

    private val preparation = Regex("(?i)^(?:please |kindly )?(?:prepare|obtain|collect|instruct|confirm|send|compile|arrange)\\b|^தயார்|^பெற")
    private val execution = Regex("(?i)^(?:please |kindly )?(?:attend|join|submit|review|conduct|discuss|complete|follow up|participate)\\b|^கலந்து|^சமர்ப்பிக்க")
    private val ignoredWords = setOf("the", "a", "an", "to", "of", "for", "with", "on", "in", "at", "by", "and", "please", "kindly", "prepare", "obtain", "collect", "instruct", "confirm", "send", "compile", "arrange", "attend", "join", "submit", "review", "conduct", "discuss", "complete", "follow", "up", "participate", "listed", "scheduled", "officials")
    private val genericSubjects = setOf("meeting", "report", "review", "work", "task", "document", "proposal", "conference", "session")
    private fun subjects(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase(Locale.ROOT))
        .map { it.value }.filter { it.length >= 3 && it !in ignoredWords }.toSet()
}
