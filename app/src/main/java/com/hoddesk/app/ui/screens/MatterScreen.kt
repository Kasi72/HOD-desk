package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.semantic.MatterTimeline
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.theme.Hod

@Composable
fun MatterPage(timeline: MatterTimeline, onBack: () -> Unit, onReviewChange: () -> Unit, onAcceptChange: (Long) -> Unit, onKeepExisting: (Long) -> Unit, onCreateNew: (Long) -> Unit = {}, onKeepSeparate: (Long) -> Unit = {}) {
    val c = Hod.colors
    SettingsPage("Matter file", "Library", onBack) {
        Text("Linked evidence, commitments and schedule for one matter.", style = Hod.type.subheadline, color = c.secondaryLabel)
        timeline.latestInstruction?.let { latest ->
            GroupedSection(header = "Latest instruction") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(latest.title, style = Hod.type.headline, color = c.label)
                    Text(cleanEvidence(latest.body), style = Hod.type.body, color = c.secondaryLabel)
                }
            }
        }
        GroupedSection(header = "Next action") {
            timeline.nextAction?.let { task ->
                ListRow(task.title, subtitle = task.project, value = task.dueAt?.let { dueLabel(it) } ?: "No date")
            } ?: Text("No open action", style = Hod.type.body, color = c.secondaryLabel, modifier = Modifier.padding(16.dp))
        }
        if (timeline.unresolvedQuestions.isNotEmpty()) GroupedSection(header = "Needs clarification") {
            timeline.unresolvedQuestions.forEachIndexed { index, question ->
                if (index > 0) RowDivider()
                Text(question, style = Hod.type.body, color = c.orangeText, modifier = Modifier.padding(16.dp))
            }
        }
        GroupedSection(header = "Sources") {
            timeline.sources.forEachIndexed { index, source ->
                if (index > 0) RowDivider()
                ListRow(source.title, subtitle = source.sourceType, value = dueLabel(source.capturedAt).substringBefore(","))
            }
        }
        timeline.tasks.firstOrNull { it.clarification != null }?.let { task ->
            GroupedSection(header = "Proposed change") {
                ListRow("Create new task", value = "Separate", titleColor = c.tint, onClick = { onCreateNew(task.id) })
                RowDivider(58.dp)
                ListRow("Update existing", value = "Review evidence", titleColor = c.greenText, onClick = { onAcceptChange(task.id) })
                RowDivider(58.dp)
                ListRow("Keep separate", titleColor = c.secondaryLabel, onClick = { onKeepSeparate(task.id) })
                RowDivider(58.dp)
                ListRow("Review evidence", value = "Open", titleColor = c.tint, onClick = onReviewChange)
                RowDivider(58.dp)
                ListRow("Keep existing task", titleColor = c.secondaryLabel, onClick = { onKeepExisting(task.id) })
            }
        }
    }
}
