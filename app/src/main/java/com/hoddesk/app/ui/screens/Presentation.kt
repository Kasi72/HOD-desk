package com.hoddesk.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.ui.theme.Hod
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

data class SourceLook(val icon: ImageVector, val color: Color, val label: String)

@Composable
fun sourceLook(type: String): SourceLook {
    val c = Hod.colors
    val t = type.lowercase()
    return when {
        "whatsapp" in t || "message" in t -> SourceLook(Icons.Rounded.Chat, c.green, "WhatsApp")
        "call" in t || "audio" in t || "transcript" in t -> SourceLook(Icons.Rounded.Call, c.orange, "Call")
        "voice" in t -> SourceLook(Icons.Rounded.Mic, c.tint, "Voice note")
        "camera" in t -> SourceLook(Icons.Rounded.PhotoCamera, c.gray, "Photo")
        "screenshot" in t || "image" in t -> SourceLook(Icons.Rounded.Smartphone, c.indigo, "Screenshot")
        "gmail" in t || "document" in t || "pdf" in t || "word" in t || "official" in t -> SourceLook(Icons.Rounded.Description, c.tint, "Letter")
        else -> SourceLook(Icons.Rounded.EditNote, c.yellow, "Note")
    }
}

/** Scope key used by the Library segmented control. */
fun sourceScope(type: String): String {
    val t = type.lowercase()
    return when {
        "whatsapp" in t || "message" in t -> "WhatsApp"
        "call" in t || "audio" in t || "transcript" in t || "voice" in t -> "Calls"
        "gmail" in t || "document" in t || "pdf" in t || "word" in t || "official" in t -> "Letters"
        else -> "Other"
    }
}

fun bucketLabel(bucket: String): String = when (bucket.uppercase()) {
    "ACT" -> "Act"
    "DELEGATE" -> "Delegate"
    "WAITING" -> "Waiting"
    "NOT_MINE" -> "Not mine"
    else -> "FYI"
}

private val zone: ZoneId get() = ZoneId.systemDefault()

fun epochToDate(epoch: Long): LocalDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()

fun timeLabel(epoch: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epoch))

fun dueLabel(dueAt: Long?, now: LocalDate = LocalDate.now()): String {
    if (dueAt == null) return "No date"
    val day = epochToDate(dueAt)
    val time = timeLabel(dueAt)
    return when {
        day == now -> "Today, $time"
        day == now.plusDays(1) -> "Tomorrow, $time"
        day == now.minusDays(1) -> "Yesterday, $time"
        day.year == now.year -> "${day.format(DateTimeFormatter.ofPattern("EEE d MMM"))}, $time"
        else -> "${day.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}, $time"
    }
}

enum class TaskState { Open, Delegated, Scheduled, Clarification, Blocked, Overdue, Done }

fun taskState(task: TaskEntity, now: Long = System.currentTimeMillis()): TaskState = when {
    task.status == "completed" -> TaskState.Done
    !task.blocker.isNullOrBlank() -> TaskState.Blocked
    task.status == "needs_clarification" -> TaskState.Clarification
    task.dueAt != null && task.dueAt < now -> TaskState.Overdue
    !task.assignee.isNullOrBlank() -> TaskState.Delegated
    task.dueAt != null -> TaskState.Scheduled
    else -> TaskState.Open
}

data class PillLook(val text: String, val background: Color, val foreground: Color)

@Composable
fun taskPill(state: TaskState): PillLook {
    val c = Hod.colors
    return when (state) {
        TaskState.Open -> PillLook("Open", c.fill, c.label)
        TaskState.Delegated -> PillLook("Delegated", c.blue.copy(alpha = 0.12f), c.blueText)
        TaskState.Scheduled -> PillLook("Scheduled", c.blue.copy(alpha = 0.12f), c.blueText)
        TaskState.Clarification -> PillLook("Needs clarification", c.orange.copy(alpha = 0.14f), c.orangeText)
        TaskState.Blocked -> PillLook("Blocked", c.fill, c.label)
        TaskState.Overdue -> PillLook("Overdue", c.red.copy(alpha = 0.12f), c.redText)
        TaskState.Done -> PillLook("Done", c.green.copy(alpha = 0.14f), c.greenText)
    }
}

/** Lines the extractor appends to a source body ("--- HOD Desk review cues ---" etc.) are machinery, not evidence. */
fun cleanEvidence(body: String): String = body.lineSequence()
    .takeWhile { !it.startsWith("--- HOD Desk review cues") }
    .filterNot { line -> REVIEW_CUE_PREFIXES.any { line.startsWith(it) } }
    .joinToString("\n").trim()

private val REVIEW_CUE_PREFIXES = listOf("Action candidate:", "Dates/times to verify:", "Clock times to verify:", "Locations to verify:", "People/officials to verify:")

fun initials(name: String): String = name.split(' ', '.').filter { it.isNotBlank() && it.first().isLetter() }
    .let { parts -> (parts.firstOrNull()?.take(1).orEmpty() + (parts.drop(1).lastOrNull()?.take(1).orEmpty())).uppercase() }
    .ifBlank { "?" }

fun SourceEntity.isClinical(): Boolean = sensitivity in setOf("T3", "T4", "T3_CLINICAL", "T4_SPECIAL", "clinical", "special") ||
    com.hoddesk.app.privacy.SensitivityClassifier.classify(title, com.hoddesk.app.reasoning.EvidenceBundleBuilder.originalText(body)).tier.code in setOf("T3", "T4")
