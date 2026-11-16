package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.followup.FollowUpCandidate
import com.hoddesk.app.followup.FollowUpEngine
import com.hoddesk.app.followup.FollowUpLanguage
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.theme.Hod

@Composable
fun FollowUpPage(candidates: List<FollowUpCandidate>, onBack: () -> Unit, onDraftGenerated: (FollowUpCandidate) -> Unit, onDraftSaved: (FollowUpCandidate) -> Unit, onCopy: (FollowUpCandidate) -> Unit, onShare: (FollowUpCandidate) -> Unit, onDismiss: (FollowUpCandidate) -> Unit) {
    val c = Hod.colors
    SettingsPage("Follow-ups", "Settings", onBack) {
        Text("Drafts are prepared from waiting commitments and their evidence. Nothing is sent automatically.", style = Hod.type.subheadline, color = c.secondaryLabel)
        if (candidates.isEmpty()) {
            GroupedSection { Text("No follow-ups are due.", style = Hod.type.body, color = c.secondaryLabel, modifier = Modifier.padding(16.dp)) }
        } else candidates.forEach { candidate ->
            val draft = remember(candidate.commitmentId) { mutableStateOf(candidate.draft) }
            val language = remember(candidate.commitmentId) { mutableStateOf(FollowUpLanguage.ENGLISH) }
            LaunchedEffect(candidate.commitmentId) { onDraftGenerated(candidate) }
            GroupedSection(header = candidate.reason) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = { draft.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Draft message") },
                    )
                    Text("Evidence: " + candidate.evidence, style = Hod.type.footnote, color = c.secondaryLabel)
                    Text("Draft language", style = Hod.type.footnote, color = c.secondaryLabel)
                    Row {
                        FollowUpLanguage.entries.forEach { option ->
                            HodButton(option.name.lowercase().replaceFirstChar(Char::uppercase), {
                                language.value = option
                                draft.value = FollowUpEngine.localize(candidate.copy(draft = draft.value), option).draft
                            }, style = if (language.value == option) HodButtonStyle.Tinted else HodButtonStyle.Gray)
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    RowDivider()
                    HodButton("Save draft", { onDraftSaved(candidate.copy(draft = draft.value)) }, style = HodButtonStyle.Gray, modifier = Modifier.fillMaxWidth())
                    HodButton("Copy draft", { onCopy(candidate.copy(draft = draft.value)) }, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth())
                    HodButton("Share after review", { onShare(candidate.copy(draft = draft.value)) }, style = HodButtonStyle.Gray, modifier = Modifier.fillMaxWidth())
                    HodButton("Dismiss", { onDismiss(candidate) }, style = HodButtonStyle.Gray, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
