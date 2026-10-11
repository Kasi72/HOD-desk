package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.followup.FollowUpCandidate
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.theme.Hod

@Composable
fun FollowUpPage(candidates: List<FollowUpCandidate>, onBack: () -> Unit, onCopy: (FollowUpCandidate) -> Unit, onShare: (FollowUpCandidate) -> Unit) {
    val c = Hod.colors
    SettingsPage("Follow-ups", "Settings", onBack) {
        Text("Drafts are prepared from waiting commitments and their evidence. Nothing is sent automatically.", style = Hod.type.subheadline, color = c.secondaryLabel)
        if (candidates.isEmpty()) {
            GroupedSection { Text("No follow-ups are due.", style = Hod.type.body, color = c.secondaryLabel, modifier = Modifier.padding(16.dp)) }
        } else candidates.forEach { candidate ->
            val draft = remember(candidate.commitmentId) { mutableStateOf(candidate.draft) }
            GroupedSection(header = candidate.reason) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = { draft.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Draft message") },
                    )
                    Text("Evidence: " + candidate.evidence, style = Hod.type.footnote, color = c.secondaryLabel)
                    RowDivider()
                    HodButton("Copy draft", { onCopy(candidate.copy(draft = draft.value)) }, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth())
                    HodButton("Share after review", { onShare(candidate.copy(draft = draft.value)) }, style = HodButtonStyle.Gray, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
