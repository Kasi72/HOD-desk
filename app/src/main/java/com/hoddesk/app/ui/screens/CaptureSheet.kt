package com.hoddesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hoddesk.app.privacy.ConsentScope
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.IconTile
import com.hoddesk.app.ui.components.SectionHeader
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles

data class ProcessingItem(val title: String, val detail: String, val progress: Float?)

enum class CaptureKind(val label: String, val scope: ConsentScope?) {
    Camera("Camera", ConsentScope.SCREENSHOTS),
    Screenshot("Screenshot", ConsentScope.SCREENSHOTS),
    Document("Document", ConsentScope.DOCUMENTS),
    WhatsApp("WhatsApp", ConsentScope.MESSAGES),
    Call("Call", ConsentScope.CALLS),
    Note("Note", null),
}

@Composable
fun CaptureSheet(
    allowed: (ConsentScope?) -> Boolean,
    recording: Boolean,
    recordSeconds: Int,
    processing: List<ProcessingItem>,
    onDismiss: () -> Unit,
    onToggleVoice: () -> Unit,
    onPick: (CaptureKind) -> Unit,
    onBlocked: (CaptureKind) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val c = Hod.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().pressable(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.9f).clip(Squircles.sheetTop).background(c.groupedBackground).pressable(onClick = {})
                    .navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(c.chevron))
                Box(Modifier.fillMaxWidth()) {
                    Text("Cancel", style = Hod.type.body, color = c.tint, modifier = Modifier.align(Alignment.CenterStart).pressable(onClick = onDismiss))
                    Text("Capture", style = Hod.type.headline, color = c.label, modifier = Modifier.align(Alignment.Center))
                }

                val voiceAllowed = allowed(ConsentScope.CALLS)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(Squircles.card).background(if (recording) c.red else c.tint)
                        .pressable { if (voiceAllowed) onToggleVoice() else onBlocked(CaptureKind.Call) }
                        .semantics { contentDescription = if (recording) "Stop recording voice note" else "Record a voice note" }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(if (recording) Icons.Rounded.Stop else if (voiceAllowed) Icons.Rounded.Mic else Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (recording) "Recording · ${recordSeconds / 60}:${(recordSeconds % 60).toString().padStart(2, '0')}" else "Speak a Note", style = Hod.type.headline, color = Color.White)
                        Text(if (recording) "Tap to stop and transcribe" else if (voiceAllowed) "Tamil or English · stays on this phone" else "Voice notes are off in Privacy", style = Hod.type.subheadline, color = Color.White.copy(alpha = 0.85f))
                    }
                }

                val tiles = listOf(
                    Triple(CaptureKind.Camera, Icons.Rounded.PhotoCamera, c.gray),
                    Triple(CaptureKind.Screenshot, Icons.Rounded.Smartphone, c.indigo),
                    Triple(CaptureKind.Document, Icons.Rounded.Description, c.blue),
                    Triple(CaptureKind.WhatsApp, Icons.Rounded.Chat, c.green),
                    Triple(CaptureKind.Call, Icons.Rounded.Call, c.orange),
                    Triple(CaptureKind.Note, Icons.Rounded.EditNote, c.yellow),
                )
                tiles.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (kind, icon, color) ->
                            val ok = allowed(kind.scope)
                            CaptureTile(kind.label, if (ok) icon else Icons.Rounded.Lock, if (ok) color else c.gray, locked = !ok, modifier = Modifier.weight(1f)) {
                                if (ok) onPick(kind) else onBlocked(kind)
                            }
                        }
                    }
                }

                if (processing.isNotEmpty()) Column {
                    SectionHeader("Processing")
                    Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface)) {
                        processing.forEach { p ->
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.title, style = Hod.type.body, color = c.label)
                                Text(p.detail, style = Hod.type.footnote, color = c.secondaryLabel)
                                p.progress?.let { progress ->
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.fill)) {
                                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(c.tint))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Text("Everything stays encrypted on this phone. Phone numbers and emails are hidden, and patient details are kept out of search. ", style = Hod.type.footnote, color = c.secondaryLabel, modifier = Modifier.weight(1f))
                }
                Text("Privacy Settings", style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.padding(horizontal = 16.dp).pressable(onClick = onOpenPrivacy))
            }
        }
    }
}

@Composable
private fun CaptureTile(label: String, icon: ImageVector, color: Color, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(96.dp).clip(Squircles.card).background(Hod.colors.surface).pressable(onClick = onClick)
            .semantics { contentDescription = label + if (locked) ", turned off in Privacy" else "" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        IconTile(icon, color, size = 40.dp, iconTint = if (color == Hod.colors.yellow) Hod.colors.label else Color.White)
        Spacer(Modifier.height(8.dp))
        Text(label, style = Hod.type.footnote.copy(fontWeight = FontWeight.Medium), color = if (locked) Hod.colors.secondaryLabel else Hod.colors.label, textAlign = TextAlign.Center)
    }
}

/** Typed or pasted note. The title is optional and generated from the first line when blank. */
@Composable
fun NoteDialog(onDismiss: () -> Unit, onSave: (title: String, body: String, type: String) -> Unit) {
    val c = Hod.colors
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Manual note") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New Note", style = Hod.type.headline, color = c.label)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, minLines = 4, shape = Squircles.small, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What needs attention?") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.tint, cursorColor = c.tint),
            )
            Row(
                Modifier.fillMaxWidth().clip(Squircles.small).background(c.tintSoft).pressable {
                    clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { text = it; type = "WhatsApp" }
                }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.ContentPaste, null, tint = c.tint, modifier = Modifier.size(18.dp))
                Text("Paste copied WhatsApp message", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HodButton("Cancel", onDismiss, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.weight(1f))
                HodButton("Save", {
                    if (text.isNotBlank()) {
                        val title = text.lineSequence().first { it.isNotBlank() }.trim().take(60)
                        onSave(if (type == "WhatsApp") "WhatsApp message" else title, text.trim(), type)
                    }
                }, enabled = text.isNotBlank(), height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}
