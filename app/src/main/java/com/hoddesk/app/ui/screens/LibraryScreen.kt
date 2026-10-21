package com.hoddesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.semantic.HybridMatch
import com.hoddesk.app.ui.components.CheckCircle
import com.hoddesk.app.ui.components.EmptyState
import com.hoddesk.app.ui.components.EvidenceText
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.IconTile
import com.hoddesk.app.ui.components.PageContentPadding
import com.hoddesk.app.ui.components.SearchField
import com.hoddesk.app.ui.components.SegmentedControl
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Neu
import com.hoddesk.app.ui.theme.Squircles

private val Scopes = listOf("All", "Letters", "WhatsApp", "Calls")

@Composable
fun LibraryScreen(
    sources: List<SourceEntity>,
    query: String,
    matches: List<HybridMatch>,
    statusLine: String,
    onQueryChange: (String) -> Unit,
    onOpen: (SourceEntity) -> Unit,
    onDeleteSelected: (List<Long>) -> Unit,
) {
    val c = Hod.colors
    var scope by rememberSaveable { mutableIntStateOf(0) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val searchable = sources.filter { !it.isClinical() }
    // A source can have a summary vector, passage vectors and action vectors.
    // Semantic search may therefore return the same source more than once.
    // Compose LazyColumn keys must be unique; merge those hits before rendering
    // while retaining all match explanations.
    val base: List<Pair<SourceEntity, List<String>>> = if (query.isBlank()) searchable.map { it to emptyList() }
        else if (matches.isNotEmpty()) matches.asSequence()
            .filter { !it.source.isClinical() }
            .groupBy { it.source.id }
            .values
            .map { hits ->
                val source = hits.first().source
                source to hits.flatMap { it.reasons }.distinct()
            }
            .toList()
        else searchable.filter { "${it.title} ${it.body}".contains(query, ignoreCase = true) }.map { it to listOf("Exact words") }
    val results = base.filter { scope == 0 || sourceScope(it.first.sourceType) == Scopes[scope] }
    val terms = query.split(Regex("\\s+")).filter { it.length >= 2 }

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PageContentPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(if (selecting) "${selected.size} Selected" else "Library", style = Hod.type.largeTitle, color = c.label, modifier = Modifier.weight(1f))
                Text(if (selecting) "Done" else "Select", style = Hod.type.body.copy(fontWeight = if (selecting) FontWeight.SemiBold else FontWeight.Normal), color = c.tint, modifier = Modifier.padding(bottom = 6.dp).pressable {
                    selecting = !selecting; selected = emptySet()
                })
            }
        }
        item { SearchField(query, onQueryChange, "Search in Tamil or English, GO numbers…") }
        item { SegmentedControl(Scopes, scope, { scope = it }) }
        item { Text(if (query.isBlank()) "${searchable.size} items · $statusLine" else "${results.size} result(s) · $statusLine", style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 4.dp)) }
        if (selecting) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton(if (selected.size == results.size && results.isNotEmpty()) "Clear All" else "Select All", {
                    selected = if (selected.size == results.size) emptySet() else results.map { it.first.id }.toSet()
                }, style = HodButtonStyle.Gray, height = 40.dp, modifier = Modifier.weight(1f))
                HodButton("Delete ${selected.size}", { onDeleteSelected(selected.toList()); selected = emptySet(); selecting = false }, style = HodButtonStyle.DestructiveTinted, height = 40.dp, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f))
            }
        }
        if (results.isEmpty()) item {
            Box(Modifier.fillMaxWidth().then(Neu.card(shape = Squircles.card)).clip(Squircles.card).background(c.surface)) {
                EmptyState(if (query.isBlank()) "Library is empty" else "No matches", if (query.isBlank()) "Imported letters, messages and calls are kept here, searchable on this phone." else "Try a GO number, a person, or describe it in Tamil or English.")
            }
        }
        itemsIndexed(results, key = { _, r -> r.first.id }) { _, (source, reasons) ->
            val look = sourceLook(source.sourceType)
            val isSelected = source.id in selected
            Row(
                Modifier.fillMaxWidth().then(Neu.card(shape = Squircles.card)).clip(Squircles.card).background(if (isSelected) c.tintSoft else c.surface)
                    .pressable { if (selecting) selected = if (isSelected) selected - source.id else selected + source.id else onOpen(source) }
                    .padding(start = if (selecting) 4.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selecting) CheckCircle(isSelected, { selected = if (isSelected) selected - source.id else selected + source.id }, source.title)
                else IconTile(look.icon, look.color, size = 34.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(source.title, style = Hod.type.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(dueLabel(source.capturedAt).substringBefore(","), style = Hod.type.subheadline, color = c.tertiaryLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(look.label + if (source.reviewed) " · reviewed" else " · not reviewed", style = Hod.type.footnote, color = c.secondaryLabel)
                    EvidenceText(cleanEvidence(source.body).ifBlank { source.title }, terms, maxLines = 3)
                    if (reasons.isNotEmpty()) Text(reasons.joinToString(" · ") { it.replace("semantic evidence", "same meaning").replace("keyword:", "exact words:").replaceFirstChar(Char::uppercase) }, style = Hod.type.footnote.copy(fontWeight = FontWeight.Medium), color = c.tint)
                }
            }
        }
    }
}
