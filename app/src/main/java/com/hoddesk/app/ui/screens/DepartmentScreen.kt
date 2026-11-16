package com.hoddesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hoddesk.app.settings.DeskSettingsStore
import com.hoddesk.app.settings.FixedBlock
import com.hoddesk.app.settings.TeamMember
import com.hoddesk.app.settings.Venue
import com.hoddesk.app.settings.formatMinute
import com.hoddesk.app.ui.components.GroupedSection
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.ListRow
import com.hoddesk.app.ui.components.RowDivider
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles
import java.time.DayOfWeek

@Composable
fun DepartmentPage(store: DeskSettingsStore, onBack: () -> Unit, onChanged: () -> Unit) {
    val c = Hod.colors
    var blocks by remember { mutableStateOf(store.fixedBlocks) }
    var team by remember { mutableStateOf(store.team) }
    var venues by remember { mutableStateOf(store.venues) }
    var authority by remember { mutableStateOf(store.authorityOrder) }
    var editingBlock by remember { mutableStateOf<Pair<Int, FixedBlock>?>(null) }
    var editingMember by remember { mutableStateOf<Pair<Int, TeamMember>?>(null) }
    var editingVenue by remember { mutableStateOf<Pair<Int, Venue>?>(null) }
    fun saveBlocks(v: List<FixedBlock>) { blocks = v; store.fixedBlocks = v; onChanged() }
    fun saveTeam(v: List<TeamMember>) { team = v; store.team = v; onChanged() }
    fun saveVenues(v: List<Venue>) { venues = v; store.venues = v; onChanged() }

    SettingsPage("Department", "Settings", onBack) {
        Text("Used to plan your day around clinical work and to suggest who to delegate to. Stored only on this phone.", style = Hod.type.subheadline, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 4.dp))
        GroupedSection(header = "Fixed Clinical Time", footer = "Plan My Day and Protect Time never book over these blocks.") {
            blocks.forEachIndexed { i, b ->
                if (i > 0) RowDivider()
                ListRow(b.label, subtitle = b.daysLabel(), value = b.timeLabel(), chevron = true, onClick = { editingBlock = i to b })
            }
            if (blocks.isNotEmpty()) RowDivider()
            ListRow("Add Fixed Block", titleColor = c.tint, onClick = { editingBlock = -1 to FixedBlock("", setOf(DayOfWeek.MONDAY), 9 * 60, 10 * 60) })
        }
        GroupedSection(header = "Team", footer = "People you can delegate to from Review and Tasks. The app never messages them; you send the message.") {
            team.forEachIndexed { i, m ->
                if (i > 0) RowDivider(64.dp)
                Row(Modifier.fillMaxWidth().pressable { editingMember = i to m }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c.blue), contentAlignment = Alignment.Center) { Text(initials(m.name), style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = Hod.type.body, color = c.label)
                        Text(listOf(m.role, m.topics).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Add role and topics" }, style = Hod.type.footnote, color = c.secondaryLabel)
                    }
                    com.hoddesk.app.ui.components.Chevron()
                }
            }
            if (team.isNotEmpty()) RowDivider()
            ListRow("Add Team Member", titleColor = c.tint, onClick = { editingMember = -1 to TeamMember("", "", "") })
        }
        GroupedSection(header = "Venues & Travel Time") {
            venues.forEachIndexed { i, v ->
                if (i > 0) RowDivider()
                ListRow(v.name, value = if (v.travelMinutes >= 180) "Out of town" else "${v.travelMinutes} min", chevron = true, onClick = { editingVenue = i to v })
            }
            if (venues.isNotEmpty()) RowDivider()
            ListRow("Add Venue", titleColor = c.tint, onClick = { editingVenue = -1 to Venue("", 15) })
        }
        GroupedSection(header = "Who Outranks Whom", footer = "Highest first. Higher authority makes an item more urgent in your brief.") {
            authority.forEachIndexed { i, name ->
                if (i > 0) RowDivider(44.dp)
                Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = Hod.type.subheadline, color = c.tertiaryLabel, modifier = Modifier.width(28.dp))
                    Text(name, style = Hod.type.body, color = c.label, modifier = Modifier.weight(1f))
                    Box(Modifier.size(44.dp).pressable(enabled = i > 0) { val l = authority.toMutableList(); l.add(i - 1, l.removeAt(i)); authority = l; store.authorityOrder = l }.semantics { contentDescription = "Move $name up" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = if (i > 0) c.tint else c.chevron)
                    }
                    Box(Modifier.size(44.dp).pressable(enabled = i < authority.lastIndex) { val l = authority.toMutableList(); l.add(i + 1, l.removeAt(i)); authority = l; store.authorityOrder = l }.semantics { contentDescription = "Move $name down" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = if (i < authority.lastIndex) c.tint else c.chevron)
                    }
                }
            }
        }
    }

    editingBlock?.let { (index, block) ->
        BlockEditor(block, isNew = index < 0, onDismiss = { editingBlock = null }, onDelete = { saveBlocks(blocks.filterIndexed { i, _ -> i != index }); editingBlock = null }) { updated ->
            saveBlocks(if (index < 0) blocks + updated else blocks.mapIndexed { i, b -> if (i == index) updated else b }); editingBlock = null
        }
    }
    editingMember?.let { (index, member) ->
        FieldsEditor(if (index < 0) "Add Team Member" else "Team Member", listOf("Name" to member.name, "Role" to member.role, "Usual topics" to member.topics), isNew = index < 0,
            onDismiss = { editingMember = null }, onDelete = { saveTeam(team.filterIndexed { i, _ -> i != index }); editingMember = null }) { v ->
            if (v[0].isNotBlank()) { val m = TeamMember(v[0].trim(), v[1].trim(), v[2].trim()); saveTeam(if (index < 0) team + m else team.mapIndexed { i, o -> if (i == index) m else o }) }
            editingMember = null
        }
    }
    editingVenue?.let { (index, venue) ->
        FieldsEditor(if (index < 0) "Add Venue" else "Venue", listOf("Name" to venue.name, "Travel minutes" to venue.travelMinutes.toString()), isNew = index < 0,
            onDismiss = { editingVenue = null }, onDelete = { saveVenues(venues.filterIndexed { i, _ -> i != index }); editingVenue = null }) { v ->
            if (v[0].isNotBlank()) { val nv = Venue(v[0].trim(), v[1].filter(Char::isDigit).toIntOrNull() ?: 0); saveVenues(if (index < 0) venues + nv else venues.mapIndexed { i, o -> if (i == index) nv else o }) }
            editingVenue = null
        }
    }
}

@Composable
private fun BlockEditor(block: FixedBlock, isNew: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (FixedBlock) -> Unit) {
    val c = Hod.colors
    var label by remember { mutableStateOf(block.label) }
    var days by remember { mutableStateOf(block.days) }
    var start by remember { mutableStateOf(block.startMinute) }
    var end by remember { mutableStateOf(block.endMinute) }
    var picking by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (isNew) "Add Fixed Block" else "Fixed Block", style = Hod.type.headline, color = c.label)
            OutlinedTextField(label, { label = it }, label = { Text("Name (OPD, ECT, Teaching…)") }, singleLine = true, shape = Squircles.small, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.tint, focusedLabelColor = c.tint, cursorColor = c.tint))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.values().forEach { d ->
                    val on = d in days
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(Squircles.small).background(if (on) c.tint else c.fill)
                            .pressable { days = if (on) days - d else days + d }.semantics { contentDescription = d.name.lowercase() + if (on) ", on" else ", off" },
                        contentAlignment = Alignment.Center,
                    ) { Text(d.name.take(2).lowercase().replaceFirstChar(Char::uppercase), style = Hod.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (on) c.onTint else c.label, textAlign = TextAlign.Center) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton("From ${formatMinute(start)}", { picking = "start" }, style = HodButtonStyle.Gray, height = 44.dp, modifier = Modifier.weight(1f))
                HodButton("To ${formatMinute(end)}", { picking = "end" }, style = HodButtonStyle.Gray, height = 44.dp, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isNew) HodButton("Delete", onDelete, style = HodButtonStyle.DestructiveTinted, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Cancel", onDismiss, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Save", { onSave(FixedBlock(label.trim(), days, start, end)) }, enabled = label.isNotBlank() && days.isNotEmpty() && end > start, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
    picking?.let { which ->
        TimeDialog(if (which == "start") "Starts" else "Ends", if (which == "start") start else end, onDismiss = { picking = null }) { m ->
            if (which == "start") start = m else end = m
            picking = null
        }
    }
}

@Composable
private fun FieldsEditor(title: String, fields: List<Pair<String, String>>, isNew: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (List<String>) -> Unit) {
    val c = Hod.colors
    var values by remember { mutableStateOf(fields.map { it.second }) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Hod.type.headline, color = c.label)
            fields.forEachIndexed { i, (label, _) ->
                OutlinedTextField(values[i], { v -> values = values.toMutableList().also { it[i] = v } }, label = { Text(label) }, singleLine = true, shape = Squircles.small, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.tint, focusedLabelColor = c.tint, cursorColor = c.tint))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isNew) HodButton("Delete", onDelete, style = HodButtonStyle.DestructiveTinted, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Cancel", onDismiss, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Save", { onSave(values) }, enabled = values.first().isNotBlank(), height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}
