package com.hoddesk.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoddesk.app.data.AuditEntryEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.settings.TeamMember
import com.hoddesk.app.ui.components.ActionSheetContent
import com.hoddesk.app.ui.components.CheckCircle
import com.hoddesk.app.ui.components.Chevron
import com.hoddesk.app.ui.components.EmptyState
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.LargeTitleHeader
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.NavBar
import com.hoddesk.app.ui.components.PageContentPadding
import com.hoddesk.app.ui.components.Pill
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.components.SectionHeader
import com.hoddesk.app.ui.components.SegmentedControl
import com.hoddesk.app.ui.components.SheetAction
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private enum class Bucket(val label: String) { Overdue("Overdue"), Today("Today"), Week("This Week"), Later("Later"), NoDate("No Date"), Done("Done") }

private fun bucketOf(task: TaskEntity, today: LocalDate): Bucket {
    if (task.status == "completed") return Bucket.Done
    val due = task.dueAt ?: return Bucket.NoDate
    if (due < System.currentTimeMillis()) return Bucket.Overdue
    val day = epochToDate(due)
    return when {
        day == today -> Bucket.Today
        day <= today.plusDays(7) -> Bucket.Week
        else -> Bucket.Later
    }
}

@Composable
fun TasksScreen(tasks: List<TaskEntity>, initialSegment: Int, onToggleDone: (TaskEntity) -> Unit, onOpen: (TaskEntity) -> Unit) {
    val c = Hod.colors
    var segment by rememberSaveable(initialSegment) { mutableIntStateOf(initialSegment) }
    val today = LocalDate.now()
    val visible = when (segment) {
        0 -> tasks.filter { it.status != "completed" && it.assignee.isNullOrBlank() }
        1 -> tasks.filter { it.status != "completed" && !it.assignee.isNullOrBlank() }
        else -> tasks.filter { it.status == "completed" }.sortedByDescending { it.completedAt ?: it.createdAt }
    }
    val grouped = visible.groupBy { bucketOf(it, today) }.toSortedMap(compareBy { it.ordinal })
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PageContentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Tasks", style = Hod.type.largeTitle, color = c.label, modifier = Modifier.padding(start = 4.dp, top = 12.dp)) }
        item { SegmentedControl(listOf("Mine", "Delegated", "Done"), segment, { segment = it }) }
        if (visible.isEmpty()) item {
            Box(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface)) {
                EmptyState(
                    when (segment) { 0 -> "No open tasks"; 1 -> "Nothing delegated"; else -> "Nothing completed yet" },
                    when (segment) { 0 -> "Approve an action in Review and it becomes a task here."; 1 -> "Use Delegate in Review to hand work to your team."; else -> "Completed tasks are kept here with their history." },
                )
            }
        }
        grouped.forEach { (bucket, list) ->
            item(key = "h-${bucket.name}") {
                GroupedSection(header = "${bucket.label} · ${list.size}") {
                    list.forEachIndexed { index, task ->
                        if (index > 0) RowDivider(60.dp)
                        TaskRow(task, onToggle = { onToggleDone(task) }, onOpen = { onOpen(task) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = Hod.colors
    val state = taskState(task)
    val done = state == TaskState.Done
    Row(Modifier.fillMaxWidth().pressable(onClick = onOpen).padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(done, onToggle, task.title)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(task.title, style = Hod.type.body.copy(textDecoration = if (done) TextDecoration.LineThrough else null), color = if (done) c.tertiaryLabel else c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(task.dueAt?.let { dueLabel(it) }, task.assignee?.let { "with $it" }, task.project.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = Hod.type.footnote, color = if (state == TaskState.Overdue) c.redText else c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (state == TaskState.Blocked || state == TaskState.Clarification) {
            val pill = taskPill(state)
            Pill(pill.text, pill.background, pill.foreground, Modifier.padding(start = 6.dp))
        }
        Chevron()
    }
}

@Composable
fun TaskDetailScreen(
    task: TaskEntity,
    source: SourceEntity?,
    audit: List<AuditEntryEntity>,
    team: List<TeamMember>,
    onBack: () -> Unit,
    onSetDone: (Boolean) -> Unit,
    onReschedule: (Long?) -> Unit,
    onAssign: (String?) -> Unit,
    onBlocker: (String?) -> Unit,
    onClarify: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenSource: (SourceEntity) -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = Hod.colors
    val state = taskState(task)
    var showMore by remember { mutableStateOf(false) }
    var showOwner by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(c.groupedBackground).statusBarsPadding()) {
        NavBar("Tasks", onBack, trailing = {
            Box(Modifier.size(44.dp).pressable { showMore = true }.semantics { contentDescription = "More actions" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, null, tint = c.tint)
            }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pill = taskPill(state)
                    Pill(pill.text, pill.background, pill.foreground)
                    if (state != TaskState.Done && task.dueAt != null && epochToDate(task.dueAt) == LocalDate.now() && state != TaskState.Overdue) Pill("Due today", c.orange.copy(alpha = 0.14f), c.orangeText)
                }
                Text(task.title, style = Hod.type.title1, color = c.label)
            }

            GroupedSection {
                ListRow("Due", icon = Icons.Rounded.CalendarMonth, iconColor = c.red, value = task.dueAt?.let { dueLabel(it) } ?: "Add date", valueColor = if (task.dueAt == null) c.tint else c.secondaryLabel, chevron = true, onClick = { showDate = true })
                RowDivider(58.dp)
                ListRow("Owner", icon = Icons.Rounded.Person, iconColor = c.blue, value = task.assignee ?: "Me", chevron = true, onClick = { showOwner = true })
                RowDivider(58.dp)
                ListRow("Matter", icon = Icons.Rounded.Folder, iconColor = c.indigo, value = task.project.ifBlank { "—" })
                if (source != null) {
                    RowDivider(58.dp)
                    ListRow("Source", icon = Icons.Rounded.Description, iconColor = c.tint, value = source.title, chevron = true, onClick = { onOpenSource(source) })
                }
            }

            Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("NEXT STEP", style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.tint)
                Text(nextStep(task, state), style = Hod.type.body, color = c.label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        state == TaskState.Blocked -> HodButton("Clear Blocker", { onBlocker(null) }, style = HodButtonStyle.Tinted, height = 36.dp)
                        state == TaskState.Clarification -> HodButton("Mark Answered", { onSetDone(false) }, style = HodButtonStyle.Tinted, height = 36.dp)
                        task.dueAt == null && state != TaskState.Done -> HodButton("Pick a Time", { showDate = true }, style = HodButtonStyle.Tinted, height = 36.dp)
                        else -> {}
                    }
                }
            }

            GroupedSection(header = "Workflow") {
                ListRow("Blocker", icon = Icons.Rounded.Block, iconColor = c.gray, value = task.blocker ?: "None", chevron = true, onClick = { prompt = "blocker" })
                RowDivider(58.dp)
                ListRow("Clarification", icon = Icons.Rounded.HelpOutline, iconColor = c.orange, value = task.clarification ?: "None", chevron = true, onClick = { prompt = "clarify" })
            }

            if (source != null) Column {
                SectionHeader("From the source")
                Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).pressable { onOpenSource(source) }.padding(16.dp)) {
                    Text(cleanEvidence(source.body).take(420), style = Hod.type.subheadline, color = c.secondaryLabel, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
            }

            if (audit.isNotEmpty()) Column {
                SectionHeader("History")
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    audit.take(12).forEachIndexed { i, entry ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(if (i == 0) c.tint else c.chevron))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(humanAudit(entry), style = Hod.type.subheadline, color = c.label)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAt)), style = Hod.type.caption, color = c.secondaryLabel)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HodButton("Reschedule", { showDate = true }, style = HodButtonStyle.Gray, modifier = Modifier.weight(1f))
            if (state == TaskState.Done) HodButton("Reopen", { onSetDone(false) }, style = HodButtonStyle.Tinted, modifier = Modifier.weight(1.6f))
            else HodButton("Mark Done", { onSetDone(true) }, modifier = Modifier.weight(1.6f))
        }
    }

    if (showMore) SheetDialog({ showMore = false }) {
        ActionSheetContent(null, null, listOf(SheetAction("Delete Task", destructive = true) { showMore = false; onDelete() }), onCancel = { showMore = false })
    }
    if (showOwner) SheetDialog({ showOwner = false }) {
        ActionSheetContent(
            "Who owns this?", if (team.isEmpty()) "Add your team in Settings › Department." else null,
            listOf(SheetAction("Me", emphasized = task.assignee == null) { onAssign(null); showOwner = false }) +
                team.map { m -> SheetAction(m.name, emphasized = task.assignee == m.name) { onAssign(m.name); showOwner = false } } +
                SheetAction("Someone else…") { showOwner = false; prompt = "owner" },
            onCancel = { showOwner = false },
        )
    }
    if (showDate) DateTimePickerDialog(task.dueAt, onDismiss = { showDate = false }) { onReschedule(it); showDate = false }
    when (prompt) {
        "blocker" -> TextPromptDialog("Blocker", "What is this waiting on?", task.blocker.orEmpty(), "Save", onDismiss = { prompt = null }, multiline = true) { onBlocker(it.ifBlank { null }); prompt = null }
        "clarify" -> TextPromptDialog("Ask for Clarification", "What needs clarifying?", task.clarification.orEmpty(), "Save", onDismiss = { prompt = null }, multiline = true) { if (it.isNotBlank()) onClarify(it); prompt = null }
        "owner" -> TextPromptDialog("Delegate to", "Name", "", "Delegate", onDismiss = { prompt = null }) { if (it.isNotBlank()) onAssign(it.trim()); prompt = null }
    }
}

private fun nextStep(task: TaskEntity, state: TaskState): String = when (state) {
    TaskState.Done -> "Completed${task.completedAt?.let { " on " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: ""}."
    TaskState.Blocked -> "Waiting on: ${task.blocker}. Clear the blocker when it's resolved."
    TaskState.Clarification -> "Clarification requested: ${task.clarification}"
    TaskState.Overdue -> "This was due ${dueLabel(task.dueAt)}. Do it now or reschedule."
    TaskState.Delegated -> "${task.assignee} owns this. Check in before ${task.dueAt?.let { dueLabel(it) } ?: "it slips"}."
    TaskState.Open -> "No time set. Pick a slot so it doesn't drift."
    TaskState.Scheduled -> "Due ${dueLabel(task.dueAt)}."
}

fun humanAudit(entry: AuditEntryEntity): String {
    val d = entry.detail?.takeIf { it.isNotBlank() }
    return when (entry.action) {
        "created_from_action" -> "Approved from review"
        "action_approved" -> "Approved: ${d.orEmpty()}"
        "completed" -> "Marked done"
        "reopened" -> "Reopened"
        "delegated" -> "Delegated to ${d.orEmpty()}"
        "taken_back" -> "Taken back"
        "rescheduled" -> "Due date set to ${d.orEmpty()}"
        "due_cleared" -> "Due date cleared"
        "blocker_added" -> "Blocked: ${d.orEmpty()}"
        "blocker_cleared" -> "Blocker cleared"
        "clarification_requested" -> "Clarification requested: ${d.orEmpty()}"
        "scheduled_confirmed" -> "Added to calendar: ${d.orEmpty()}"
        "deleted" -> "Deleted"
        "dismissed_not_mine" -> "Filed as not mine"
        "dismissed_no_action" -> "No action needed"
        else -> entry.action.replace('_', ' ').replaceFirstChar(Char::uppercase) + (d?.let { ": $it" } ?: "")
    }
}
