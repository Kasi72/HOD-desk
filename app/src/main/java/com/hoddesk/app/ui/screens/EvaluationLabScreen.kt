package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.evaluation.EvaluationStore
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.theme.Hod

/** A privacy-preserving, on-device readiness view for the evaluation set. */
@Composable
fun EvaluationLabPage(sources: List<SourceEntity>, onBack: () -> Unit) {
    val c = Hod.colors
    val anonymized = sources.count { EvaluationStore.anonymize(it.body) != it.body }
    SettingsPage("Evaluation lab", "Settings", onBack) {
        Text("Measure retrieval and extraction changes before promoting a new model or prompt. Only anonymised text is used in this view.", style = Hod.type.subheadline, color = c.secondaryLabel)
        GroupedSection(header = "Current corpus") {
            ListRow("Imported examples", value = sources.size.toString())
            RowDivider(58.dp)
            ListRow("Examples requiring redaction", value = anonymized.toString(), valueColor = if (anonymized > 0) c.orangeText else c.greenText)
            RowDivider(58.dp)
            ListRow("Clinical items excluded", value = sources.count { it.sensitivity == "T3" || it.sensitivity == "T4" }.toString(), valueColor = c.greenText)
        }
        GroupedSection(header = "Safety gate") {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("A model or prompt change is ready only when action recall improves without increasing false verified fields.", style = Hod.type.body, color = c.label)
                Text("Run the fixed Tamil, Tanglish and English cases on a desktop reference model, then compare the report before switching the index snapshot.", style = Hod.type.footnote, color = c.secondaryLabel)
            }
        }
    }
}
