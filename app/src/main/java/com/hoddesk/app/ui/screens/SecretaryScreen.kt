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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.secretary.SecretaryIntent
import com.hoddesk.app.secretary.VoiceSecretary
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.theme.Hod

@Composable
fun SecretaryPage(initialTranscript: String = "", onBack: () -> Unit, onRecord: () -> Unit, onRun: (SecretaryIntent) -> Unit) {
    val c = Hod.colors
    val transcript = remember(initialTranscript) { mutableStateOf(initialTranscript) }
    val intent = remember(transcript.value) { VoiceSecretary.parse(transcript.value) }
    SettingsPage("Secretary", "Settings", onBack) {
        Text("Ask about meetings, waiting items or search. Actions that change work always require confirmation.", style = Hod.type.subheadline, color = c.secondaryLabel)
        GroupedSection {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(transcript.value, { transcript.value = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Type or paste a voice transcript") })
                HodButton("Speak to secretary", onRecord, style = HodButtonStyle.Gray, modifier = Modifier.fillMaxWidth())
                Text("Intent: " + intent.kind.name.replace('_', ' '), style = Hod.type.subheadline, color = c.tint)
                Text(if (intent.requiresConfirmation) "Confirmation required before any change." else "Read-only request.", style = Hod.type.footnote, color = c.secondaryLabel)
                HodButton("Run secretary request", { onRun(intent) }, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth(), enabled = transcript.value.isNotBlank())
            }
        }
    }
}
