package com.hoddesk.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoddesk.app.data.AuditEntryEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.privacy.ConsentScope
import com.hoddesk.app.privacy.PrivacyStore
import com.hoddesk.app.settings.formatMinute
import com.hoddesk.app.ui.components.EmptyState
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.IconTile
import com.hoddesk.app.ui.components.IosSwitch
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.NavBar
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.components.SectionHeader
import com.hoddesk.app.ui.components.SegmentedControl
import com.hoddesk.app.ui.components.SwitchRow
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles
import java.text.DateFormat
import java.util.Date

/** Pushed settings page frame: back bar, large title, scrolling grouped content. */
@Composable
fun SettingsPage(title: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Hod.colors.groupedBackground).statusBarsPadding()) {
        NavBar(backLabel, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(start = 18.dp, end = 18.dp, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(title, style = Hod.type.largeTitle, color = Hod.colors.label, modifier = Modifier.padding(start = 4.dp))
            content()
        }
    }
}

data class SettingsSummary(
    val teamCount: Int,
    val venueCount: Int,
    val blockSummary: String,
    val calendarName: String?,
    val workday: String,
    val slackPercent: Int,
    val clinicalCount: Int,
    val aiHealthy: Boolean,
    val aiLine: String,
    val versionLine: String,
    val setupDone: Int,
)

@Composable
fun SettingsHome(summary: SettingsSummary, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = Hod.colors
    SettingsPage("Settings", "Today", onBack) {
        Row(
            Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).pressable { onOpen("department") }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = c.onTint, modifier = Modifier.size(32.dp)) }
            Column(Modifier.weight(1f)) {
                Text("Department Profile", style = Hod.type.title3, color = c.label)
                Text("${summary.blockSummary} · ${summary.teamCount} team member(s)", style = Hod.type.subheadline, color = c.secondaryLabel)
            }
            com.hoddesk.app.ui.components.Chevron()
        }
        if (summary.setupDone < 4) Row(
            Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).pressable { onOpen("department") }.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(Icons.Rounded.PriorityHigh, c.orange)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Finish setup · ${summary.setupDone} of 4", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                Text("Add clinical time, your team, venues and the calendar to write to, so planning and delegation fit your department.", style = Hod.type.footnote, color = c.secondaryLabel)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.fill)) { Box(Modifier.fillMaxWidth(summary.setupDone / 4f).height(4.dp).background(c.orange)) }
            }
        }
        GroupedSection(header = "Work") {
            ListRow("Fixed Clinical Time", icon = Icons.Rounded.Apartment, iconColor = c.tint, value = summary.blockSummary, chevron = true, onClick = { onOpen("department") })
            RowDivider(58.dp)
            ListRow("Team", icon = Icons.Rounded.Group, iconColor = c.blue, value = summary.teamCount.toString(), chevron = true, onClick = { onOpen("department") })
            RowDivider(58.dp)
            ListRow("Who Outranks Whom", icon = Icons.Rounded.Sort, iconColor = c.indigo, chevron = true, onClick = { onOpen("department") })
        }
        GroupedSection(header = "Calendar", footer = "Plan My Day leaves ${summary.slackPercent}% of your working hours free for interruptions and never books over fixed clinical time.") {
            ListRow("Write Events To", icon = Icons.Rounded.CalendarMonth, iconColor = c.red, value = summary.calendarName ?: "Choose", valueColor = if (summary.calendarName == null) c.tint else c.secondaryLabel, chevron = true, onClick = { onOpen("calendar") })
            RowDivider(58.dp)
            ListRow("Working Hours", icon = Icons.Rounded.Schedule, iconColor = c.green, value = summary.workday, chevron = true, onClick = { onOpen("workday") })
            RowDivider(58.dp)
            ListRow("Keep Free", icon = Icons.Rounded.Schedule, iconColor = c.gray, value = "${summary.slackPercent}%", chevron = true, onClick = { onOpen("slack") })
        }
        GroupedSection(header = "Privacy & Security") {
            ListRow("Privacy & Data", icon = Icons.Rounded.Lock, iconColor = c.green, value = "On this phone", chevron = true, onClick = { onOpen("privacy") })
            RowDivider(58.dp)
            ListRow("Clinical Vault", icon = Icons.Rounded.Shield, iconColor = c.label, value = "${summary.clinicalCount}", chevron = true, onClick = { onOpen("vault") })
            RowDivider(58.dp)
            ListRow("Activity Log", icon = Icons.Rounded.History, iconColor = c.blue, chevron = true, onClick = { onOpen("activity") })
        }
        GroupedSection(header = "Intelligence") {
            ListRow("On-Device AI", icon = Icons.Rounded.AutoAwesome, iconColor = c.tint, value = summary.aiLine, valueColor = if (summary.aiHealthy) c.greenText else c.orangeText, chevron = true, onClick = { onOpen("ai") })
            RowDivider(58.dp)
            ListRow("Follow-ups", icon = Icons.Rounded.Schedule, iconColor = c.orange, value = "Waiting items", chevron = true, onClick = { onOpen("followups") })
            RowDivider(58.dp)
            ListRow("Secretary", icon = Icons.Rounded.Mic, iconColor = c.indigo, value = "Ask locally", chevron = true, onClick = { onOpen("secretary") })
            RowDivider(58.dp)
            ListRow("Control Centre", icon = Icons.Rounded.PriorityHigh, iconColor = c.orange, value = "All signals", chevron = true, onClick = { onOpen("control") })
            RowDivider(58.dp)
            ListRow("Evaluation lab", icon = Icons.Rounded.Assessment, iconColor = c.indigo, value = "Quality gates", chevron = true, onClick = { onOpen("evaluation") })
            RowDivider(58.dp)
            ListRow("How to Import", icon = Icons.Rounded.HelpOutline, iconColor = c.green, value = "WhatsApp, Gmail…", chevron = true, onClick = { onOpen("howto") })
        }
        Text(summary.versionLine + "\nNothing leaves this phone.", style = Hod.type.footnote, color = c.tertiaryLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun PrivacyPage(store: PrivacyStore, onBack: () -> Unit, onOpenActivity: () -> Unit, onDeleteAll: () -> Unit) {
    val c = Hod.colors
    var consent by remember { mutableStateOf(store.consentGranted) }
    var redact by remember { mutableStateOf(store.redactIdentifiers) }
    var scopes by remember { mutableStateOf(ConsentScope.values().associateWith { store.scopeGranted(it) }) }
    var retention by remember { mutableStateOf(store.retentionDays) }
    var customRetention by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    SettingsPage("Privacy & Data", "Settings", onBack) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTile(Icons.Rounded.Lock, c.green, size = 56.dp)
            Text("Everything stays on this phone", style = Hod.type.title3, color = c.label, textAlign = TextAlign.Center)
            Text("Stored encrypted. No cloud AI. Nothing is sent anywhere unless you share it yourself.", style = Hod.type.subheadline, color = c.secondaryLabel, textAlign = TextAlign.Center)
        }
        GroupedSection(header = "Imports", footer = if (consent) "A source that is off can't be imported. Its Capture tile shows a lock." else "Sensitive imports are blocked until you allow them.") {
            SwitchRow("Allow Sensitive Imports", consent, { consent = it; store.consentGranted = it }, subtitle = "Calls, messages, images and documents")
            listOf(
                Triple(ConsentScope.CALLS, "Call Recordings & Voice Notes", Icons.Rounded.Call to c.orange),
                Triple(ConsentScope.MESSAGES, "WhatsApp & Messages", Icons.Rounded.Chat to c.green),
                Triple(ConsentScope.SCREENSHOTS, "Screenshots & Camera", Icons.Rounded.PhotoCamera to c.gray),
                Triple(ConsentScope.DOCUMENTS, "Documents & Gmail", Icons.Rounded.Description to c.blue),
            ).forEach { (scope, label, look) ->
                RowDivider(58.dp)
                SwitchRow(label, consent && scopes[scope] == true, { v -> store.setScope(scope, v); scopes = scopes + (scope to v) }, icon = look.first, iconColor = look.second, enabled = consent)
            }
        }
        GroupedSection(header = "Protection", footer = "Messages that mention patients are kept in the Clinical Vault: never indexed for search, never sent to the reasoning model, and never shown in tasks or calendar events.") {
            SwitchRow("Hide Phone Numbers & Emails", redact, { redact = it; store.redactIdentifiers = it })
            RowDivider()
            ListRow("Keep Patient Details Out of Search", value = "Always on") { Icon(Icons.Rounded.Lock, "Always on", tint = c.greenText, modifier = Modifier.size(18.dp)) }
        }
        val presets = listOf(30, 180, 365)
        val selected = presets.indexOf(retention).let { if (it < 0) 3 else it }
        GroupedSection(header = "Retention Policy", footer = "Current policy: $retention days. Cleanup is always explicit — nothing is deleted automatically, and approved tasks keep their history.") {
            Box(Modifier.padding(12.dp)) {
                SegmentedControl(listOf("30 days", "6 months", "1 year", "Custom"), selected, { i ->
                    if (i < 3) { retention = presets[i]; store.retentionDays = presets[i] } else customRetention = true
                })
            }
        }
        GroupedSection {
            ListRow("Activity Log & Export", titleColor = c.tint, onClick = onOpenActivity)
            RowDivider()
            ListRow("Delete All Data…", titleColor = c.redText, onClick = { confirmDelete = true })
        }
        GroupedSection(header = "Data boundary", footer = "The app keeps source evidence, embeddings, corrections and audit history on this phone. AI suggestions always require your approval before a task or calendar write.") {
            ListRow("Search & reasoning", value = "On-device only", icon = Icons.Rounded.Lock, iconColor = c.green, valueColor = c.greenText)
            RowDivider(58.dp)
            ListRow("Clinical material", value = "Quarantined", icon = Icons.Rounded.Shield, iconColor = c.green, valueColor = c.greenText)
            RowDivider(58.dp)
            ListRow("Recovery", subtitle = "Self-check and rebuild status remain visible for review before using AI suggestions.", value = "Inspectable", icon = Icons.Rounded.History, iconColor = c.blue, valueColor = c.greenText)
        }
    }
    if (customRetention) TextPromptDialog("Keep Items For", "Days (1–3650)", retention.toString(), "Save", onDismiss = { customRetention = false }, numeric = true) {
        it.toIntOrNull()?.let { d -> store.retentionDays = d; retention = store.retentionDays }
        customRetention = false
    }
    if (confirmDelete) {
        TextPromptDialog(
            "Delete All Data?", "Type DELETE to confirm", "", "Delete", onDismiss = { confirmDelete = false },
            message = "Removes every source, task, event, search index entry and reasoning record from this phone. Events already written to your Android calendar stay there. This cannot be undone.",
        ) { if (it.trim() == "DELETE") { onDeleteAll(); confirmDelete = false } }
    }
}

data class AiStatus(
    val healthy: Boolean,
    val headline: String,
    val detail: String,
    val searchLine: String,
    val speechReady: Boolean,
    val reasoningInstalled: Boolean,
    val reasoningProgress: Int?,
    val technical: List<Pair<String, String>>,
    val busy: String?,
    val correctionCount: Int = 0,
    val clinicalCount: Int = 0,
)

@Composable
fun AiPage(status: AiStatus, onBack: () -> Unit, onSelfCheck: () -> Unit, onRebuild: () -> Unit, onInstallReasoning: () -> Unit, snapshotSummary: String? = null, onCreateSnapshot: (() -> Unit)? = null, onRestoreSnapshot: (() -> Unit)? = null, onResetLearning: (() -> Unit)? = null) {
    val c = Hod.colors
    var showTech by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    SettingsPage("On-Device AI", "Settings", onBack) {
        Row(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(if (status.healthy) Icons.Rounded.Check else Icons.Rounded.PriorityHigh, if (status.healthy) c.green else c.orange, size = 52.dp)
            Column { Text(status.headline, style = Hod.type.headline, color = c.label); Text(status.detail, style = Hod.type.subheadline, color = c.secondaryLabel) }
        }
        GroupedSection(header = "What the App Can Do", footer = "AI only suggests. Nothing becomes a task or calendar event until you approve it.") {
            ListRow("Smart Search", subtitle = status.searchLine, icon = Icons.Rounded.Search, iconColor = c.tint, value = if (status.healthy) "Ready" else "Check", valueColor = if (status.healthy) c.greenText else c.orangeText)
            RowDivider(58.dp)
            ListRow("Speech to Text", subtitle = "Tamil-first, English mixed", icon = Icons.Rounded.Mic, iconColor = c.orange, value = if (status.speechReady) "Ready" else "Downloads on first use", valueColor = if (status.speechReady) c.greenText else c.secondaryLabel)
            RowDivider(58.dp)
            ListRow("Read Text from Images", subtitle = "Screenshots, photos, scans", icon = Icons.Rounded.DocumentScanner, iconColor = c.gray, value = "Ready", valueColor = c.greenText)
            RowDivider(58.dp)
            ListRow("Deeper Understanding", subtitle = "Reads whole letters and links related messages. Large download.", icon = Icons.Rounded.AutoAwesome, iconColor = c.indigo) {
                when {
                    status.reasoningInstalled -> Text("Installed", style = Hod.type.subheadline, color = c.greenText)
                    status.reasoningProgress != null -> Text("${status.reasoningProgress}%", style = Hod.type.subheadline, color = c.secondaryLabel)
                    else -> HodButton("Get", onInstallReasoning, style = HodButtonStyle.Tinted, height = 32.dp)
                }
            }
        }
        GroupedSection(header = "Maintenance", footer = status.busy ?: "Technical details list model versions and index checks for IT or security review.") {
            ListRow("Run Self-Check Now", titleColor = c.tint, onClick = onSelfCheck)
            RowDivider()
            ListRow("Rebuild Search Index", titleColor = c.tint, onClick = onRebuild)
            RowDivider()
            if (onCreateSnapshot != null) {
                ListRow("Save index snapshot", subtitle = snapshotSummary ?: "No snapshot saved", titleColor = c.tint, onClick = onCreateSnapshot)
                RowDivider()
            }
            if (onRestoreSnapshot != null && snapshotSummary != null) {
                ListRow("Restore previous snapshot", subtitle = "Rollback vectors atomically", titleColor = c.orangeText, onClick = onRestoreSnapshot)
                RowDivider()
            }
            ListRow("Rebuild safety", subtitle = "Self-check exposes stale or invalid vectors before you rely on search.", icon = Icons.Rounded.History, iconColor = c.blue, value = "Inspectable", valueColor = c.greenText)
            RowDivider(58.dp)
            ListRow("Clinical boundary", subtitle = "Clinical items stay outside search, reasoning and calendar output.", icon = Icons.Rounded.Shield, iconColor = c.green, value = "${status.clinicalCount} protected", valueColor = c.greenText)
            RowDivider(58.dp)
            ListRow("Corrections learned", subtitle = "Your approved edits improve future extraction and ranking on this phone.", icon = Icons.Rounded.Check, iconColor = c.indigo, value = status.correctionCount.toString(), valueColor = c.secondaryLabel)
            RowDivider()
            if (onResetLearning != null) {
                ListRow("Reset learned preferences", subtitle = "Remove local ranking corrections", titleColor = c.orangeText, onClick = { confirmReset = true })
                RowDivider()
            }
            ListRow("Technical Details", value = if (showTech) "Hide" else null, chevron = !showTech, onClick = { showTech = !showTech })
            if (showTech) status.technical.forEach { (k, v) ->
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(k, style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.weight(1f))
                    Text(v, style = Hod.type.footnote, color = c.label, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
                }
            }
        }
    }
    if (confirmReset) TextPromptDialog("Reset learned preferences?", "Type RESET to confirm", "", "Reset", onDismiss = { confirmReset = false }, message = "This removes only local correction history. Imported sources and tasks remain unchanged.") {
        if (it.trim().equals("RESET", ignoreCase = true)) { onResetLearning?.invoke(); confirmReset = false }
    }
}

@Composable
fun ActivityPage(entries: List<AuditEntryEntity>, onBack: () -> Unit, onExport: () -> Unit) {
    val c = Hod.colors
    SettingsPage("Activity Log", "Settings", onBack) {
        Text("An append-only record of imports, approvals, scheduling, completion and deletion. Export creates a copy only when you choose a destination.", style = Hod.type.subheadline, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 4.dp))
        HodButton("Export for Review", onExport, style = HodButtonStyle.Tinted, modifier = Modifier.fillMaxWidth(), enabled = entries.isNotEmpty())
        if (entries.isEmpty()) Box(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface)) { EmptyState("No activity yet", "Actions you take will be recorded here.") }
        entries.groupBy { DateFormat.getDateInstance(DateFormat.FULL).format(Date(it.createdAt)) }.forEach { (day, list) ->
            GroupedSection(header = day) {
                list.forEachIndexed { i, e ->
                    if (i > 0) RowDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(humanAudit(e), style = Hod.type.body, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${e.entityType.replaceFirstChar(Char::uppercase)} #${e.entityId} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.createdAt))}", style = Hod.type.footnote, color = c.secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun HowToPage(onBack: () -> Unit) {
    SettingsPage("How to Import", "Settings", onBack) {
        val c = Hod.colors
        listOf(
            Triple("WhatsApp", Icons.Rounded.Chat to c.green, "In the chat, tap ⋮ › More › Export chat › Without media. If WhatsApp refuses, turn off Advanced Chat Privacy for that chat first. Then choose the ZIP or .txt with Capture › WhatsApp, or share it straight to HOD Desk."),
            Triple("Gmail attachments", Icons.Rounded.Description to c.blue, "Open the attachment in Gmail, tap Share, and pick Psychiatry HOD Desk. PDFs and Word files keep their page references."),
            Triple("Screenshots & photos", Icons.Rounded.PhotoCamera to c.gray, "Use Capture › Screenshot for an image you already have, or Capture › Camera for a letter or notice board. Text is read on the phone; the original image stays linked."),
            Triple("Calls & voice notes", Icons.Rounded.Call to c.orange, "Use Capture › Call for a saved recording (M4A, AAC, WAV), or Speak a Note to dictate. Tamil and English are transcribed on the phone."),
        ).forEach { (title, look, body) ->
            GroupedSection {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(look.first, look.second)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = Hod.type.headline, color = c.label)
                        Text(body, style = Hod.type.subheadline, color = c.secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun VaultPage(items: List<SourceEntity>, onBack: () -> Unit) {
    val c = Hod.colors
    var open by remember { mutableStateOf<Long?>(null) }
    SettingsPage("Clinical Vault", "Settings", onBack) {
        Text("Items that mention patients or special categories. They are not searchable, not sent to any AI model, and never copied into tasks or calendar events.", style = Hod.type.subheadline, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 4.dp))
        if (items.isEmpty()) Box(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface)) { EmptyState("Vault is empty", "Clinical material you import will be kept here.") }
        else GroupedSection {
            items.forEachIndexed { i, s ->
                if (i > 0) RowDivider()
                Column(Modifier.fillMaxWidth().pressable { open = if (open == s.id) null else s.id }.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.title, style = Hod.type.body, color = c.label)
                    Text("${sourceLook(s.sourceType).label} · ${dueLabel(s.capturedAt).substringBefore(",")} · ${if (s.sensitivity == "T4") "special category" else "clinical"}", style = Hod.type.footnote, color = c.secondaryLabel)
                    if (open == s.id) Text(cleanEvidence(s.body), style = Hod.type.subheadline, color = c.label)
                }
            }
        }
    }
}

fun workdayLine(start: Int, end: Int): String = "${formatMinute(start)} – ${formatMinute(end)}"
