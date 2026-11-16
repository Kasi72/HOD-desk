package com.hoddesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.evaluation.EvaluationCorpus
import com.hoddesk.app.evaluation.EvaluationStore
import com.hoddesk.app.evaluation.EvaluationRunner
import com.hoddesk.app.ui.components.HodButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.theme.Hod

/** A privacy-preserving, on-device readiness view for the evaluation set. */
@Composable
fun EvaluationLabPage(sources: List<SourceEntity>, onBack: () -> Unit) {
    val c = Hod.colors
    val context = LocalContext.current
    var latest by remember { mutableStateOf(EvaluationStore.latest(context)) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val baseline = EvaluationCorpus.baseline
    val anonymized = sources.count { EvaluationStore.anonymize(it.body) != it.body }
    SettingsPage("Evaluation lab", "Settings", onBack) {
        Text("Measure retrieval and extraction changes before promoting a new model or prompt. Only anonymised text is used in this view.", style = Hod.type.subheadline, color = c.secondaryLabel)
        HodButton(if (running) "Running checks…" else "Run extraction checks", {
            running = true; error = null
            scope.launch {
                try {
                    val run = withContext(Dispatchers.Default) { EvaluationRunner.deterministic() }
                    withContext(Dispatchers.IO) { EvaluationStore.saveLatest(context, run) }
                    latest = run
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "Evaluation failed" }
                finally { running = false }
            }
        }, enabled = !running)
        Text("This runs text extraction on synthetic cases. Real Tamil audio, OCR, retrieval and Gemma inference need separate device evaluation before release.", style = Hod.type.footnote, color = c.secondaryLabel)
        error?.let { Text(it, style = Hod.type.footnote, color = c.redText) }
        GroupedSection(header = "Current corpus") {
            ListRow("Fixed baseline cases", value = baseline.size.toString())
            RowDivider(58.dp)
            ListRow("Imported examples", value = sources.size.toString())
            RowDivider(58.dp)
            ListRow("Examples requiring redaction", value = anonymized.toString(), valueColor = if (anonymized > 0) c.orangeText else c.greenText)
            RowDivider(58.dp)
            ListRow("Clinical items excluded", value = sources.count { it.sensitivity == "T3" || it.sensitivity == "T4" }.toString(), valueColor = c.greenText)
        }
        GroupedSection(header = "Modality coverage") {
            EvaluationCorpus.baseline.groupBy { it.inputType }.toSortedMap().entries.forEachIndexed { index, (type, cases) ->
                if (index > 0) RowDivider(58.dp)
                ListRow(type.replaceFirstChar { it.uppercase() }, value = "${cases.size} cases")
            }
            RowDivider(58.dp)
            ListRow("Languages", value = "English · Tamil · Tanglish")
        }
        GroupedSection(header = "Safety gate") {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("A model or prompt change is ready only when action recall improves without increasing false verified fields.", style = Hod.type.body, color = c.label)
                Text("Run the fixed Tamil, Tanglish and English cases on a desktop reference model, then compare the report before switching the index snapshot.", style = Hod.type.footnote, color = c.secondaryLabel)
                Text("The baseline is synthetic and contains no patient data. Accepted actions must retain a source citation.", style = Hod.type.footnote, color = c.secondaryLabel)
            }
        }
        latest?.let { run ->
            val gate = EvaluationStore.gate(run.baseline, run.current, run.falseVerifiedRate)
            GroupedSection(header = "Last recorded run") {
                ListRow("Extraction gate", value = if (gate.passed) "Passed" else "Blocked", valueColor = if (gate.passed) c.greenText else c.redText)
                ListRow("Test scope", value = run.scope)
                RowDivider(58.dp)
                ListRow("Action recall", value = "${(run.current.recallAt10 * 100).toInt()}%")
                RowDivider(58.dp)
                ListRow("False verified", value = "${(run.falseVerifiedRate * 100).toInt()}%", valueColor = if (run.falseVerifiedRate <= .02) c.greenText else c.redText)
                RowDivider(58.dp)
                ListRow("Field accuracy", value = "${(run.current.fieldAccuracy * 100).toInt()}%", valueColor = if (run.current.fieldAccuracy >= .95) c.greenText else c.redText)
                RowDivider(58.dp)
                ListRow("Citation coverage", value = "${(run.current.citationCoverage * 100).toInt()}%", valueColor = if (run.current.citationCoverage >= .98) c.greenText else c.redText)
                if (!gate.passed) Text(gate.reasons.joinToString("\n"), style = Hod.type.footnote, color = c.redText, modifier = Modifier.padding(16.dp))
                run.current.cer?.let { cer ->
                    RowDivider(58.dp)
                    ListRow("Character error rate", value = "${(cer * 100).toInt()}%")
                }
                run.current.wer?.let { wer ->
                    RowDivider(58.dp)
                    ListRow("Word error rate", value = "${(wer * 100).toInt()}%")
                }
            }
        }
    }
}
