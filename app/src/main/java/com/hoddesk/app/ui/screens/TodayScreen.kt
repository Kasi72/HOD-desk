package com.hoddesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.settings.FixedBlock
import com.hoddesk.app.settings.formatMinute
import com.hoddesk.app.ui.components.Banner
import com.hoddesk.app.ui.components.BannerTone
import com.hoddesk.app.ui.components.Chevron
import com.hoddesk.app.ui.components.EmptyState
import com.hoddesk.app.ui.components.IconTile
import com.hoddesk.app.ui.components.PageContentPadding
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.MeridianColors
import com.hoddesk.app.ui.theme.Neu
import com.hoddesk.app.ui.theme.Squircles
import com.hoddesk.app.workflow.SecretaryIntelligence
import com.hoddesk.app.secretary.SecretaryStateSnapshot
import com.hoddesk.app.workflow.MeetingPreparation
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One item in the "Must act" brief: either an unreviewed high-priority source or a task due now. */
sealed interface MustAct {
    data class Review(val source: SourceEntity, val headline: String) : MustAct
    data class Task(val task: TaskEntity) : MustAct
}

fun buildMustAct(sources: List<SourceEntity>, tasks: List<TaskEntity>, today: LocalDate = LocalDate.now()): List<MustAct> {
    val endOfToday = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dueTasks = tasks.filter { it.status != "completed" && it.status != "deleted" && it.status != "archived" && it.dueAt != null && it.dueAt < endOfToday }
        .sortedBy { it.dueAt }.map { MustAct.Task(it) }
    val reviews = sources.filter { it.processingStatus !in setOf("deleted", "archived") && it.body.isNotBlank() && !it.reviewed && it.triageBucket == "ACT" && !it.isClinical() }
        .sortedByDescending { it.priorityScore }.map { MustAct.Review(it, it.title) }
    return (dueTasks + reviews).take(4)
}

@Composable
fun TodayScreen(
    sources: List<SourceEntity>,
    tasks: List<TaskEntity>,
    todayEvents: List<EventEntity>,
    commitments: List<CommitmentEntity> = emptyList(),
    todayBlocks: List<FixedBlock>,
    onDeviceLine: String,
    onDeviceHealthy: Boolean,
    status: String?,
    onDismissStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onOpenReview: (SourceEntity?) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenWaiting: () -> Unit,
    secretaryState: SecretaryStateSnapshot? = null,
) {
    val c = Hod.colors
    val today = LocalDate.now()
    val mustAct = buildMustAct(sources, tasks, today)
    val toReview = sources.count { it.processingStatus !in setOf("deleted", "archived") && it.body.isNotBlank() && !it.reviewed && !it.isClinical() }
    val waiting = secretaryState?.waiting ?: tasks.count { it.status != "completed" && it.status != "deleted" && it.status != "archived" && !it.assignee.isNullOrBlank() }
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PageContentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(), style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel)
                    Text("Today", style = Hod.type.largeTitle, color = c.label)
                }
                Box(
                    Modifier.padding(bottom = 4.dp).size(40.dp).clip(CircleShape).background(c.tint).pressable(onClick = onOpenSettings).semantics { contentDescription = "Settings" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Person, null, tint = c.onTint, modifier = Modifier.size(22.dp)) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(38.dp).clip(Squircles.small).background(c.fill).pressable(onClick = onSearch).padding(horizontal = 10.dp).semantics { contentDescription = "Search letters, people, GO numbers" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
                Text("Search letters, people, GO numbers", style = Hod.type.body, color = c.secondaryLabel, maxLines = 1)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = if (onDeviceHealthy) c.greenText else c.orangeText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(onDeviceLine, style = Hod.type.footnote, color = c.secondaryLabel)
            }
        }
        status?.let { item { Banner("Import", it, BannerTone.Info, onDismiss = onDismissStatus) } }

        item {
            val brief = SecretaryIntelligence.brief(sources, tasks, today)
            val cited = SecretaryIntelligence.executiveBrief(SecretaryIntelligence.BriefKind.MORNING, sources, tasks, now = System.currentTimeMillis())
            SecretaryBriefPanel(brief, cited.citations.size)
        }
        item {
            val now = System.currentTimeMillis()
            val executiveBriefs = listOf(
                SecretaryIntelligence.BriefKind.MEETING_PREPARATION,
                SecretaryIntelligence.BriefKind.MIDDAY_RISK,
                SecretaryIntelligence.BriefKind.EVENING_CLOSE,
            ).map { kind -> SecretaryIntelligence.executiveBrief(kind, sources, tasks, todayEvents, commitments, now) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                executiveBriefs.forEach { executive ->
                    ExecutiveBriefRow(executive)
                }
            }
        }

        MeetingPreparation.next(todayEvents)?.let { nextEvent ->
            item {
                val pack = MeetingPreparation.pack(nextEvent, tasks, sources)
                Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Meeting preparation", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint)
                    Text(nextEvent.title, style = Hod.type.headline, color = c.label)
                    if (pack.agenda.isNotEmpty()) Text("Prepare: ${pack.agenda.joinToString(" · ")}", style = Hod.type.footnote, color = c.secondaryLabel, maxLines = 2)
                    if (pack.risks.isNotEmpty()) Text(pack.risks.joinToString(" · "), style = Hod.type.footnote, color = c.orangeText, maxLines = 2)
                    else if (pack.evidence.isNotEmpty()) Text("Evidence ready: ${pack.evidence.joinToString(" · ")}", style = Hod.type.footnote, color = c.secondaryLabel, maxLines = 2)
                }
            }
        }

        item { SectionTitle("Must Act", if (mustAct.isEmpty()) null else "${mustAct.size} today") }
        if (mustAct.isEmpty()) item {
            Box(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface)) {
                EmptyState("Nothing needs you right now", if (toReview > 0) "$toReview item(s) are waiting in Review when you have time." else "New letters, messages and calls will appear here when they need action.")
            }
        }
        items(mustAct, key = { when (it) { is MustAct.Task -> "t${it.task.id}"; is MustAct.Review -> "s${it.source.id}" } }) { item ->
            when (item) {
                is MustAct.Task -> {
                    val overdue = (item.task.dueAt ?: Long.MAX_VALUE) < System.currentTimeMillis()
                    BriefCard(
                        icon = Icons.Rounded.PriorityHigh,
                        iconColor = if (overdue) c.red else c.orange,
                        label = (if (overdue) "Overdue · " else "Due ") + dueLabel(item.task.dueAt).removePrefix("Today, ") + (item.task.assignee?.let { " · with $it" } ?: ""),
                        labelColor = if (overdue) c.redText else c.orangeText,
                        title = item.task.title,
                        subtitle = item.task.project,
                        onClick = { onOpenTask(item.task) },
                    )
                }
                is MustAct.Review -> {
                    val look = sourceLook(item.source.sourceType)
                    BriefCard(
                        icon = look.icon,
                        iconColor = look.color,
                        label = "Needs review" + (item.source.priorityReasons?.let { " · $it" } ?: ""),
                        labelColor = c.orangeText,
                        title = item.headline,
                        subtitle = cleanEvidence(item.source.body).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                        onClick = { onOpenReview(item.source) },
                    )
                }
            }
        }

        item { SectionTitle("Schedule", null, action = "Calendar", onAction = onOpenCalendar) }
        item {
            val rows = buildList {
                todayBlocks.forEach { add(Triple(it.startMinute, it.label + " · fixed", true)) }
                todayEvents.forEach { e ->
                    val t = java.time.Instant.ofEpochMilli(e.startsAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                    add(Triple(t.hour * 60 + t.minute, e.title, false))
                }
            }.sortedBy { it.first }
            Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface)) {
                if (rows.isEmpty()) Text("No events today.", style = Hod.type.body, color = c.secondaryLabel, modifier = Modifier.padding(16.dp))
                rows.forEachIndexed { index, (minute, title, fixed) ->
                    if (index > 0) RowDivider(78.dp)
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMinute(minute), style = Hod.type.subheadline, color = c.secondaryLabel, modifier = Modifier.width(62.dp))
                        Text(title, style = Hod.type.body, color = if (fixed) c.secondaryLabel else c.label, maxLines = 1)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountTile(secretaryState?.action ?: toReview, "To review", Modifier.weight(1f)) { onOpenReview(null) }
                CountTile(waiting, "Waiting on others", Modifier.weight(1f), onClick = onOpenWaiting)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailingText: String?, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = Hod.type.title2, color = Hod.colors.label, modifier = Modifier.weight(1f))
        trailingText?.let { Text(it, style = Hod.type.subheadline, color = Hod.colors.secondaryLabel) }
        if (action != null && onAction != null) Text(action, style = Hod.type.body, color = Hod.colors.tint, modifier = Modifier.pressable(onClick = onAction))
    }
}

@Composable
private fun BriefCard(icon: ImageVector, iconColor: Color, label: String, labelColor: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Squircles.card).background(Hod.colors.surface).pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTile(icon, iconColor, size = 26.dp)
            Text(label, style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = labelColor, modifier = Modifier.weight(1f), maxLines = 1)
            Chevron()
        }
        Text(title, style = Hod.type.headline, color = Hod.colors.label)
        if (subtitle.isNotBlank()) Text(subtitle, style = Hod.type.subheadline, color = Hod.colors.secondaryLabel, maxLines = 2)
    }
}

@Composable
private fun CountTile(count: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(Squircles.card).background(Hod.colors.surface).pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(count.toString(), style = Hod.type.title1, color = Hod.colors.label)
        Text(label, style = Hod.type.subheadline, color = Hod.colors.secondaryLabel)
    }
}

@Composable
private fun SecretaryBriefPanel(brief: SecretaryIntelligence.Brief, citedCount: Int = 0) {
    val c = Hod.colors
    Column(
        Modifier.fillMaxWidth().then(Neu.darkHero(shape = Squircles.card)).clip(Squircles.card)
            .background(MeridianColors.panelBrief).padding(horizontal = 18.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SECRETARY BRIEF", style = Hod.type.label, color = MeridianColors.aiOnDark)
            Spacer(Modifier.weight(1f))
            Text("${brief.urgent} URGENT", style = Hod.type.mono, color = MeridianColors.onPanel3)
        }
        Text(brief.headline, style = Hod.type.title3.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif), color = MeridianColors.onPanel)
        Text(brief.detail, style = Hod.type.subheadline, color = MeridianColors.onPanel2, maxLines = 2)
        Text("${citedCount} evidence-backed item${if (citedCount == 1) "" else "s"} · review before acting", style = Hod.type.footnote, color = MeridianColors.onPanel3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BriefStat("${brief.toReview}", "TO REVIEW")
            BriefStat("${brief.dueSoon}", "DUE SOON")
            BriefStat("${brief.waiting}", "WAITING")
        }
    }
}

@Composable
private fun ExecutiveBriefRow(brief: SecretaryIntelligence.ExecutiveBrief) {
    val c = Hod.colors
    Column(
        Modifier.fillMaxWidth().clip(Squircles.small).background(c.surface).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(brief.title, style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f))
            Text("${brief.citations.size} cited", style = Hod.type.footnote, color = c.tint)
        }
        Text(brief.summary, style = Hod.type.footnote, color = c.secondaryLabel, maxLines = 2)
        val unresolved = brief.citations.count { it.unresolved }
        val authority = brief.citations.count { it.authority == "High authority" || it.authority == "Official" }
        if (unresolved > 0 || authority > 0) {
            Text(
                buildString {
                    if (unresolved > 0) append("$unresolved unresolved")
                    if (unresolved > 0 && authority > 0) append(" · ")
                    if (authority > 0) append("$authority official")
                },
                style = Hod.type.label,
                color = if (unresolved > 0) c.orangeText else c.secondaryLabel,
            )
        }
        Text("Review before acting", style = Hod.type.label, color = c.secondaryLabel)
    }
}

@Composable
private fun BriefStat(value: String, label: String) {
    Column(
        Modifier.fillMaxWidth(0.333f).clip(Squircles.small).background(MeridianColors.panelWell).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = Hod.type.title3, color = MeridianColors.onPanel)
        Text(label, style = Hod.type.label.copy(fontSize = 9.sp, letterSpacing = 0.08.em), color = MeridianColors.onPanel3)
    }
}
