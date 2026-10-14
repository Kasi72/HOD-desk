package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.secretary.SecretaryIntent
import com.hoddesk.app.secretary.VoiceSecretary
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.theme.Hod

@Composable
fun SecretaryPage(initialTranscript: String = "", supportingSources: List<SourceEntity> = emptyList(), proposalDiff: String? = null,
                  onBack: () -> Unit, onRecord: () -> Unit, onTranscriptChanged: (String) -> Unit = {}, onRun: (SecretaryIntent) -> Unit) {
    val c = Hod.colors
    val transcript = remember(initialTranscript) { mutableStateOf(initialTranscript) }
    val intent = remember(transcript.value) { VoiceSecretary.parse(transcript.value) }
    var confirmedTranscript by remember { mutableStateOf<String?>(null) }
    SettingsPage("Secretary", "Settings", onBack) {
        Text("Ask about meetings, waiting items or search. Actions that change work always require confirmation.", style = Hod.type.subheadline, color = c.secondaryLabel)
        GroupedSection {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(transcript.value, { value -> transcript.value = value; onTranscriptChanged(value) }, modifier = Modifier.fillMaxWidth(), label = { Text("Type or paste a voice transcript") })
                HodButton("Speak to secretary", onRecord, style = HodButtonStyle.Gray, modifier = Modifier.fillMaxWidth())
                Text("Intent: " + intent.kind.name.replace('_', ' '), style = Hod.type.subheadline, color = c.tint)
                Text(if (intent.requiresConfirmation) "Confirmation required before any change." else "Read-only request.", style = Hod.type.footnote, color = c.secondaryLabel)
                if (intent.requiresConfirmation) {
                    Text("This request can change tasks or schedules. Review it before continuing.", style = Hod.type.footnote, color = c.orangeText)
                    if (supportingSources.isNotEmpty()) {
                        Text("Supporting evidence", style = Hod.type.subheadline, color = c.label)
                        supportingSources.take(5).forEach { source ->
                            Text("${source.title}: ${source.body.trim().take(180)}", style = Hod.type.footnote, color = c.secondaryLabel)
                        }
                    } else Text("No supporting evidence was found; confirmation will not guess missing details.", style = Hod.type.footnote, color = c.orangeText)
                    proposalDiff?.let {
                        Text("Proposed change", style = Hod.type.subheadline, color = c.label)
                        Text(it, style = Hod.type.body, color = c.tint)
                    }
                    HodButton("Review request", { confirmedTranscript = transcript.value }, style = HodButtonStyle.Gray, modifier = Modifier.fillMaxWidth(), enabled = transcript.value.isNotBlank())
                    if (confirmedTranscript == transcript.value) {
                        Text("Ready to apply: ${intent.kind.name.replace('_', ' ').lowercase()}", style = Hod.type.subheadline, color = c.tint)
                        HodButton("Confirm and run", { onRun(intent); confirmedTranscript = null }, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    HodButton("Run secretary request", { onRun(intent) }, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth(), enabled = transcript.value.isNotBlank())
                }
            }
        }
    }
}
