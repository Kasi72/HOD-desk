package com.hoddesk.app.workflow

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import java.time.LocalDate
import java.time.ZoneId

/** Deterministic secretary layer around EmbeddingGemma retrieval and local reasoning. */
object SecretaryIntelligence {
    data class Brief(
        val headline: String,
        val detail: String,
        val urgent: Int,
        val waiting: Int,
        val conflicts: Int,
        val toReview: Int = 0,
        val followUps: Int = 0,
        val blocked: Int = 0,
        val dueSoon: Int = 0,
    )
    data class Conflict(val first: TaskEntity, val second: TaskEntity, val reason: String)
    data class Command(val kind: Kind, val query: String)
    enum class Kind { SEARCH, PLAN, FOLLOW_UP, SHOW_WAITING, SHOW_MEETINGS, UNKNOWN }

    private fun active(task: TaskEntity): Boolean =
        task.status != "completed" && task.status != "deleted" && task.status != "archived"

    fun brief(sources: List<SourceEntity>, tasks: List<TaskEntity>, today: LocalDate = LocalDate.now()): Brief {
        val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val soon = end + 48 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val visibleSources = sources.filter { it.processingStatus !in setOf("deleted", "archived") && it.sensitivity !in setOf("T3", "T4", "T3_CLINICAL", "T4_SPECIAL") && it.body.isNotBlank() }
        val visibleIds = visibleSources.map { it.id }.toSet()
        val activeTasks = tasks.filter { active(it) && (it.sourceId == null || it.sourceId in visibleIds) }
        val toReview = visibleSources.count { !it.reviewed }
        val dueSoon = activeTasks.count { it.dueAt != null && it.dueAt in now..soon }
        val followUps = activeTasks.count { !it.assignee.isNullOrBlank() && now - it.createdAt >= 2 * 24 * 60 * 60 * 1000L }
        val blocked = activeTasks.count { !it.blocker.isNullOrBlank() || !it.clarification.isNullOrBlank() }
        // Keep the brief aligned with Today/Review: only actionable, non-clinical
        // sources count. Clinical and explicitly dismissed items must never inflate
        // the user's attention count.
        val urgent = activeTasks.count { it.dueAt != null && it.dueAt < end } + toReview
        val waiting = activeTasks.count { !it.assignee.isNullOrBlank() }
        val conflicts = conflicts(tasks).size
        val headline = when {
            conflicts > 0 -> "${conflicts} schedule conflict${if (conflicts == 1) "" else "s"} needs review"
            blocked > 0 -> "${blocked} blocked item${if (blocked == 1) "" else "s"} need attention"
            urgent > 0 -> "$urgent item${if (urgent == 1) "" else "s"} need your attention today"
            waiting > 0 -> "You are waiting on $waiting delegated item${if (waiting == 1) "" else "s"}"
            else -> "Your desk is clear"
        }
        val detail = buildList {
            if (urgent > 0) add("$urgent urgent or due")
            if (toReview > 0) add("$toReview to review")
            if (dueSoon > 0) add("$dueSoon due within 48 hours")
            if (waiting > 0) add("$waiting waiting on others")
            if (followUps > 0) add("$followUps follow-up${if (followUps == 1) "" else "s"} due")
            if (blocked > 0) add("$blocked blocked")
            if (conflicts > 0) add("$conflicts calendar conflict${if (conflicts == 1) "" else "s"}")
            if (isEmpty()) add("New imports will be triaged automatically")
        }.joinToString(" · ")
        return Brief(headline, detail, urgent, waiting, conflicts, toReview, followUps, blocked, dueSoon)
    }

    fun conflicts(tasks: List<TaskEntity>): List<Conflict> = tasks.filter { active(it) && it.dueAt != null }
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
        tasks.filter { active(it) && !it.assignee.isNullOrBlank() && now - it.createdAt >= 2 * 24 * 60 * 60 * 1000L }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }

    /** Cited executive views used by the brief card, notifications and review UI. */
    enum class BriefKind { MORNING, MEETING_PREPARATION, MIDDAY_RISK, EVENING_CLOSE }
    data class BriefCitation(val sourceId: Long?, val taskId: Long?, val quote: String, val locator: String, val authority: String = "Unspecified", val unresolved: Boolean = false)
    data class ExecutiveBrief(val kind: BriefKind, val title: String, val summary: String, val citations: List<BriefCitation>, val requiresReview: Boolean)

    fun executiveBrief(kind: BriefKind, sources: List<SourceEntity>, tasks: List<TaskEntity>, events: List<com.hoddesk.app.data.EventEntity> = emptyList(), commitments: List<com.hoddesk.app.data.CommitmentEntity> = emptyList(), now: Long = System.currentTimeMillis()): ExecutiveBrief {
        val visible = sources.filter { it.processingStatus !in setOf("deleted", "archived") && it.sensitivity !in setOf("T3", "T4", "T3_CLINICAL", "T4_SPECIAL") && it.body.isNotBlank() }
        val visibleIds = visible.map { it.id }.toSet()
        val activeTasks = tasks.filter { active(it) && (it.sourceId == null || it.sourceId in visibleIds) }
        val activeCommitments = commitments.filter { it.status == "open" && (it.sourceId == null || it.sourceId in visibleIds) && it.evidence.isNotBlank() }
        fun sourceAuthority(sourceId: Long?): String = visible.firstOrNull { it.id == sourceId }?.let { when { it.priorityScore >= 80 -> "High authority"; it.priorityScore >= 40 -> "Official"; else -> "Routine" } } ?: "User-confirmed"
        fun taskCitation(task: TaskEntity, quote: String, locator: String, unresolved: Boolean = false) = BriefCitation(task.sourceId, task.id, quote, locator, sourceAuthority(task.sourceId), unresolved)
        val citations = mutableListOf<BriefCitation>()
        val title: String
        val summary: String
        when (kind) {
            BriefKind.MORNING -> {
                title = "Morning brief"
                activeTasks.filter { it.dueAt != null && it.dueAt <= now + 24 * 60 * 60 * 1000L }.take(8).forEach { citations += taskCitation(it, it.title, "task", it.blocker != null || it.clarification != null) }
                visible.filter { !it.reviewed }.take(8).forEach { citations += BriefCitation(it.id, null, it.body.trim().take(240), "${it.sourceType} · ${it.title}", sourceAuthority(it.id), unresolved = true) }
                val authorityCount = citations.count { it.authority == "High authority" || it.authority == "Official" }
                summary = "${events.count { it.startsAt >= now && it.startsAt < now + 24 * 60 * 60 * 1000L }} meetings · ${citations.size} cited attention items · $authorityCount official"
            }
            BriefKind.MEETING_PREPARATION -> {
                title = "Meeting preparation"
                events.filter { it.startsAt >= now }.sortedBy { it.startsAt }.take(3).forEach { event ->
                    citations += BriefCitation(null, event.taskId, event.title, "calendar")
                    activeTasks.filter { it.id == event.taskId || it.project.equals(event.title, true) }.take(5).forEach { task -> citations += taskCitation(task, task.title, "preparation") }
                }
                summary = if (citations.isEmpty()) "No upcoming meetings need preparation." else "${citations.count { it.locator == "calendar" }} meeting${if (citations.count { it.locator == "calendar" } == 1) "" else "s"} with cited preparation"
            }
            BriefKind.MIDDAY_RISK -> {
                title = "Midday risk brief"
                activeTasks.filter { !it.blocker.isNullOrBlank() || !it.clarification.isNullOrBlank() || (it.dueAt != null && it.dueAt < now) }.take(10).forEach { task -> citations += taskCitation(task, task.blocker ?: task.clarification ?: task.title, "risk", unresolved = true) }
                activeCommitments.filter { it.dueAt != null && it.dueAt < now }.take(10).forEach { item -> citations += BriefCitation(item.sourceId, item.taskId, item.summary, "commitment", sourceAuthority(item.sourceId), unresolved = true) }
                summary = "${citations.size} unresolved or overdue risk${if (citations.size == 1) "" else "s"}"
            }
            BriefKind.EVENING_CLOSE -> {
                title = "Evening close"
                activeTasks.filter { it.dueAt != null && it.dueAt < now }.take(10).forEach { task -> citations += taskCitation(task, task.title, "unfinished task", unresolved = true) }
                activeCommitments.take(10).forEach { item -> citations += BriefCitation(item.sourceId, item.taskId, item.summary, if (item.direction == "OWED_TO_ME") "waiting on others" else "owed by me", sourceAuthority(item.sourceId), unresolved = true) }
                summary = "${citations.size} item${if (citations.size == 1) "" else "s"} remains open for review"
            }
        }
        return ExecutiveBrief(kind, title, summary, citations.distinctBy { Triple(it.sourceId, it.taskId, it.quote) }, requiresReview = true)
    }
}
