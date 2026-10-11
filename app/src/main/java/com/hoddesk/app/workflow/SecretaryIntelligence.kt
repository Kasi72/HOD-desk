package com.hoddesk.app.workflow

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Deterministic secretary layer around EmbeddingGemma retrieval and local reasoning. */
object SecretaryIntelligence {
    data class Brief(val headline: String, val detail: String, val urgent: Int, val waiting: Int, val conflicts: Int)
    data class Conflict(val first: TaskEntity, val second: TaskEntity, val reason: String)
    data class Command(val kind: Kind, val query: String)
    enum class Kind { SEARCH, PLAN, FOLLOW_UP, SHOW_WAITING, SHOW_MEETINGS, UNKNOWN }

    fun brief(sources: List<SourceEntity>, tasks: List<TaskEntity>, today: LocalDate = LocalDate.now()): Brief {
        val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val urgent = tasks.count { it.status != "completed" && it.dueAt != null && it.dueAt < end } +
            sources.count { !it.reviewed && it.triageBucket == "ACT" }
        val waiting = tasks.count { it.status != "completed" && !it.assignee.isNullOrBlank() }
        val conflicts = conflicts(tasks).size
        val headline = when {
            conflicts > 0 -> "${conflicts} schedule conflict${if (conflicts == 1) "" else "s"} needs review"
            urgent > 0 -> "$urgent item${if (urgent == 1) "" else "s"} need your attention today"
            waiting > 0 -> "You are waiting on $waiting delegated item${if (waiting == 1) "" else "s"}"
            else -> "Your desk is clear"
        }
        val detail = buildList {
            if (urgent > 0) add("$urgent urgent or due")
            if (waiting > 0) add("$waiting waiting on others")
            if (conflicts > 0) add("$conflicts calendar conflict${if (conflicts == 1) "" else "s"}")
            if (isEmpty()) add("New imports will be triaged automatically")
        }.joinToString(" · ")
        return Brief(headline, detail, urgent, waiting, conflicts)
    }

    fun conflicts(tasks: List<TaskEntity>): List<Conflict> = tasks.filter { it.status != "completed" && it.dueAt != null }
        .sortedBy { it.dueAt }.zipWithNext().mapNotNull { (a, b) ->
            if (a.dueAt == null || b.dueAt == null) null
            else if (kotlin.math.abs(a.dueAt - b.dueAt) < 60 * 60 * 1000L) Conflict(a, b, "Due times are within one hour") else null
        }

    fun command(text: String): Command {
        val q = text.trim()
        val lower = q.lowercase()
        return when {
            lower.startsWith("what am i waiting") || lower.contains("waiting on") -> Command(Kind.SHOW_WAITING, q)
            lower.contains("what do i have") || lower.contains("meetings") -> Command(Kind.SHOW_MEETINGS, q)
            lower.startsWith("plan") || lower.contains("schedule my day") -> Command(Kind.PLAN, q)
            lower.contains("follow up") || lower.contains("follow-up") -> Command(Kind.FOLLOW_UP, q)
            q.isNotBlank() -> Command(Kind.SEARCH, q)
            else -> Command(Kind.UNKNOWN, q)
        }
    }

    fun followUpCandidates(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): List<TaskEntity> =
        tasks.filter { it.status != "completed" && !it.assignee.isNullOrBlank() && now - it.createdAt >= 2 * 24 * 60 * 60 * 1000L }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }
}
