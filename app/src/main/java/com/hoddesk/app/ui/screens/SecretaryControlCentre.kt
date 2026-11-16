package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.secretary.SecretaryStateSnapshot
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.theme.Hod

@Composable
fun SecretaryControlCentre(state: SecretaryStateSnapshot, aiHealthy: Boolean, onBack: () -> Unit, onOpenReview: () -> Unit, onOpenCalendar: () -> Unit, onOpenWaiting: () -> Unit) {
    val c = Hod.colors
    SettingsPage("Secretary Control Centre", "Settings", onBack) {
        Text("A single view of work requiring attention. Every number links back to the existing evidence workflow.", style = Hod.type.subheadline, color = c.secondaryLabel)
        GroupedSection(header = "Today") {
            ListRow("Priority actions", value = state.action.toString(), valueColor = if (state.action > 0) c.orangeText else c.greenText, chevron = true, onClick = onOpenReview)
            RowDivider(58.dp)
            ListRow("Meetings", value = state.meetings.toString(), chevron = true, onClick = onOpenCalendar)
            RowDivider(58.dp)
            ListRow("Due within 48 hours", value = state.dueSoon.toString(), valueColor = if (state.dueSoon > 0) c.orangeText else c.secondaryLabel)
        }
        GroupedSection(header = "Risks") {
            ListRow("Waiting on others", value = state.waiting.toString(), chevron = true, onClick = onOpenWaiting)
            RowDivider(58.dp)
            ListRow("Overdue commitments", value = state.overdue.toString(), valueColor = if (state.overdue > 0) c.redText else c.greenText)
            RowDivider(58.dp)
            ListRow("Schedule conflicts", value = state.conflicts.toString(), valueColor = if (state.conflicts > 0) c.redText else c.greenText)
            RowDivider(58.dp)
            ListRow("Sensitive items protected", value = state.sensitive.toString(), valueColor = c.greenText)
        }
        GroupedSection(header = "AI status") {
            ListRow("On-device intelligence", value = if (aiHealthy) "Ready" else "Needs attention", valueColor = if (aiHealthy) c.greenText else c.orangeText)
            RowDivider(58.dp)
            ListRow("Information only", value = state.informationOnly.toString())
        }
    }
}
