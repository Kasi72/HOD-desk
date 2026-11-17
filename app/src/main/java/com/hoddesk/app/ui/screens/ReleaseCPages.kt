package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.health.SystemHealth
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.components.SettingsHeaderCard
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.workflow.WeeklyReview
import java.text.DateFormat
import java.util.Date

private const val ROW_LIMIT = 6

/** Weekly executive review. Read-only: rows open the task, Review or Follow-ups. */
@Composable
fun WeeklyReviewPage(
    report: WeeklyReview.Report,
    lastReviewAt: Long?,
    onBack: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenReview: () -> Unit,
    onOpenFollowUps: () -> Unit,
    onMarkReviewed: () -> Unit,
) {
    val c = Hod.colors
    SettingsPage("Weekly Review", "Back", onBack) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(report.headline(), style = Hod.type.title3, color = c.label)
            Text(
                "Last 7 days and the next 7. " + (lastReviewAt?.let { "Last reviewed ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}." } ?: "Not reviewed before."),
                style = Hod.type.subheadline, color = c.secondaryLabel,
            )
        }
        TaskGroup("Slipped", report.slipped, "Past their due date and still open.", late = true, onOpenTask)
        TaskGroup("Due in the Next 7 Days", report.dueThisWeek, null, late = false, onOpenTask)
        CommitmentGroup("Waiting On Others", report.waitingOn, onOpenFollowUps)
        CommitmentGroup("I Owe", report.iOwe, onOpenFollowUps)
        TaskGroup("No Due Date", report.undated, "Give these a date or close them.", late = false, onOpenTask)
        GroupedSection(header = "Desk") {
            ListRow("Waiting in Review", value = report.reviewBacklog.toString() + (report.oldestReviewDays?.takeIf { it > 0 }?.let { " · oldest $it d" } ?: ""),
                valueColor = if (report.reviewBacklog > 0) c.orangeText else c.greenText, chevron = report.reviewBacklog > 0,
                onClick = if (report.reviewBacklog > 0) onOpenReview else null)
            RowDivider()
            ListRow("Overdue promises", value = report.overdueCommitments.toString(), valueColor = if (report.overdueCommitments > 0) c.redText else c.greenText)
            RowDivider()
            ListRow("Meetings in the next 7 days", value = report.meetingsAhead.toString())
            RowDivider()
            ListRow("Completed in the last 7 days", value = report.done.size.toString(), valueColor = if (report.done.isNotEmpty()) c.greenText else c.secondaryLabel)
        }
        HodButton("Mark Week as Reviewed", onMarkReviewed, modifier = Modifier.fillMaxWidth())
        Text("Nothing is changed by this review. Open an item to act on it.", style = Hod.type.footnote, color = c.tertiaryLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TaskGroup(header: String, tasks: List<TaskEntity>, footer: String?, late: Boolean, onOpenTask: (TaskEntity) -> Unit) {
    if (tasks.isEmpty()) return
    val more = if (tasks.size > ROW_LIMIT) "Showing $ROW_LIMIT of ${tasks.size}; the rest are in Tasks." else null
    GroupedSection(header = "$header (${tasks.size})", footer = listOfNotNull(footer, more).joinToString(" ").ifBlank { null }) {
        tasks.take(ROW_LIMIT).forEachIndexed { i, t ->
            if (i > 0) RowDivider()
            ListRow(
                t.title, subtitle = listOfNotNull(t.dueAt?.let { dueLabel(it) }, t.assignee?.let { "with $it" }).joinToString(" · ").ifBlank { t.project },
                value = if (late) "Late" else null, valueColor = Hod.colors.redText, chevron = true, onClick = { onOpenTask(t) },
            )
        }
    }
}

@Composable
private fun CommitmentGroup(header: String, items: List<CommitmentEntity>, onOpen: () -> Unit) {
    if (items.isEmpty()) return
    val now = System.currentTimeMillis()
    GroupedSection(header = "$header (${items.size})") {
        items.take(ROW_LIMIT).forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            val late = item.dueAt != null && item.dueAt < now
            ListRow(
                item.summary, subtitle = listOfNotNull(item.counterparty, item.dueAt?.let { dueLabel(it) }).joinToString(" · ").ifBlank { null },
                value = if (late) "Late" else null, valueColor = Hod.colors.redText, chevron = true, onClick = onOpen,
            )
        }
    }
}

/** One page showing every part of the app and whether it is working. */
@Composable
fun HealthPage(rows: List<SystemHealth.Row>, onBack: () -> Unit, onRunSelfCheck: (() -> Unit)?) {
    val c = Hod.colors
    val overall = SystemHealth.overall(rows)
    SettingsPage("App Health", "Settings", onBack) {
        val (icon, color, title, text) = when (overall) {
            SystemHealth.Level.BLOCKED -> Quad(Icons.Rounded.Error, c.red, "Something Is Broken", "One part cannot work until it is fixed. Details below.")
            SystemHealth.Level.ATTENTION -> Quad(Icons.Rounded.Warning, c.orange, "Needs Attention", "Everything works, but some parts need a quick look.")
            else -> Quad(Icons.Rounded.CheckCircle, c.green, "All Good", "Every part of HOD Desk is working on this phone.")
        }
        SettingsHeaderCard(icon, color, title, text)
        rows.groupBy { it.group }.forEach { (group, groupRows) ->
            GroupedSection(header = group) {
                groupRows.forEachIndexed { i, row ->
                    if (i > 0) RowDivider(58.dp)
                    val (rowIcon, rowColor) = when (row.level) {
                        SystemHealth.Level.OK -> Icons.Rounded.CheckCircle to c.green
                        SystemHealth.Level.ATTENTION -> Icons.Rounded.Warning to c.orange
                        SystemHealth.Level.BLOCKED -> Icons.Rounded.Error to c.red
                        SystemHealth.Level.OFF -> Icons.Rounded.RemoveCircleOutline to c.gray
                    }
                    ListRow(
                        row.label, subtitle = row.detail, icon = rowIcon, iconColor = rowColor, value = row.value,
                        valueColor = when (row.level) {
                            SystemHealth.Level.ATTENTION -> c.orangeText
                            SystemHealth.Level.BLOCKED -> c.redText
                            else -> c.secondaryLabel
                        },
                    )
                }
            }
        }
        if (onRunSelfCheck != null) HodButton("Run AI Self-Check", onRunSelfCheck, modifier = Modifier.fillMaxWidth(), style = com.hoddesk.app.ui.components.HodButtonStyle.Tinted)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
