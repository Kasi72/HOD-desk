package com.hoddesk.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.imports.ActionPointExtractor
import com.hoddesk.app.imports.ActionDueParser
import com.hoddesk.app.imports.RecognitionCorrectionEngine
import com.hoddesk.app.reasoning.ActionReasoningOutput
import com.hoddesk.app.reasoning.EvidenceBundleBuilder
import com.hoddesk.app.reasoning.FieldGrounding
import com.hoddesk.app.reasoning.FieldEvidenceAttribution
import com.hoddesk.app.reasoning.ActionFieldQualityScorer
import com.hoddesk.app.reasoning.ActionDependencyAnalyzer
import com.hoddesk.app.reasoning.ActionReasoningFallback
import com.hoddesk.app.reasoning.OnDeviceActionReasoner
import com.hoddesk.app.reasoning.ReviewApprovalPolicy
import com.hoddesk.app.semantic.SourceSemanticHit
import com.hoddesk.app.settings.TeamMember
import com.hoddesk.app.ui.components.ActionSheetContent
import com.hoddesk.app.ui.components.EmptyState
import com.hoddesk.app.ui.components.EvidenceText
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.IconTile
import com.hoddesk.app.ui.components.NavBar
import com.hoddesk.app.ui.components.Pill
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.components.SectionHeader
import com.hoddesk.app.ui.components.SheetAction
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private enum class FieldState { Verified, Likely, Confirm, Conflict, Missing }
private data class FieldRowModel(val key: String, val label: String, val value: String?, val state: FieldState, val confidence: Float = 0f, val citation: String? = null)

@Composable
fun ReviewScreen(
    queue: List<SourceEntity>,
    startSourceId: Long?,
    team: List<TeamMember>,
    observeActionPoints: (Long) -> Flow<List<ActionPointEntity>>,
    searchRelated: suspend (String, Long) -> List<SourceSemanticHit>,
    rankActions: suspend (List<String>) -> List<String>,
    instructionModelPath: String?,
    onReasoning: (Long, ActionReasoningOutput) -> Unit,
    onUpdateActionPoint: suspend (ActionPointEntity) -> Unit,
    onApproveEdited: suspend (SourceEntity, ActionPointEntity?, String, String?) -> Unit,
    onApprove: suspend (source: SourceEntity, action: String, assignee: String?, dueAt: Long?) -> Unit,
    onActionStatus: suspend (sourceId: Long, action: String, status: String) -> Unit,
    onDismissSource: suspend (sourceId: Long, reason: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = Hod.colors
    var currentId by remember { mutableStateOf(startSourceId) }
    val source = queue.firstOrNull { it.id == currentId } ?: queue.firstOrNull()
    Column(Modifier.fillMaxSize().background(c.groupedBackground).statusBarsPadding()) {
        val position = source?.let { queue.indexOf(it) + 1 } ?: 0
        NavBar(
            backLabel = "Today", onBack = onBack, title = "Review",
            subtitle = if (source != null) "$position of ${queue.size}" else null,
            trailing = if (queue.size > 1 && source != null) ({
                Text("Later", style = Hod.type.body, color = c.tint, modifier = Modifier.padding(horizontal = 8.dp).pressable {
                    val i = queue.indexOf(source)
                    currentId = queue[(i + 1) % queue.size].id
                })
            }) else null,
        )
        if (source == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("All clear", "Nothing is waiting for review. New imports will appear here.")
                    HodButton("Back to Today", onBack, style = HodButtonStyle.Tinted, height = 44.dp)
                }
            }
        } else {
            SourceReview(
                source = source, team = team, observeActionPoints = observeActionPoints, searchRelated = searchRelated,
                rankActions = rankActions, instructionModelPath = instructionModelPath, onReasoning = onReasoning,
                onUpdateActionPoint = onUpdateActionPoint,
                onApproveEdited = onApproveEdited,
                onApprove = onApprove, onActionStatus = onActionStatus, onDismissSource = onDismissSource,
            )
        }
    }
}

@Composable
private fun SourceReview(
    source: SourceEntity,
    team: List<TeamMember>,
    observeActionPoints: (Long) -> Flow<List<ActionPointEntity>>,
    searchRelated: suspend (String, Long) -> List<SourceSemanticHit>,
    rankActions: suspend (List<String>) -> List<String>,
    instructionModelPath: String?,
    onReasoning: (Long, ActionReasoningOutput) -> Unit,
    onUpdateActionPoint: suspend (ActionPointEntity) -> Unit,
    onApproveEdited: suspend (SourceEntity, ActionPointEntity?, String, String?) -> Unit,
    onApprove: suspend (SourceEntity, String, String?, Long?) -> Unit,
    onActionStatus: suspend (Long, String, String) -> Unit,
    onDismissSource: suspend (Long, String) -> Unit,
) {
    val c = Hod.colors
    val context = LocalContext.current
    val pointState by remember(source.id) { observeActionPoints(source.id).map { it as List<ActionPointEntity>? } }.collectAsState(initial = null)
    val actionPoints = pointState.orEmpty()
    val scope = rememberCoroutineScope()
    var busy by remember(source.id) { mutableStateOf(false) }
    var operationError by remember(source.id) { mutableStateOf<String?>(null) }
    fun perform(operation: suspend () -> Unit) {
        if (busy) return
        busy = true; operationError = null
        scope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { operationError = failure.message ?: "Could not save. Please retry." }
            finally { busy = false }
        }
    }
    val evidence = remember(source.id, source.body) { cleanEvidence(source.body) }
    val pending = actionPoints.filter { it.status == "needs_review" || it.status == "needs_confirmation" || it.status == "needs_clarification" }
    val fallback = remember(source.id, evidence) { ActionPointExtractor().extract(evidence).map { it.toEntity(source.id) } }
    var manual by remember(source.id) { mutableStateOf<ActionPointEntity?>(null) }
    val candidates = if (actionPoints.isNotEmpty()) pending else if (pointState != null) fallback else emptyList()
    val baseActions = when {
        manual != null -> listOf(manual!!.action)
        else -> candidates.map { it.action }
    }
    var ranked by remember(source.id) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(source.id, baseActions) { ranked = if (baseActions.size > 1) runCatching { rankActions(baseActions) }.getOrNull() else null }
    val actions = (ranked.orEmpty().filter { it in baseActions } + baseActions).distinct()
    var handled by remember(source.id) { mutableStateOf(setOf<String>()) }
    val remaining = actions.filter { it !in handled }
    val action = remaining.firstOrNull()
    val point = manual ?: candidates.firstOrNull { it.action == action }

    var editing by remember(source.id, action) { mutableStateOf(manual != null) }
    var edited by remember(source.id, action) { mutableStateOf(action.orEmpty()) }
    var editedDate by remember(source.id, action, point?.id) { mutableStateOf(point?.dateText.orEmpty()) }
    var editedTime by remember(source.id, action, point?.id) { mutableStateOf(point?.timeText.orEmpty()) }
    var editedLocation by remember(source.id, action, point?.id) { mutableStateOf(point?.locationText.orEmpty()) }
    var editedPeople by remember(source.id, action, point?.id) { mutableStateOf(point?.peopleText.orEmpty()) }
    var editedOwner by remember(source.id, action, point?.id) { mutableStateOf(point?.ownerText.orEmpty()) }
    var editedReference by remember(source.id, action, point?.id) { mutableStateOf(point?.referenceNumber.orEmpty()) }
    var fieldsConfirmed by remember(source.id, action, point?.id) { mutableStateOf(false) }
    var showReject by remember { mutableStateOf(false) }
    var showDelegate by remember { mutableStateOf(false) }
    var customDelegate by remember { mutableStateOf<String?>(null) }
    var showAllEvidence by remember(source.id) { mutableStateOf(false) }

    var related by remember(source.id) { mutableStateOf<List<SourceSemanticHit>>(emptyList()) }
    var possibleUpdates by remember(source.id) { mutableStateOf<List<SourceSemanticHit>>(emptyList()) }
    var reasoning by remember(source.id) { mutableStateOf<ActionReasoningOutput?>(null) }
    var reasoningStatus by remember(source.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(source.id, actions.firstOrNull()) {
        actions.firstOrNull()?.let { first ->
            related = runCatching { searchRelated(first, source.id) }.getOrDefault(emptyList()).take(3)
            val revisionCue = Regex("\\b(revis|amend|replace|latest|updated|corrigendum|cancel|withdraw)\\w*\\b", RegexOption.IGNORE_CASE)
            possibleUpdates = related.filter { hit -> hit.source.capturedAt > source.capturedAt && revisionCue.containsMatchIn("${hit.source.title} ${hit.source.body}") }
        }
    }
    var reasoningRequested by remember(source.id, action) { mutableStateOf(false) }
    LaunchedEffect(source.id, action, point, evidence, instructionModelPath, related, reasoningRequested) {
        reasoning = null
        if (instructionModelPath == null) {
            reasoningStatus = "Local extraction shown. The optional Gemma instruction model is not installed."
            return@LaunchedEffect
        }
        if (!reasoningRequested || action.isNullOrBlank() || manual != null) { reasoningStatus = null; return@LaunchedEffect }
        reasoningStatus = "Reading cited evidence on this phone…"
        try {
            val conflictHints = possibleUpdates.map { hit -> "Newer source '${hit.source.title}' may revise this instruction; compare its fields." }
            val bundle = EvidenceBundleBuilder.build(source, listOf(action), related, conflictHints)
            val result = OnDeviceActionReasoner(instructionModelPath).reason(bundle, action)
            if (result.action.isNotBlank()) {
                reasoning = result; onReasoning(source.id, result)
                reasoningStatus = null
            } else reasoningStatus = "Gemma found no supported action for this candidate. Review the source manually."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            reasoningStatus = "Gemma could not produce valid cited output. The original fields remain available for review."
        }
    }

    fun finish(approvedAction: String) {
        handled = handled + approvedAction
        manual = null
    }
    fun editedPoint(): ActionPointEntity = ReviewApprovalPolicy.edit(requireNotNull(point), edited,
        editedDate, editedTime, editedLocation, editedPeople, editedOwner, editedReference, fieldsConfirmed)
    fun approveTo(assignee: String?) = perform {
        val candidate = if (editing) editedPoint() else requireNotNull(point)
        onApproveEdited(source, candidate, candidate.action, assignee)
        finish(action.orEmpty()); showDelegate = false; customDelegate = null
    }

    val groundedPoint = point
    val fields = remember(groundedPoint) { fieldRows(groundedPoint, groundedPoint?.evidence ?: evidence) }
    val highlights = fields.mapNotNull { f -> f.value.takeIf { f.state == FieldState.Verified } }.flatMap { v -> v.split(",", ";").map(String::trim) }
    val look = sourceLook(source.sourceType)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(2.dp))
            Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile(look.icon, look.color, size = 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(source.title, style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${look.label} · ${dueLabel(source.capturedAt).substringBefore(",")}", style = Hod.type.footnote, color = c.secondaryLabel)
                    }
                    val bucket = bucketLabel(source.triageBucket)
                    if (bucket == "Act") Pill(bucket, c.orange.copy(alpha = 0.14f), c.orangeText) else Pill(bucket, c.fill, c.label)
                }
                Pill("Intent · ${source.intentKind.replace('_', ' ').replaceFirstChar { it.uppercase() }}", c.tintSoft, c.tint)
                if (action == null) {
                    Text(if (pointState == null) "Loading action points…" else "No supported action points remain.", style = Hod.type.title3, color = c.label)
                    if (pointState != null) {
                        Text("Read the original before filing this item. You can enter an action yourself if extraction missed it.", style = Hod.type.subheadline, color = c.secondaryLabel)
                        HodButton("Enter an action manually", {
                            manual = ActionPointEntity(sourceId = source.id, action = "", evidence = evidence, confidence = 0f)
                        }, style = HodButtonStyle.Tinted, enabled = !busy)
                    }
                } else if (editing) {
                    OutlinedTextField(
                        value = edited, onValueChange = { edited = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, minLines = 2,
                        label = { Text("Action wording") }, textStyle = Hod.type.headline,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.tint, focusedLabelColor = c.tint, cursorColor = c.tint),
                    )
                    val correctionCandidates = remember(edited, evidence) { RecognitionCorrectionEngine.candidates(edited, evidence) }
                    if (correctionCandidates.size > 1) {
                        Text("Recognition candidates from cited evidence", style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel)
                        correctionCandidates.forEach { candidate ->
                            HodButton("Use ${candidate.text.take(72)}", { edited = candidate.text }, style = HodButtonStyle.Tinted, height = 40.dp, enabled = !busy)
                        }
                    }
                    OutlinedTextField(value = editedDate, onValueChange = { editedDate = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Due date (optional)") }, singleLine = true)
                    OutlinedTextField(value = editedTime, onValueChange = { editedTime = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Time (optional)") }, singleLine = true)
                    OutlinedTextField(value = editedLocation, onValueChange = { editedLocation = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Where (optional)") }, singleLine = true)
                    OutlinedTextField(value = editedPeople, onValueChange = { editedPeople = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("People / owner (optional)") }, singleLine = true)
                    OutlinedTextField(value = editedOwner, onValueChange = { editedOwner = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Responsible person / office (optional)") })
                    OutlinedTextField(value = editedReference, onValueChange = { editedReference = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Reference number (optional)") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fieldsConfirmed, onCheckedChange = { fieldsConfirmed = it }, enabled = !busy)
                        Text("I checked these fields and resolved the source warnings. Cleared dates remain unscheduled.", style = Hod.type.footnote, color = c.secondaryLabel)
                    }
                } else {
                    Text(action, style = Hod.type.title2, color = c.label)
                }
                if (remaining.size > 1) Text("Action ${actions.size - remaining.size + 1} of ${actions.size} from this item", style = Hod.type.footnote, color = c.secondaryLabel)
                source.priorityReasons?.takeIf { it.isNotBlank() }?.let { Text("Why: $it", style = Hod.type.subheadline, color = c.secondaryLabel) }
            }

            if (action != null) GroupedSection(header = "Details", footer = point?.ambiguity?.let { "Needs clarification: $it" }) {
                fields.forEachIndexed { i, f ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(f.label, style = Hod.type.body, color = c.label, modifier = Modifier.weight(1f))
                        Column(Modifier.weight(1.8f).pressable(enabled = !busy) {
                                // Field values are edited together with the
                                // action so uncertain extraction can never be
                                // silently accepted as fact.
                                editedDate = point?.dateText.orEmpty()
                                editedTime = point?.timeText.orEmpty()
                                editedLocation = point?.locationText.orEmpty()
                                editedPeople = point?.peopleText.orEmpty()
                                editedOwner = point?.ownerText.orEmpty(); editedReference = point?.referenceNumber.orEmpty()
                                edited = action.orEmpty()
                                editing = true
                            }) {
                            val valueText = when (f.state) {
                                FieldState.Missing -> "Not stated"
                                FieldState.Confirm, FieldState.Conflict -> "${f.value.orEmpty()} · confirm"
                                FieldState.Likely -> "${f.value.orEmpty()} · likely"
                                FieldState.Verified -> f.value.orEmpty()
                            }
                            Text(valueText, style = Hod.type.body, color = when (f.state) {
                                FieldState.Verified -> c.secondaryLabel
                                FieldState.Likely -> c.tint
                                FieldState.Confirm, FieldState.Conflict -> c.orangeText
                                FieldState.Missing -> c.tertiaryLabel
                            }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            f.confidence.takeIf { it > 0f }?.let { Text("${(it * 100).roundToInt()}% confidence", style = Hod.type.caption, color = c.secondaryLabel) }
                            f.citation?.let { Text("“${it.take(140)}”", style = Hod.type.caption, color = c.tertiaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                        when (f.state) {
                            FieldState.Verified -> Icon(Icons.Rounded.CheckCircle, "Found in source", tint = c.green, modifier = Modifier.size(22.dp))
                            FieldState.Likely -> Icon(Icons.Rounded.Error, "Likely; confirm before approval", tint = c.tint, modifier = Modifier.size(22.dp))
                            FieldState.Confirm, FieldState.Conflict -> Icon(Icons.Rounded.Error, "Needs confirmation: missing support or source conflict", tint = c.orange, modifier = Modifier.size(22.dp))
                            FieldState.Missing -> Icon(Icons.Rounded.Help, "Not stated in source", tint = c.chevron, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            if (groundedPoint != null) {
                Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.tint.copy(alpha = .08f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Evidence checks", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                    Text("${fields.count { it.state == FieldState.Verified }} verified · ${fields.count { it.state == FieldState.Likely }} likely · ${fields.count { it.state == FieldState.Confirm || it.state == FieldState.Conflict }} need confirmation", style = Hod.type.footnote, color = c.secondaryLabel)
                    Text("Each field shows its confidence and exact supporting sentence. Tap any value to edit and save it.", style = Hod.type.footnote, color = c.secondaryLabel)
                }
            }
            if (instructionModelPath != null && !action.isNullOrBlank() && manual == null && !reasoningRequested) {
                HodButton("Read this evidence with Gemma", { reasoningRequested = true }, style = HodButtonStyle.Tinted, enabled = !busy)
            }
            val dependencies = remember(actions) { ActionDependencyAnalyzer.analyze(actions) }
            if (dependencies.isNotEmpty()) {
                GroupedSection(header = "Action dependencies") {
                    dependencies.take(4).forEachIndexed { i, dependency ->
                        if (i > 0) RowDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text("${dependency.prerequisite}  →  ${dependency.dependent}", style = Hod.type.subheadline, color = c.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(dependency.reason, style = Hod.type.footnote, color = c.secondaryLabel)
                        }
                    }
                }
            }

            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FROM THE ${look.label.uppercase()}", style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
                    source.sourceUri?.let { uri ->
                        Text("Open", style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.padding(bottom = 6.dp).pressable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        })
                    }
                }
                Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvidenceText((if (showAllEvidence) evidence else point?.evidence ?: evidence).ifBlank { "No readable text. Open the original to review it." }, highlights, maxLines = if (showAllEvidence) Int.MAX_VALUE else 10)
                    if (evidence.lines().size > 10 || evidence.length > 600) Text(if (showAllEvidence) "Show less" else "Show all", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.pressable { showAllEvidence = !showAllEvidence })
                }
                if (related.isNotEmpty()) Text("Related: " + related.joinToString(" · ") { it.source.title }, style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
                if (possibleUpdates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.orange.copy(alpha = 0.12f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Possible update", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.orangeText)
                        Text("A newer related source contains revision language. Compare its date, time, venue, and owner before approving.", style = Hod.type.footnote, color = c.label)
                        possibleUpdates.forEach { hit -> Text("• ${hit.source.title}", style = Hod.type.footnote, color = c.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }

            reasoning?.let { r ->
                Column {
                    SectionHeader("Deeper understanding")
                    Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(r.action, style = Hod.type.headline, color = c.label)
                        Text("${r.citations.size} quote(s) from the source support this", style = Hod.type.footnote, color = c.secondaryLabel)
                        if (r.sourceIds.isNotEmpty()) Text("Sources: ${r.sourceIds.joinToString()}", style = Hod.type.footnote, color = c.secondaryLabel)
                        r.citations.take(3).forEach { Text("“$it”", style = Hod.type.subheadline, color = c.secondaryLabel) }
                        if (r.ambiguities.isNotEmpty()) Text("Check: " + r.ambiguities.joinToString("; "), style = Hod.type.footnote, color = c.orangeText)
                        if (r.conflicts.isNotEmpty()) Text("Conflict review required: " + r.conflicts.joinToString("; "), style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.redText)
                        if (action != null && r.action.isNotBlank()) HodButton("Review these suggested fields", {
                            edited = r.action; editedDate = r.date.orEmpty(); editedTime = r.time.orEmpty()
                            editedLocation = r.location.orEmpty(); editedPeople = r.people.joinToString("; "); editedOwner = r.owner.orEmpty()
                            fieldsConfirmed = false; editing = true
                        }, style = HodButtonStyle.Tinted, enabled = !busy)
                    }
                }
            }
            reasoningStatus?.let { Text(it, style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp)) }
            // Keep the last evidence row clear of the fixed action bar and the
            // gesture/navigation area.  Without this spacer the final card is
            // rendered underneath the buttons on compact phones.
            Spacer(Modifier.height(28.dp))
        }

        operationError?.let { Text(it, style = Hod.type.footnote, color = c.redText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
        if (action == null && pointState != null) Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
            HodButton(if (busy) "Saving…" else "Done with This Item", { perform { onDismissSource(source.id, "no_action") } }, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        }
        if (action != null) Column(Modifier.fillMaxWidth().background(c.groupedBackground).navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!point?.ambiguity.isNullOrBlank()) Text("Unconfirmed date/time warnings keep this task unscheduled. Edit and confirm to resolve them.", style = Hod.type.footnote, color = c.orangeText)
            HodButton(if (busy) "Saving…" else if (editing) "Save fields and approve" else "Approve", onClick = { approveTo(null) }, modifier = Modifier.fillMaxWidth(), enabled = !busy && (!editing || edited.isNotBlank()))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton(if (editing) "Save fields" else "Edit fields", onClick = {
                    if (editing) perform {
                        val updated = editedPoint()
                        onUpdateActionPoint(updated)
                        manual = null; editing = false
                    } else {
                        edited = action
                        editedDate = point?.dateText.orEmpty(); editedTime = point?.timeText.orEmpty(); editedLocation = point?.locationText.orEmpty(); editedPeople = point?.peopleText.orEmpty()
                        editedOwner = point?.ownerText.orEmpty(); editedReference = point?.referenceNumber.orEmpty()
                        editing = true
                    }
                }, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f), enabled = !busy && (!editing || edited.isNotBlank()))
                HodButton("Delegate", onClick = { showDelegate = true }, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f), enabled = !busy && (!editing || edited.isNotBlank()))
                HodButton("Reject", onClick = { showReject = true }, style = HodButtonStyle.DestructiveTinted, height = 46.dp, modifier = Modifier.weight(1f), enabled = !busy)
            }
        }
    }
    if (showReject && action != null) SheetDialog(onDismiss = { showReject = false }) {
        ActionSheetContent(
            title = "What's wrong with this suggestion?", message = null,
            actions = listOf(
                SheetAction("Not an action") { perform { onActionStatus(source.id, action, "rejected"); showReject = false; finish(action) } },
                SheetAction("Needs clarification — keep for later") { perform { onActionStatus(source.id, action, "needs_clarification"); showReject = false; handled = handled + action } },
                SheetAction("Not mine — file the whole item", destructive = true) { perform { onDismissSource(source.id, "not_mine"); showReject = false } },
            ),
            onCancel = { showReject = false },
        )
    }
    if (showDelegate && action != null) SheetDialog(onDismiss = { showDelegate = false }) {
        val finalAction = if (editing && edited.isNotBlank()) edited.trim() else action
        ActionSheetContent(
            title = "Delegate to", message = if (team.isEmpty()) "Add your team in Settings › Department to pick people here." else "A task is created with this person as owner. You send the message yourself.",
            actions = team.map { m -> SheetAction("${m.name}${m.role.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}") { approveTo(m.name) } } +
                SheetAction("Someone else…") { showDelegate = false; customDelegate = "" },
            onCancel = { showDelegate = false },
        )
    }
    customDelegate?.let { name ->
        val finalAction = if (editing && edited.isNotBlank()) edited.trim() else action.orEmpty()
        TextPromptDialog(title = "Delegate to", label = "Name", initial = name, confirm = "Delegate", onDismiss = { customDelegate = null }) { entered ->
            if (entered.isNotBlank() && action != null) approveTo(entered.trim())
            customDelegate = null
        }
    }
}

private fun fieldRows(point: ActionPointEntity?, evidence: String): List<FieldRowModel> {
    val people = point?.peopleText?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    val grounding = FieldGrounding.validate(evidence, point?.dateText, point?.timeText, point?.locationText, people, listOfNotNull(point?.ambiguity))
    val fallback = FieldEvidenceAttribution.assess(point?.action.orEmpty(), point?.dateText, point?.timeText, point?.locationText, people, point?.ownerText, point?.referenceNumber, evidence)
    val storedConfidence = FieldEvidenceAttribution.readConfidences(point?.fieldConfidenceJson)
    val storedCitations = FieldEvidenceAttribution.readCitations(point?.fieldCitationsJson)
    fun state(key: String, raw: String?, grounded: Boolean): FieldState {
        if (raw.isNullOrBlank()) return FieldState.Missing
        val confidence = storedConfidence[key]?.takeIf { it > 0f } ?: fallback[key]?.confidence ?: 0f
        if (point?.ambiguity?.contains("conflict", true) == true && (key == "date" || key == "time")) return FieldState.Conflict
        return when { grounded && confidence >= .8f -> FieldState.Verified; confidence >= .55f -> FieldState.Likely; else -> FieldState.Confirm }
    }
    fun row(key: String, label: String, raw: String?, grounded: Boolean) = FieldRowModel(key, label, raw, state(key, raw, grounded), storedConfidence[key]?.takeIf { it > 0f } ?: fallback[key]?.confidence ?: 0f, storedCitations[key] ?: fallback[key]?.quote)
    return listOf(
        row("action", "Action", point?.action, point?.action?.let { FieldGrounding.supportsAction(evidence, it) } == true),
        row("date", "Date", point?.dateText, grounding.date != null && point?.ambiguity.isNullOrBlank()),
        row("time", "Time", point?.timeText, grounding.time != null && point?.ambiguity.isNullOrBlank()),
        row("location", "Where", point?.locationText, grounding.location != null),
        row("people", "People", people.joinToString("; ").ifBlank { null }, grounding.people.size == people.size),
        row("owner", "Owner", point?.ownerText, point?.ownerText?.let { FieldGrounding.supportsField(evidence, it) } == true),
        row("reference", "Reference", point?.referenceNumber, point?.referenceNumber?.let { FieldGrounding.supportsField(evidence, it) } == true),
    )
}

/** Bottom-anchored dialog host for action sheets. */
@Composable
fun SheetDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().pressable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            // A sheet must remain fully reachable on short screens and when
            // the IME is visible. The old unconstrained wrapper let tall
            // action sheets extend past the display, clipping their actions.
            Box(
                Modifier.fillMaxWidth()
                    // Force a bounded viewport. heightIn alone lets an
                    // intrinsic child measure past the dialog window on
                    // gesture-navigation devices.
                    .fillMaxHeight(0.9f)
                    .heightIn(max = (configuration.screenHeightDp * 0.9f).dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .pressable(onClick = {}),
            ) { content() }
        }
    }
}
