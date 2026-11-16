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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.NotificationsActive
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
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import com.hoddesk.app.ui.components.SearchField
import com.hoddesk.app.ui.components.SettingsHeaderCard
import com.hoddesk.app.ui.theme.Neu
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

/**
 * Pushed settings page frame, iOS style: back bar, large title that collapses into the bar as the
 * page scrolls (with a hairline under the bar), and grouped content.
 */
@Composable
fun SettingsPage(title: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    val c = Hod.colors
    val scroll = rememberScrollState()
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val collapsed by remember { derivedStateOf { scroll.value > threshold } }
    val barTitle by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "barTitle")
    Column(Modifier.fillMaxSize().background(c.groupedBackground).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth()) {
            NavBar(backLabel, onBack)
            Text(
                title, style = Hod.type.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).alpha(barTitle),
            )
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).alpha(barTitle).background(c.separator))
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(title, style = Hod.type.largeTitle, color = c.label, modifier = Modifier.padding(start = 4.dp).alpha(1f - barTitle))
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
    val remindersLine: String = "Off",
    val backupLine: String = "Never",
    val autoLockLine: String = "5 min",
)

/** One row on the Settings home screen. [keywords] let search find it by what it does. */
private data class SettingsEntry(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val route: String,
    val value: String? = null,
    val valueColor: Color? = null,
    val keywords: String = "",
)

private data class SettingsGroup(val header: String?, val entries: List<SettingsEntry>, val footer: String? = null)

@Composable
private fun settingsGroups(summary: SettingsSummary): List<SettingsGroup> {
    val c = Hod.colors
    return listOf(
        SettingsGroup("Department", listOf(
            SettingsEntry("Fixed Clinical Time", Icons.Rounded.Apartment, c.tint, "department", summary.blockSummary, keywords = "opd ect teaching ward blocks timetable"),
            SettingsEntry("Team", Icons.Rounded.Group, c.blue, "department", summary.teamCount.toString(), keywords = "delegate staff residents people"),
            SettingsEntry("Venues & Travel", Icons.Rounded.Place, c.green, "department", summary.venueCount.toString(), keywords = "travel time collectorate location"),
            SettingsEntry("Who Outranks Whom", Icons.Rounded.Sort, c.indigo, "department", keywords = "authority priority urgency dme"),
        )),
        SettingsGroup("Planning", listOf(
            SettingsEntry("Write Events To", Icons.Rounded.CalendarMonth, c.red, "calendar", summary.calendarName ?: "Choose", if (summary.calendarName == null) c.tint else null, "calendar google android"),
            SettingsEntry("Working Hours", Icons.Rounded.AccessTime, c.orange, "workday", summary.workday, keywords = "workday start end office"),
            SettingsEntry("Keep Free", Icons.Rounded.HourglassBottom, c.gray, "slack", "${summary.slackPercent}%", keywords = "slack buffer interruptions plan my day"),
        ), footer = "Plan My Day leaves ${summary.slackPercent}% of your working hours free for interruptions and never books over fixed clinical time."),
        SettingsGroup(null, listOf(
            SettingsEntry("Reminders", Icons.Rounded.NotificationsActive, c.red, "reminders", summary.remindersLine, if (summary.remindersLine == "Blocked") c.orangeText else null, "notifications morning brief due alerts"),
        )),
        SettingsGroup("Privacy & Security", listOf(
            SettingsEntry("Auto-Lock", Icons.Rounded.LockClock, c.blue, "autolock", summary.autoLockLine, keywords = "fingerprint face lock screen recent apps screenshot privacy"),
            SettingsEntry("Privacy & Data", Icons.Rounded.PrivacyTip, c.indigo, "privacy", keywords = "consent imports redact retention delete"),
            SettingsEntry("Clinical Vault", Icons.Rounded.Shield, c.gray, "vault", "${summary.clinicalCount}", keywords = "patients clinical protected"),
            SettingsEntry("Activity Log", Icons.Rounded.History, c.purple, "activity", keywords = "audit history export"),
        )),
        SettingsGroup(null, listOf(
            SettingsEntry("Backup & Restore", Icons.Rounded.Backup, c.tint, "backup", summary.backupLine, if (summary.backupLine == "Never") c.orangeText else null, "export import encrypted password new phone"),
        )),
        SettingsGroup("Assistant", listOf(
            SettingsEntry("Secretary", Icons.Rounded.Mic, c.purple, "secretary", keywords = "voice ask command"),
            SettingsEntry("Follow-ups", Icons.AutoMirrored.Rounded.Reply, c.orange, "followups", keywords = "waiting chase pending delegated"),
            SettingsEntry("Control Centre", Icons.Rounded.Tune, c.gray, "control", keywords = "signals overview dashboard"),
        )),
        SettingsGroup("On-Device AI", listOf(
            SettingsEntry("On-Device AI", Icons.Rounded.AutoAwesome, c.tint, "ai", summary.aiLine, if (summary.aiHealthy) c.greenText else c.orangeText, "gemma model search index download"),
            SettingsEntry("Evaluation Lab", Icons.Rounded.Science, c.indigo, "evaluation", keywords = "quality test accuracy"),
        )),
        SettingsGroup(null, listOf(
            SettingsEntry("How to Import", Icons.Rounded.HelpOutline, c.blue, "howto", keywords = "help whatsapp gmail share screenshot call"),
        )),
    )
}

@Composable
fun SettingsHome(summary: SettingsSummary, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = Hod.colors
    var query by remember { mutableStateOf("") }
    val groups = settingsGroups(summary)
    SettingsPage("Settings", "Today", onBack) {
        SearchField(query, { query = it }, "Search")
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hits = groups.flatMap { it.entries }.filter { e -> e.title.contains(q, ignoreCase = true) || e.keywords.contains(q, ignoreCase = true) }
                .distinctBy { it.title }
            if (hits.isEmpty()) Text("No Results for “$q”", style = Hod.type.body, color = c.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            else SettingsEntries(hits, onOpen)
            return@SettingsPage
        }
        // Department profile, like the account card at the top of iOS Settings.
        Row(
            Modifier.fillMaxWidth().then(Neu.card(shape = Squircles.card)).clip(Squircles.card).background(c.surface).pressable { onOpen("department") }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(c.tint), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = c.onTint, modifier = Modifier.size(32.dp)) }
            Column(Modifier.weight(1f)) {
                Text("Department Profile", style = Hod.type.title3, color = c.label)
                Text("${summary.teamCount} in team · ${summary.venueCount} venues · clinical time", style = Hod.type.subheadline, color = c.secondaryLabel)
            }
            com.hoddesk.app.ui.components.Chevron()
        }
        if (summary.setupDone < 4) GroupedSection {
            Row(
                Modifier.fillMaxWidth().pressable { onOpen("department") }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(Icons.Rounded.PriorityHigh, c.orange)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Finish Setting Up", style = Hod.type.body, color = c.label, modifier = Modifier.weight(1f))
                        Text("${summary.setupDone} of 4", style = Hod.type.body, color = c.secondaryLabel)
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.fill)) {
                        Box(Modifier.fillMaxWidth(summary.setupDone / 4f).height(4.dp).clip(CircleShape).background(c.orange))
                    }
                }
                com.hoddesk.app.ui.components.Chevron()
            }
        }
        groups.forEach { group -> SettingsEntries(group.entries, onOpen, group.header, group.footer) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary.versionLine, style = Hod.type.footnote, color = c.tertiaryLabel, textAlign = TextAlign.Center)
            Text("Nothing leaves this phone.", style = Hod.type.footnote, color = c.tertiaryLabel, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsEntries(entries: List<SettingsEntry>, onOpen: (String) -> Unit, header: String? = null, footer: String? = null) {
    GroupedSection(header = header, footer = footer) {
        entries.forEachIndexed { i, e ->
            if (i > 0) RowDivider(58.dp)
            ListRow(
                e.title, icon = e.icon, iconColor = e.color, value = e.value, valueColor = e.valueColor ?: Hod.colors.secondaryLabel,
                chevron = true, onClick = { onOpen(e.route) },
            )
        }
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
        SettingsHeaderCard(Icons.Rounded.PrivacyTip, c.indigo, "Everything stays on this phone", "Stored encrypted. No cloud AI. Nothing is sent anywhere unless you share it yourself.")
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
            SwitchRow("Keep Patient Details Out of Search", checked = true, onCheckedChange = {}, subtitle = "Always on · cannot be turned off", enabled = false)
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
fun AiPage(status: AiStatus, onBack: () -> Unit, onSelfCheck: () -> Unit, onRebuild: () -> Unit, onInstallReasoning: () -> Unit, snapshotSummary: String? = null, onCreateSnapshot: (() -> Unit)? = null, onRestoreSnapshot: (() -> Unit)? = null, onResetLearning: (() -> Unit)? = null, onExportLearning: (() -> Unit)? = null) {
    val c = Hod.colors
    var showTech by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    SettingsPage("On-Device AI", "Settings", onBack) {
        SettingsHeaderCard(if (status.healthy) Icons.Rounded.Check else Icons.Rounded.PriorityHigh, if (status.healthy) c.green else c.orange, status.headline, status.detail)
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
            if (onExportLearning != null) {
                ListRow("Export learning profile", subtitle = "Reviewable redacted corrections; nothing is sent automatically.", titleColor = c.tint, onClick = onExportLearning)
                RowDivider()
            }
            SwitchRow("Show Technical Details", showTech, { showTech = it })
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
        SettingsHeaderCard(Icons.Rounded.History, c.purple, "Every change is recorded", "Imports, approvals, scheduling, completion and deletion, in order. Nothing here can be edited.")
        GroupedSection(footer = "Export creates a copy only when you choose where to save it.") {
            ListRow("Export for Review…", titleColor = if (entries.isNotEmpty()) c.tint else c.tertiaryLabel, value = "${entries.size} entries", onClick = if (entries.isNotEmpty()) onExport else null)
        }
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
        SettingsHeaderCard(Icons.Rounded.Shield, c.gray, "Clinical Vault", "Items that mention patients or special categories. Not searchable, never sent to any AI model, never copied into tasks or calendar events.")
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

/** Morning brief and due-soon alerts. Both are off until the HOD turns them on. */
@Composable
fun RemindersPage(
    store: com.hoddesk.app.settings.DeskSettingsStore,
    notificationsAllowed: Boolean,
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Hod.colors
    var brief by remember { mutableStateOf(store.briefEnabled && notificationsAllowed) }
    var briefMinute by remember { mutableStateOf(store.briefMinute) }
    var due by remember { mutableStateOf(store.dueRemindersEnabled && notificationsAllowed) }
    var lead by remember { mutableStateOf(store.dueLeadMinutes) }
    var pickTime by remember { mutableStateOf(false) }
    var pickLead by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    fun turnOn(apply: () -> Unit) {
        if (notificationsAllowed) apply() else onRequestPermission { granted -> if (granted) apply() else blocked = true }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun leadLabel(m: Int) = if (m % 60 == 0) "${m / 60} hour${if (m == 60) "" else "s"} before" else "$m minutes before"
    SettingsPage("Reminders", "Settings", onBack) {
        SettingsHeaderCard(Icons.Rounded.NotificationsActive, c.red, "Reminders", "Created on this phone. Their text is hidden on the lock screen.")
        if (blocked) GroupedSection(footer = "Notifications are turned off for HOD Desk in Android settings, so reminders cannot appear.") {
            ListRow("Allow Notifications", titleColor = c.tint, chevron = true, onClick = { openAppSettings(context) })
        }
        GroupedSection(header = "Daily", footer = "One notification a day with what is overdue, due today, waiting for review and today’s meetings.") {
            SwitchRow("Morning Brief", brief, { on ->
                if (on) turnOn { brief = true; store.briefEnabled = true; onChanged() } else { brief = false; store.briefEnabled = false; onChanged() }
            })
            if (brief) {
                RowDivider()
                ListRow("Time", value = formatMinute(briefMinute), chevron = true, onClick = { pickTime = true })
            }
        }
        GroupedSection(header = "Tasks", footer = "Only open tasks with a due time. Done or rescheduled tasks never ring.") {
            SwitchRow("Due-Soon Alerts", due, { on ->
                if (on) turnOn { due = true; store.dueRemindersEnabled = true; onChanged() } else { due = false; store.dueRemindersEnabled = false; onChanged() }
            })
            if (due) {
                RowDivider()
                ListRow("Alert me", value = leadLabel(lead), chevron = true, onClick = { pickLead = true })
            }
        }
    }
    if (pickTime) TimeDialog("Brief time", briefMinute, onDismiss = { pickTime = false }) { m ->
        briefMinute = m; store.briefMinute = m; pickTime = false; onChanged()
    }
    if (pickLead) SheetDialog({ pickLead = false }) {
        com.hoddesk.app.ui.components.ActionSheetContent(
            "Alert me before a task is due", null,
            listOf(15, 30, 60, 120, 24 * 60).map { m ->
                com.hoddesk.app.ui.components.SheetAction(if (m == 24 * 60) "1 day before" else leadLabel(m), emphasized = m == lead) {
                    lead = m; store.dueLeadMinutes = m; pickLead = false; onChanged()
                }
            },
            onCancel = { pickLead = false },
        )
    }
}

/** Auto-lock: asks for the phone's own screen lock after the app has been away. */
@Composable
fun AutoLockPage(store: com.hoddesk.app.settings.DeskSettingsStore, deviceSecure: Boolean, onChanged: () -> Unit, onBack: () -> Unit) {
    val c = Hod.colors
    var enabled by remember { mutableStateOf(store.autoLockEnabled) }
    var minutes by remember { mutableStateOf(store.autoLockMinutes) }
    var pick by remember { mutableStateOf(false) }
    var secure by remember { mutableStateOf(store.secureScreen) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    SettingsPage("Auto-Lock", "Settings", onBack) {
        SettingsHeaderCard(Icons.Rounded.LockClock, c.blue, "Auto-Lock", "After HOD Desk has been away for a while, it covers itself and asks for your fingerprint, face or screen lock.")
        if (enabled && !deviceSecure) GroupedSection(footer = "This phone has no screen lock, so HOD Desk cannot lock itself.") {
            ListRow("Set Up Screen Lock", titleColor = c.tint, chevron = true, onClick = {
                runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
        }
        GroupedSection(header = "Lock", footer = "Nothing you were doing is lost; it is hidden until you unlock.") {
            SwitchRow("Lock HOD Desk", enabled, { on -> enabled = on; store.autoLockEnabled = on; onChanged() })
            if (enabled) {
                RowDivider()
                ListRow("Require Unlock", value = com.hoddesk.app.security.AppLockPolicy.timeoutLabel(minutes), chevron = true, onClick = { pick = true })
            }
        }
        GroupedSection(header = "Screen Privacy", footer = "Shows a blank card in recent apps and blocks screenshots and screen recording of HOD Desk. Turn off if you need to screenshot the app.") {
            SwitchRow("Hide in Recent Apps", secure, { on ->
                secure = on; store.secureScreen = on
                com.hoddesk.app.security.ScreenPrivacy.apply(activity, on)
                onChanged()
            })
        }
    }
    if (pick) SheetDialog({ pick = false }) {
        com.hoddesk.app.ui.components.ActionSheetContent(
            "Lock HOD Desk", null,
            com.hoddesk.app.security.AppLockPolicy.TIMEOUT_CHOICES.map { m ->
                com.hoddesk.app.ui.components.SheetAction(com.hoddesk.app.security.AppLockPolicy.timeoutLabel(m), emphasized = m == minutes) {
                    minutes = m; store.autoLockMinutes = m; pick = false; onChanged()
                }
            },
            onCancel = { pick = false },
        )
    }
}

/** Password-encrypted backup the HOD saves where they choose, and restore from it. */
@Composable
fun BackupPage(lastBackupAt: Long?, busy: String?, onCreate: () -> Unit, onRestore: () -> Unit, onBack: () -> Unit) {
    val c = Hod.colors
    SettingsPage("Backup & Restore", "Settings", onBack) {
        SettingsHeaderCard(
            Icons.Rounded.Backup, c.tint, "Your data lives only on this phone",
            "If the phone is lost or replaced, everything is gone unless you keep a backup: one file, locked with a password you choose.",
        )
        GroupedSection(footer = busy ?: "Save the file somewhere you trust: a USB drive, your computer or your own cloud storage.") {
            ListRow(
                "Last Backup",
                value = lastBackupAt?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "Never",
                valueColor = if (lastBackupAt == null) c.orangeText else c.secondaryLabel,
            )
            RowDivider()
            ListRow("Back Up Now…", titleColor = if (busy == null) c.tint else c.tertiaryLabel, onClick = if (busy == null) onCreate else null)
        }
        GroupedSection(footer = "Restoring replaces everything currently on this phone. Search is rebuilt afterwards.") {
            ListRow("Restore from Backup…", titleColor = if (busy == null) c.tint else c.tertiaryLabel, onClick = if (busy == null) onRestore else null)
        }
        GroupedSection(header = "Good to Know") {
            ListRow("Forgotten password", subtitle = "The backup cannot be opened by anyone, including you.", icon = Icons.Rounded.Lock, iconColor = c.orange)
            RowDivider(58.dp)
            ListRow("What is included", subtitle = "Sources, tasks, clinical vault, commitments, settings and the activity log.", icon = Icons.Rounded.Check, iconColor = c.green)
            RowDivider(58.dp)
            ListRow("Calendar", subtitle = "Events already in your Android calendar are not touched. Choose the calendar again on a new phone.", icon = Icons.Rounded.CalendarMonth, iconColor = c.red)
        }
    }
}

/** Opens HOD Desk's page in Android settings (permissions, notifications). */
internal fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
