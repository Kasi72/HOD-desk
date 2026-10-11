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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.hoddesk.app.reasoning.ActionReasoningOutput
import com.hoddesk.app.reasoning.FieldGrounding
import com.hoddesk.app.reasoning.OnDeviceActionReasoner
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

private enum class FieldState { Verified, Confirm, Missing }
private data class FieldRowModel(val label: String, val value: String?, val state: FieldState)

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
    onUpdateActionPoint: (ActionPointEntity) -> Unit,
    onApproveEdited: (SourceEntity, ActionPointEntity?, String, String?) -> Unit,
    onApprove: (source: SourceEntity, action: String, assignee: String?) -> Unit,
    onActionStatus: (sourceId: Long, action: String, status: String) -> Unit,
    onDismissSource: (sourceId: Long, reason: String) -> Unit,
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
    onUpdateActionPoint: (ActionPointEntity) -> Unit,
    onApproveEdited: (SourceEntity, ActionPointEntity?, String, String?) -> Unit,
    onApprove: (SourceEntity, String, String?) -> Unit,
    onActionStatus: (Long, String, String) -> Unit,
    onDismissSource: (Long, String) -> Unit,
) {
    val c = Hod.colors
    val context = LocalContext.current
    val actionPoints by remember(source.id) { observeActionPoints(source.id) }.collectAsState(initial = emptyList())
    val evidence = remember(source.id, source.body) { cleanEvidence(source.body) }
    val pending = actionPoints.filter { it.status == "needs_review" || it.status == "needs_clarification" }
    val fallback = remember(source.id) { ActionPointExtractor().extract(evidence).map { it.action }.filter { it.isNotBlank() } }
    val baseActions = when {
        actionPoints.isNotEmpty() -> pending.map { it.action }
        fallback.isNotEmpty() -> fallback
        else -> listOf(evidence.lineSequence().firstOrNull { it.isNotBlank() } ?: source.title)
    }
    var ranked by remember(source.id) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(source.id, baseActions) { ranked = if (baseActions.size > 1) runCatching { rankActions(baseActions) }.getOrNull() else null }
    val actions = (ranked?.filter { it in baseActions } ?: baseActions)
    var handled by remember(source.id) { mutableStateOf(setOf<String>()) }
    val remaining = actions.filter { it !in handled }
    val action = remaining.firstOrNull()
    val point = actionPoints.firstOrNull { it.action == action }

    var editing by remember(source.id, action) { mutableStateOf(false) }
    var edited by remember(source.id, action) { mutableStateOf(action.orEmpty()) }
    var editedDate by remember(source.id, action, point?.id) { mutableStateOf(point?.dateText.orEmpty()) }
    var editedTime by remember(source.id, action, point?.id) { mutableStateOf(point?.timeText.orEmpty()) }
    var editedLocation by remember(source.id, action, point?.id) { mutableStateOf(point?.locationText.orEmpty()) }
    var editedPeople by remember(source.id, action, point?.id) { mutableStateOf(point?.peopleText.orEmpty()) }
    var showReject by remember { mutableStateOf(false) }
    var showDelegate by remember { mutableStateOf(false) }
    var customDelegate by remember { mutableStateOf<String?>(null) }
    var showAllEvidence by remember(source.id) { mutableStateOf(false) }

    var related by remember(source.id) { mutableStateOf<List<SourceSemanticHit>>(emptyList()) }
    var reasoning by remember(source.id) { mutableStateOf<ActionReasoningOutput?>(null) }
    var reasoningStatus by remember(source.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(source.id, actions.firstOrNull()) {
        actions.firstOrNull()?.let { first -> related = runCatching { searchRelated(first, source.id) }.getOrDefault(emptyList()).take(3) }
    }
    LaunchedEffect(source.id, instructionModelPath, related) {
        if (instructionModelPath == null) return@LaunchedEffect
        reasoningStatus = "Reading the whole letter on this phone…"
        val bundle = buildString {
            append("PRIMARY SOURCE [").append(source.sourceType).append("]\n").append(source.body).append("\n")
            append("EXTRACTED ACTION CANDIDATES\n"); actions.forEach { append("- ").append(it).append("\n") }
            if (related.isNotEmpty()) {
                append("RELATED SOURCES RETRIEVED BY EMBEDDINGGEMMA\n")
                related.forEach { hit -> append("[").append(hit.source.sourceType).append("] ").append(hit.source.title).append("\n").append(hit.source.body.take(2200)).append("\n") }
            }
        }
        runCatching { OnDeviceActionReasoner(instructionModelPath).reason(bundle.take(24000)) }
            .onSuccess { reasoning = it; onReasoning(source.id, it); reasoningStatus = null }
            .onFailure { reasoningStatus = "Deeper understanding unavailable for this item. Review manually." }
    }

    fun finish(approvedAny: Boolean, approvedAction: String) {
        handled = handled + approvedAction
        if (!approvedAny && remaining.size <= 1) onDismissSource(source.id, "no_action")
    }

    val fields = remember(point, evidence) { fieldRows(point, evidence) }
    val highlights = fields.mapNotNull { f -> f.value.takeIf { f.state == FieldState.Verified } }.flatMap { v -> v.split(",").map(String::trim) }
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
                if (action == null) {
                    Text("No action points left in this item.", style = Hod.type.title3, color = c.label)
                } else if (editing) {
                    OutlinedTextField(
                        value = edited, onValueChange = { edited = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, minLines = 2,
                        label = { Text("Action wording") }, textStyle = Hod.type.headline,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.tint, focusedLabelColor = c.tint, cursorColor = c.tint),
                    )
                    OutlinedTextField(value = editedDate, onValueChange = { editedDate = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Due date (optional)") }, singleLine = true)
                    OutlinedTextField(value = editedTime, onValueChange = { editedTime = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Time (optional)") }, singleLine = true)
                    OutlinedTextField(value = editedLocation, onValueChange = { editedLocation = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("Where (optional)") }, singleLine = true)
                    OutlinedTextField(value = editedPeople, onValueChange = { editedPeople = it }, modifier = Modifier.fillMaxWidth(), shape = Squircles.small, label = { Text("People / owner (optional)") }, singleLine = true)
                } else {
                    Text(action, style = Hod.type.title2, color = c.label)
                }
                if (remaining.size > 1) Text("Action ${actions.size - remaining.size + 1} of ${actions.size} from this item", style = Hod.type.footnote, color = c.secondaryLabel)
                source.priorityReasons?.takeIf { it.isNotBlank() }?.let { Text("Why: $it", style = Hod.type.subheadline, color = c.secondaryLabel) }
            }

            if (action != null) GroupedSection(header = "Details", footer = point?.ambiguity?.let { "Needs clarification: $it" }) {
                fields.forEachIndexed { i, f ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(f.label, style = Hod.type.body, color = c.label, modifier = Modifier.weight(1f))
                        Text(
                            when (f.state) { FieldState.Missing -> "Not stated"; FieldState.Confirm -> f.value.orEmpty(); FieldState.Verified -> f.value.orEmpty() },
                            style = Hod.type.body,
                            color = when (f.state) { FieldState.Verified -> c.secondaryLabel; FieldState.Confirm -> c.orangeText; FieldState.Missing -> c.tertiaryLabel },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f, fill = false).pressable {
                                // Field values are edited together with the
                                // action so uncertain extraction can never be
                                // silently accepted as fact.
                                editedDate = point?.dateText.orEmpty()
                                editedTime = point?.timeText.orEmpty()
                                editedLocation = point?.locationText.orEmpty()
                                editedPeople = point?.peopleText.orEmpty()
                                edited = action.orEmpty()
                                editing = true
                            },
                        )
                        when (f.state) {
                            FieldState.Verified -> Icon(Icons.Rounded.CheckCircle, "Found in source", tint = c.green, modifier = Modifier.size(22.dp))
                            FieldState.Confirm -> Icon(Icons.Rounded.Error, "Confirm: not found word for word in source", tint = c.orange, modifier = Modifier.size(22.dp))
                            FieldState.Missing -> Icon(Icons.Rounded.Help, "Not stated in source", tint = c.chevron, modifier = Modifier.size(22.dp))
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
                    EvidenceText(evidence.ifBlank { "No readable text. Open the original to review it." }, highlights, maxLines = if (showAllEvidence) Int.MAX_VALUE else 10)
                    if (evidence.lines().size > 10 || evidence.length > 600) Text(if (showAllEvidence) "Show less" else "Show all", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.pressable { showAllEvidence = !showAllEvidence })
                }
                if (related.isNotEmpty()) Text("Related: " + related.joinToString(" · ") { it.source.title }, style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
            }

            reasoning?.let { r ->
                Column {
                    SectionHeader("Deeper understanding")
                    Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(r.action, style = Hod.type.headline, color = c.label)
                        Text("${r.citations.size} quote(s) from the source support this", style = Hod.type.footnote, color = c.secondaryLabel)
                        r.citations.take(3).forEach { Text("“$it”", style = Hod.type.subheadline, color = c.secondaryLabel) }
                        if (r.ambiguities.isNotEmpty()) Text("Check: " + r.ambiguities.joinToString("; "), style = Hod.type.footnote, color = c.orangeText)
                        if (action != null && r.action.isNotBlank() && r.action != action) Text("Use this wording", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.pressable { edited = r.action; editing = true })
                    }
                }
            }
            reasoningStatus?.let { Text(it, style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp)) }
            // Keep the last evidence row clear of the fixed action bar and the
            // gesture/navigation area.  Without this spacer the final card is
            // rendered underneath the buttons on compact phones.
            Spacer(Modifier.height(28.dp))
        }

        if (action == null) Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
            HodButton("Done with This Item", { onDismissSource(source.id, "no_action") }, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth())
        }
        if (action != null) Column(Modifier.fillMaxWidth().background(c.groupedBackground).navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val finalAction = if (editing && edited.isNotBlank()) edited.trim() else action
            HodButton(if (editing) "Save fields and approve" else "Approve", onClick = {
                if (editing && point != null) onUpdateActionPoint(point.copy(action = finalAction, dateText = editedDate, timeText = editedTime, locationText = editedLocation, peopleText = editedPeople))
                if (editing) onApproveEdited(source, point?.copy(action = finalAction, dateText = editedDate, timeText = editedTime, locationText = editedLocation, peopleText = editedPeople), finalAction, null)
                else onApprove(source, finalAction, null)
                finish(true, action)
            }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton(if (editing) "Save fields" else "Edit fields", onClick = {
                    if (editing) {
                        if (point != null) onUpdateActionPoint(point.copy(action = edited.trim(), dateText = editedDate, timeText = editedTime, locationText = editedLocation, peopleText = editedPeople))
                        editing = false
                    } else {
                        edited = action
                        editedDate = point?.dateText.orEmpty(); editedTime = point?.timeText.orEmpty(); editedLocation = point?.locationText.orEmpty(); editedPeople = point?.peopleText.orEmpty()
                        editing = true
                    }
                }, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Delegate", onClick = { showDelegate = true }, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Reject", onClick = { showReject = true }, style = HodButtonStyle.DestructiveTinted, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showReject && action != null) SheetDialog(onDismiss = { showReject = false }) {
        ActionSheetContent(
            title = "What's wrong with this suggestion?", message = null,
            actions = listOf(
                SheetAction("Not an action") { onActionStatus(source.id, action, "rejected"); showReject = false; finish(false, action) },
                SheetAction("Needs clarification — keep for later") { onActionStatus(source.id, action, "needs_clarification"); showReject = false; handled = handled + action },
                SheetAction("Not mine — file the whole item", destructive = true) { onDismissSource(source.id, "not_mine"); showReject = false },
            ),
            onCancel = { showReject = false },
        )
    }
    if (showDelegate && action != null) SheetDialog(onDismiss = { showDelegate = false }) {
        val finalAction = if (editing && edited.isNotBlank()) edited.trim() else action
        ActionSheetContent(
            title = "Delegate to", message = if (team.isEmpty()) "Add your team in Settings › Department to pick people here." else "A task is created with this person as owner. You send the message yourself.",
            actions = team.map { m -> SheetAction("${m.name}${m.role.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}") { onApprove(source, finalAction, m.name); showDelegate = false; finish(true, action) } } +
                SheetAction("Someone else…") { showDelegate = false; customDelegate = "" },
            onCancel = { showDelegate = false },
        )
    }
    customDelegate?.let { name ->
        val finalAction = if (editing && edited.isNotBlank()) edited.trim() else action.orEmpty()
        TextPromptDialog(title = "Delegate to", label = "Name", initial = name, confirm = "Delegate", onDismiss = { customDelegate = null }) { entered ->
            if (entered.isNotBlank() && action != null) { onApprove(source, finalAction, entered.trim()); finish(true, action) }
            customDelegate = null
        }
    }
}

private fun fieldRows(point: ActionPointEntity?, evidence: String): List<FieldRowModel> {
    val due = listOfNotNull(point?.dateText, point?.timeText).joinToString(", ").ifBlank { null }
    val people = point?.peopleText?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    val grounding = FieldGrounding.validate(evidence, point?.dateText, point?.timeText, point?.locationText, people, emptyList())
    fun state(raw: String?, grounded: Boolean) = when { raw.isNullOrBlank() -> FieldState.Missing; grounded -> FieldState.Verified; else -> FieldState.Confirm }
    val dueGrounded = (point?.dateText == null || grounding.date != null) && (point?.timeText == null || grounding.time != null)
    return listOf(
        FieldRowModel("Due", due, state(due, dueGrounded)),
        FieldRowModel("Where", point?.locationText, state(point?.locationText, grounding.location != null)),
        FieldRowModel("People", people.joinToString(", ").ifBlank { null }, state(people.joinToString(), grounding.people.size == people.size)),
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
