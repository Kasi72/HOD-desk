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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hoddesk.app.commitments.CommitmentLedger
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.SegmentedControl
import com.hoddesk.app.followup.FollowUpCandidate
import com.hoddesk.app.followup.FollowUpEngine
import com.hoddesk.app.followup.FollowUpLanguage
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.theme.Hod

@Composable
fun FollowUpPage(candidates: List<FollowUpCandidate>, commitments: List<CommitmentEntity> = emptyList(), onBack: () -> Unit, onDraftGenerated: (FollowUpCandidate) -> Unit, onDraftSaved: (FollowUpCandidate) -> Unit, onCopy: (FollowUpCandidate) -> Unit, onShare: (FollowUpCandidate) -> Unit, onDismiss: (FollowUpCandidate) -> Unit) {
    val c = Hod.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val waiting = CommitmentLedger.waitingOnOthers(commitments)
    val mine = CommitmentLedger.pendingForMe(commitments)
    SettingsPage("Follow-ups", "Settings", onBack) {
        SegmentedControl(listOf("Waiting On (${waiting.size})", "I Owe (${mine.size})"), tab, { tab = it })
        if (tab == 1) {
            CommitmentList(mine, now, empty = "Nothing you owe is open.", footer = "Tasks you approved for yourself. Mark them done in Tasks.")
            return@SettingsPage
        }
        if (waiting.isNotEmpty()) CommitmentList(waiting, now, empty = "", footer = "Tasks you delegated. A draft reminder appears below after two quiet days.", header = "Delegated")
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

@Composable
private fun CommitmentList(items: List<CommitmentEntity>, now: Long, empty: String, footer: String, header: String? = null) {
    val c = Hod.colors
    GroupedSection(header = header, footer = footer) {
        if (items.isEmpty()) Text(empty, style = Hod.type.body, color = c.secondaryLabel, modifier = Modifier.padding(16.dp))
        items.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            val overdue = item.dueAt != null && item.dueAt < now
            ListRow(
                item.summary,
                subtitle = item.owner?.takeIf { it != "me" }?.let { "With $it" },
                value = if (overdue) "Overdue" else item.dueAt?.let { dueLabel(it).substringBefore(",") } ?: "No date",
                valueColor = if (overdue) c.redText else c.secondaryLabel,
            )
        }
    }
}
