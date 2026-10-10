package com.hoddesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.settings.FixedBlock
import com.hoddesk.app.settings.formatMinute
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.hatched
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A block "Plan my day" or "Protect time" proposes. Nothing is written until confirmed. */
data class ProposedBlock(val taskId: Long?, val title: String, val start: Long, val end: Long, val clashesWith: List<String>, val rationale: String = "")

private val HourHeight: Dp = 56.dp
private const val FirstHour = 7
private const val LastHour = 20

@Composable
fun CalendarScreen(
    selectedDay: LocalDate,
    events: List<EventEntity>,
    fixedBlocks: List<FixedBlock>,
    proposals: List<ProposedBlock>,
    onSelectDay: (LocalDate) -> Unit,
    onPlanDay: () -> Unit,
    onProtectTime: () -> Unit,
    onOpenEvent: (EventEntity) -> Unit,
    onConfirmProposals: () -> Unit,
    onDiscardProposals: () -> Unit,
) {
    val c = Hod.colors
    val today = LocalDate.now()
    val weekStart = selectedDay.minusDays((selectedDay.dayOfWeek.value % 7).toLong())
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(selectedDay.format(DateTimeFormatter.ofPattern(if (selectedDay.year == today.year) "MMMM" else "MMMM yyyy")), style = Hod.type.largeTitle, color = c.label, modifier = Modifier.weight(1f))
            if (selectedDay != today) Text("Today", style = Hod.type.body, color = c.tint, modifier = Modifier.padding(end = 12.dp).pressable { onSelectDay(today) })
            HodButton("Plan My Day", onPlanDay, style = HodButtonStyle.Tinted, height = 34.dp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).pressable { onSelectDay(selectedDay.minusWeeks(1)) }.semantics { contentDescription = "Previous week" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronLeft, null, tint = c.tint)
            }
            Row(Modifier.weight(1f)) {
                (0..6).map { weekStart.plusDays(it.toLong()) }.forEach { day ->
                    val selected = day == selectedDay
                    Column(Modifier.weight(1f).pressable { onSelectDay(day) }.semantics { contentDescription = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) + if (selected) ", selected" else "" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.dayOfWeek.name.take(1), style = Hod.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.tertiaryLabel)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(38.dp).clip(Squircles.small).background(if (selected) c.tint else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
                            Text(day.dayOfMonth.toString(), style = Hod.type.title3.copy(fontWeight = if (selected || day == today) FontWeight.SemiBold else FontWeight.Normal), color = when { selected -> c.onTint; day == today -> c.tint; else -> c.label })
                        }
                    }
                }
            }
            Box(Modifier.size(36.dp).pressable { onSelectDay(selectedDay.plusWeeks(1)) }.semantics { contentDescription = "Next week" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronRight, null, tint = c.tint)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(selectedDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = Hod.type.subheadline, color = c.secondaryLabel, modifier = Modifier.weight(1f))
            Text("Protect Time", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.pressable(onClick = onProtectTime))
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))

        val scroll = rememberScrollState()
        LaunchedEffect(selectedDay) { scroll.scrollTo(0) }
        Box(Modifier.weight(1f)) {
            Timeline(selectedDay, events, fixedBlocks, proposals, onOpenEvent, Modifier.verticalScroll(scroll))
            if (proposals.isNotEmpty()) ProposalPanel(proposals, onConfirmProposals, onDiscardProposals, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Timeline(day: LocalDate, events: List<EventEntity>, fixedBlocks: List<FixedBlock>, proposals: List<ProposedBlock>, onOpenEvent: (EventEntity) -> Unit, modifier: Modifier) {
    val c = Hod.colors
    val zone = ZoneId.systemDefault()
    fun minuteOf(epoch: Long): Int = Instant.ofEpochMilli(epoch).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
    fun top(minute: Int): Dp = HourHeight * ((minute - FirstHour * 60).coerceAtLeast(0) / 60f)
    fun height(start: Int, end: Int): Dp = HourHeight * (((end - start).coerceAtLeast(20)) / 60f)
    val gutter = 58.dp
    Box(modifier.fillMaxWidth().height(HourHeight * (LastHour - FirstHour + 1) + 140.dp).padding(top = 10.dp)) {
        (FirstHour..LastHour).forEach { hour ->
            Row(Modifier.offset(y = HourHeight * (hour - FirstHour)).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(formatMinute(hour * 60).replace(":00", ""), style = Hod.type.caption, color = c.tertiaryLabel, modifier = Modifier.width(gutter).offset(y = (-7).dp).padding(end = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Box(Modifier.weight(1f).height(0.5.dp).background(c.separator.copy(alpha = 0.6f)))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = gutter + 4.dp, end = 10.dp)) {
            val width = maxWidth
            fixedBlocks.forEach { b ->
                Box(
                    Modifier.offset(y = top(b.startMinute)).fillMaxWidth().height(height(b.startMinute, b.endMinute)).clip(Squircles.pill)
                        .hatched(c.hatch, c.surface.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = "${b.label}, fixed, ${b.timeLabel()}" },
                ) { Text("${b.label} · fixed", style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel) }
            }
            // Lane layout so overlapping events sit side by side and clashes stay visible.
            data class Item(val start: Int, val end: Int, val event: EventEntity?, val proposal: ProposedBlock?)
            val items = (events.map { Item(minuteOf(it.startsAt), minuteOf(it.endsAt).let { e -> if (e <= minuteOf(it.startsAt)) 24 * 60 else e }, it, null) } +
                proposals.filter { epochToDate(it.start) == day }.map { Item(minuteOf(it.start), minuteOf(it.end), null, it) }).sortedBy { it.start }
            val lanes = mutableListOf<Int>()
            val laneOf = items.map { item ->
                val free = lanes.indexOfFirst { it <= item.start }
                if (free >= 0) { lanes[free] = item.end; free } else { lanes += item.end; lanes.size - 1 }
            }
            val laneCount = lanes.size.coerceAtLeast(1)
            items.forEachIndexed { i, item ->
                val laneWidth = width / laneCount
                val x = laneWidth * laneOf[i]
                val boxModifier = Modifier.offset(x = x, y = top(item.start)).width(laneWidth - 3.dp).height(height(item.start, item.end))
                if (item.event != null) {
                    Column(
                        boxModifier.clip(Squircles.pill).background(c.blue.copy(alpha = 0.14f)).pressable { onOpenEvent(item.event) }.padding(horizontal = 8.dp, vertical = 5.dp)
                            .semantics { contentDescription = "${item.event.title}, ${formatMinute(item.start)} to ${formatMinute(item.end)}" },
                    ) {
                        Text(item.event.title, style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.blueText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.event.location ?: "${formatMinute(item.start)} – ${formatMinute(item.end)}", style = Hod.type.caption, color = c.blueText, maxLines = 1)
                    }
                } else if (item.proposal != null) {
                    val clash = item.proposal.clashesWith.isNotEmpty()
                    val stroke = if (clash) c.red else c.tint
                    Column(
                        boxModifier.drawBehind {
                            drawRoundRect(stroke, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))), cornerRadius = CornerRadius(10.dp.toPx()))
                        }.clip(Squircles.pill).background(if (clash) c.red.copy(alpha = 0.08f) else c.tintSoft.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 5.dp)
                            .semantics { contentDescription = "Proposed: ${item.proposal.title}" + if (clash) ", clashes with ${item.proposal.clashesWith.joinToString()}" else "" },
                    ) {
                        Text(if (clash) "Clash · proposed" else "Proposed", style = Hod.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (clash) c.redText else c.tint)
                        Text(item.proposal.title, style = Hod.type.footnote, color = if (clash) c.redText else c.tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (day == LocalDate.now()) {
            val now = LocalTime.now().let { it.hour * 60 + it.minute }
            if (now in FirstHour * 60..LastHour * 60) Row(Modifier.offset(y = top(now)).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatMinute(now), style = Hod.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.red, modifier = Modifier.width(gutter).padding(end = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Box(Modifier.weight(1f).height(1.5.dp).background(c.red))
            }
        }
    }
}

@Composable
private fun ProposalPanel(proposals: List<ProposedBlock>, onConfirm: () -> Unit, onDiscard: () -> Unit, modifier: Modifier) {
    val c = Hod.colors
    val clashes = proposals.filter { it.clashesWith.isNotEmpty() }
    Column(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 96.dp).clip(Squircles.card).background(c.surface)
            .border(0.5.dp, c.separator, Squircles.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${proposals.size} proposed block(s)", style = Hod.type.headline, color = c.label)
        Text(
            if (clashes.isEmpty()) "Nothing is written to your calendar until you confirm. Fixed clinical time and existing events are left untouched."
            else "${clashes.size} clash with existing events (${clashes.flatMap { it.clashesWith }.distinct().joinToString()}). Clashing blocks will be skipped.",
            style = Hod.type.footnote, color = if (clashes.isEmpty()) c.secondaryLabel else c.redText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HodButton("Discard", onDiscard, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
            HodButton("Add ${proposals.size - clashes.size}", onConfirm, enabled = proposals.size > clashes.size, height = 46.dp, modifier = Modifier.weight(1.4f))
        }
    }
}
