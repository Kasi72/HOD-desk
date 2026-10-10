package com.hoddesk.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.hoddesk.app.data.AuditEntryEntity
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoddesk.app.calendar.CalendarProviderScheduler
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.HodDeskDatabase
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.repository.HodDeskRepository
import com.hoddesk.app.viewmodel.HodDeskViewModel
import com.hoddesk.app.viewmodel.HodDeskViewModelFactory
import com.hoddesk.app.imports.AudioTranscriptImportAdapter
import com.hoddesk.app.imports.OfflineAudioTranscriptionRuntime
import com.hoddesk.app.imports.ImportedSource
import kotlinx.coroutines.launch
import com.hoddesk.app.imports.ScreenshotImportAdapter
import com.hoddesk.app.imports.WhatsAppExportAdapter
import com.hoddesk.app.imports.OfficialDocumentImportAdapter
import com.hoddesk.app.imports.ActionPointExtractor
import com.hoddesk.app.repository.ScheduleProposal
import com.hoddesk.app.semantic.EmbeddingGemmaLiteRtRuntime
import com.hoddesk.app.semantic.EmbeddingGemmaActionRanker
import com.hoddesk.app.semantic.SourceSemanticHit
import com.hoddesk.app.semantic.SourceSemanticIndexer
import com.hoddesk.app.reasoning.ActionReasoningOutput
import com.hoddesk.app.reasoning.InstructionModelStore
import com.hoddesk.app.reasoning.OnDeviceActionReasoner
import com.hoddesk.app.scheduling.PlannedBlock
import com.hoddesk.app.scheduling.SmartScheduling
import com.hoddesk.app.privacy.PrivacyStore
import com.hoddesk.app.privacy.ConsentScope
import java.util.Calendar
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Teal = Color(0xFF0E7468)
private val Ink = Color(0xFF173530)
private val Mist = Color(0xFFF6F8F7)
private val Soft = Color(0xFFE2F1ED)
private val Amber = Color(0xFFFFF0D2)
private val Squircle = RoundedCornerShape(22.dp)
private val FieldShape = RoundedCornerShape(16.dp)
private val PagePadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
private val CardPadding = 16.dp

data class DeskTask(val id: Int, val title: String, val project: String, val due: String, val priority: String, val done: Boolean = false, val sourceId: Long? = null, val status: String = "open")
data class DeskSource(val id: Int, val title: String, val detail: String, val type: String, val reviewed: Boolean = false, val bucket: String = "FYI", val priorityReasons: String? = null)
private data class DeleteTarget(val kind: String, val id: Long, val label: String, val ids: List<Long> = listOf(id))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedUri = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null
        setContent { HODDeskApp(sharedUri) }
    }
}

@Composable
fun HODDeskApp(initialSharedUri: Uri? = null) {
    val context = LocalContext.current
    val privacyStore = remember { PrivacyStore(context) }
    val hodDeskViewModel: HodDeskViewModel = viewModel(factory = HodDeskViewModelFactory(HodDeskRepository(HodDeskDatabase.get(context).dao())))
    val database = remember { HodDeskDatabase.get(context) }
    val embeddingRuntime = remember { EmbeddingGemmaLiteRtRuntime(context) }
    val instructionModelStore = remember { InstructionModelStore(context, BuildConfig.INSTRUCTION_MODEL_URL, BuildConfig.INSTRUCTION_MODEL_SHA256.ifBlank { null }) }
    val installedInstructionModel = remember { instructionModelStore.installedPath() }
    val semanticIndexer = remember { SourceSemanticIndexer(database.dao(), embeddingRuntime) }
    val persistedTasks by hodDeskViewModel.tasks.collectAsState()
    val persistedSources by hodDeskViewModel.sources.collectAsState()
    val tasks = remember { mutableStateListOf(
        DeskTask(1, "Approve psychiatry resident rotation plan", "Department", "Today · 11:30", "High"),
        DeskTask(2, "Send committee response on CBT workshop", "Academic", "Today · 16:00", "Medium"),
        DeskTask(3, "Review procurement note for ward tablets", "Operations", "Tomorrow", "Medium"),
        DeskTask(4, "Prepare monthly service dashboard", "Reporting", "Fri, 10 Oct", "Low")
    ) }
    val sources = remember { mutableStateListOf(
        DeskSource(1, "Official group message", "Please share the final resident duty roster before 4 PM today.", "WhatsApp"),
        DeskSource(2, "Meeting capture · projector slide", "Submit the revised tele-psychiatry SOP to the dean's office this week.", "Screenshot"),
        DeskSource(3, "Call insight · 08 Oct", "Follow up with stores about the pending ward tablet quotation.", "Call recording")
    ) }
    LaunchedEffect(persistedTasks) {
        if (persistedTasks.isNotEmpty()) {
            tasks.clear()
            tasks.addAll(persistedTasks.map { DeskTask(it.id.toInt(), it.title, it.project, it.dueAt?.let { due -> java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(due)) } ?: "Needs scheduling", it.priority.replaceFirstChar(Char::uppercase), it.status == "completed", it.sourceId, it.status) })
        }
    }
    LaunchedEffect(persistedSources) {
        if (persistedSources.isNotEmpty()) {
            sources.clear()
            sources.addAll(persistedSources.map { DeskSource(it.id.toInt(), it.title, it.body, it.sourceType, it.reviewed, it.triageBucket, it.priorityReasons) })
        }
    }
    LaunchedEffect(Unit) {
        if (persistedSources.isEmpty()) {
            listOf(
                SourceEntity(title = "Official group message", body = "Please share the final resident duty roster before 4 PM today.", sourceType = "WhatsApp"),
                SourceEntity(title = "Meeting capture · projector slide", body = "Submit the revised tele-psychiatry SOP to the dean's office this week.", sourceType = "Screenshot"),
                SourceEntity(title = "Call insight · 08 Oct", body = "Follow up with stores about the pending ward tablet quotation.", sourceType = "Call recording")
            ).forEach { hodDeskViewModel.ingest(it) }
        }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf("home") }
    var showCapture by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf<DeskSource?>(null) }
    var selectedTask by remember { mutableStateOf<DeskTask?>(null) }
    var showScheduleConfirmation by remember { mutableStateOf(false) }
    var selectedDayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var librarySelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var proposedPlan by remember { mutableStateOf<List<PlannedBlock>?>(null) }
    val importScope = rememberCoroutineScope()
    var semanticQuery by rememberSaveable { mutableStateOf("") }
    var semanticHits by remember { mutableStateOf<List<SourceSemanticHit>>(emptyList()) }
    var embeddingStatus by remember { mutableStateOf("EmbeddingGemma is idle") }
    val audioRuntime = remember { OfflineAudioTranscriptionRuntime(context) }
    val selectedDay = remember(selectedDayEpoch) { LocalDate.ofEpochDay(selectedDayEpoch) }
    val dayStart = remember(selectedDay) { selectedDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val dayEnd = remember(selectedDay) { selectedDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val calendarEvents by database.dao().observeEvents(dayStart, dayEnd).collectAsState(emptyList())
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { embeddingRuntime.close() } }
    LaunchedEffect(persistedSources) {
        if (persistedSources.isNotEmpty()) {
            Log.i("HOD_SEMANTIC", "index_requested_sources=${persistedSources.size}")
            embeddingStatus = "Indexing ${persistedSources.size} source(s) on device…"
            runCatching { semanticIndexer.indexAll(persistedSources.filter { it.sensitivity != "T3" && it.sensitivity != "T4" }) }
                .onSuccess { embeddingStatus = "EmbeddingGemma index ready"; Log.i("HOD_SEMANTIC", "index_ready_sources=${persistedSources.size}") }
                .onFailure { embeddingStatus = "Index unavailable: ${it.message ?: it.javaClass.simpleName}"; Log.e("HOD_SEMANTIC", "index_failed", it) }
            if (semanticQuery.isNotBlank()) {
                semanticHits = runCatching { semanticIndexer.search(semanticQuery, persistedSources.filter { it.sensitivity != "T3" && it.sensitivity != "T4" }) }.onFailure { Log.e("HOD_SEMANTIC", "search_failed", it) }.getOrDefault(emptyList())
            }
        }
    }
    fun saveImported(item: com.hoddesk.app.imports.ImportedSource, uri: android.net.Uri) {
        val kind = item.type.lowercase()
        val scope = when {
            kind.contains("call") || kind.contains("audio") -> ConsentScope.CALLS
            kind.contains("whatsapp") || kind.contains("message") -> ConsentScope.MESSAGES
            kind.contains("screenshot") || kind.contains("camera") -> ConsentScope.SCREENSHOTS
            kind.contains("document") || kind.contains("gmail") || kind.contains("pdf") -> ConsentScope.DOCUMENTS
            else -> null
        }
        if (scope != null && (!privacyStore.canImportSensitive() || !privacyStore.scopeGranted(scope))) {
            importStatus = "Privacy consent required. Open the top-right menu and enable sensitive imports."
            return
        }
        val redactedInput = privacyStore.redact(item.body)
        val body = if (redactedInput.contains("--- HOD Desk review cues")) redactedInput else ActionPointExtractor().annotate(redactedInput)
        sources.add(0, DeskSource((sources.maxOfOrNull { it.id } ?: 0) + 1, item.title, body, item.type))
        val needsReview = body.contains("pending OCR", ignoreCase = true) || body.contains("no readable text", ignoreCase = true) || body.contains("No definite action", ignoreCase = true)
        val source = SourceEntity(title = item.title, body = body, sourceType = item.type, sourceUri = uri.toString(), processingStatus = if (needsReview) "needs_review" else "ready")
        hodDeskViewModel.ingestWithActions(source, ActionPointExtractor().extract(redactedInput))
    }
    fun saveImportedBatch(items: List<ImportedSource>, uri: android.net.Uri) = items.forEach { saveImported(it, uri) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = cameraUri
        if (captured && uri != null) {
            importStatus = "Reading camera document…"
            ScreenshotImportAdapter().recognize(context, uri)
                .addOnSuccessListener { item ->
                    val reviewed = item.copy(title = "Camera document · ${item.title}", type = "Camera document")
                    saveImported(reviewed, uri)
                    importStatus = "Camera document OCR added for action review"
                }
                .addOnFailureListener {
                    saveImported(ImportedSource("Camera document", "OCR could not read this image; manual review required.", "Camera document", uri), uri)
                    importStatus = "Camera image saved for manual review"
                }
        }
        cameraUri = null
    }
    fun captureDocument() {
        val file = File(context.cacheDir, "camera").apply { mkdirs() }
            .let { File.createTempFile("hod-document-", ".jpg", it) }
        val outputUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = outputUri
        cameraLauncher.launch(outputUri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (mime.startsWith("image/")) {
                importStatus = "Reading screenshot…"
                ScreenshotImportAdapter().recognize(context, uri)
                    .addOnSuccessListener { item -> saveImported(item, uri); importStatus = "Screenshot added for review" }
                    .addOnFailureListener { importStatus = "OCR failed; screenshot saved for manual review"; saveImported(ScreenshotImportAdapter().create(uri), uri) }
            } else {
                val imported = if (mime.startsWith("audio/")) {
                    importStatus = "Transcribing Tamil and English speech offline…"
                    importScope.launch {
                        runCatching { audioRuntime.transcribeWav(uri) }
                            .onSuccess { text -> saveImported(ImportedSource("Tamil-first call transcript", text.ifBlank { "No speech detected" }, "Audio transcript", uri), uri); importStatus = "Tamil-first offline transcript added for review" }
                            .onFailure { error ->
                                Log.e("HOD_AUDIO", "transcription_failed", error)
                                saveImported(AudioTranscriptImportAdapter(context.contentResolver).importAudio(uri), uri)
                                importStatus = "Recording queued; transcription unavailable: ${error.message ?: error.javaClass.simpleName}"
                            }
                    }
                    null
            } else if (mime.contains("pdf") || mime.contains("word") || mime.contains("officedocument") ||
                uri.toString().substringBefore('?').substringAfterLast('.').lowercase() in setOf("pdf", "doc", "docx")) {
                runCatching { OfficialDocumentImportAdapter(context).import(uri) }
                    .onSuccess { item -> saveImported(item, uri); importStatus = "Official document extracted and queued for review" }
                    .onFailure { importStatus = "Document extraction failed: ${it.message ?: it.javaClass.simpleName}" }
                null
            } else if (mime == "text/vtt" || mime == "text/plain") {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val whatsappItems = WhatsAppExportAdapter().parse(text)
                if (whatsappItems.isNotEmpty()) {
                    saveImportedBatch(whatsappItems, uri)
                    importStatus = "${whatsappItems.size} WhatsApp messages added for review"
                    null
                } else {
                    AudioTranscriptImportAdapter(context.contentResolver).importTranscript(uri)
                }
            } else if (mime == "application/zip" || mime == "application/x-zip-compressed" || mime == "application/octet-stream") {
                val items = context.contentResolver.openInputStream(uri)?.use { WhatsAppExportAdapter().parseZip(it) }.orEmpty()
                saveImportedBatch(items, uri)
                importStatus = if (items.isEmpty()) "No WhatsApp chat text found in ZIP" else "${items.size} WhatsApp messages added for review"
                null
            } else context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val items = WhatsAppExportAdapter().parse(reader.readText())
                    saveImportedBatch(items, uri)
                    importStatus = if (items.isEmpty()) "No WhatsApp messages found" else "${items.size} WhatsApp messages added for review"
                    null
                }
                imported?.let { item -> saveImported(item, uri) }
            }
        }
    }
    LaunchedEffect(initialSharedUri) {
        initialSharedUri?.let { uri ->
            runCatching { OfficialDocumentImportAdapter(context).import(uri, "Gmail attachment") }
                .onSuccess { item -> saveImported(item, uri); importStatus = "Gmail attachment extracted and queued for review" }
                .onFailure { importStatus = "Gmail attachment could not be read: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val tabs = listOf("Today", "Inbox", "Tasks", "Calendar", "Library")
    val icons = listOf(Icons.Outlined.Today, Icons.Outlined.Inbox, Icons.Outlined.TaskAlt, Icons.Outlined.CalendarMonth, Icons.Outlined.LibraryBooks)

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Teal, onPrimary = Color.White, background = Mist, surface = Color.White, onSurface = Ink), typography = Typography(), shapes = Shapes(extraSmall = FieldShape, small = FieldShape, medium = Squircle, large = Squircle, extraLarge = Squircle)) {
        Scaffold(
            containerColor = Mist,
            topBar = { TopBar(tab, tabs[tab], onMore = { showPrivacy = true }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { showCapture = true }, shape = Squircle, containerColor = Teal, contentColor = Color.White) {
                    Icon(Icons.Outlined.Add, "Capture")
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], label) }, label = { Text(label, fontSize = 11.sp, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Teal, selectedTextColor = Teal, unselectedIconColor = Color(0xFF687773), unselectedTextColor = Color(0xFF687773), indicatorColor = Soft))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> TodayScreen(tasks, sources, onReview = { selectedSource = it }, onToggleTask = { id -> toggleTask(tasks, id); hodDeskViewModel.setDone(id.toLong(), tasks.firstOrNull { it.id == id }?.done == true) }, onDeleteTask = { task -> deleteTarget = DeleteTarget("task", task.id.toLong(), task.title) }, onDeleteSource = { source -> deleteTarget = DeleteTarget("source", source.id.toLong(), source.title) }, onPlanDay = {
                        proposedPlan = SmartScheduling.propose(persistedTasks, calendarEvents, dayStart + 9 * 60 * 60 * 1000L, dayStart + 17 * 60 * 60 * 1000L)
                    }, onTab = { tab = it })
                    1 -> InboxScreen(sources, onReview = { selectedSource = it }, onDelete = { source -> deleteTarget = DeleteTarget("source", source.id.toLong(), source.title) })
                    2 -> TasksScreen(tasks, onToggle = { id -> toggleTask(tasks, id); hodDeskViewModel.setDone(id.toLong(), tasks.firstOrNull { it.id == id }?.done == true) }, onOpen = { selectedTask = it }, onDelete = { task -> deleteTarget = DeleteTarget("task", task.id.toLong(), task.title) })
                    3 -> CalendarScreen(selectedDay, calendarEvents, onDayChange = { selectedDayEpoch = it.toEpochDay() }, onAddBlock = { showScheduleConfirmation = true }, onDelete = { event -> deleteTarget = DeleteTarget("event", event.id, event.title) })
                    else -> LibraryScreen(sources, semanticQuery, semanticHits, embeddingStatus, selectedIds = librarySelection, onToggleSelection = { id -> librarySelection = if (id in librarySelection) librarySelection - id else librarySelection + id }, onSelectAll = { librarySelection = sources.map { it.id }.toSet() }, onClearSelection = { librarySelection = emptySet() }, onDeleteSelected = {
                        if (librarySelection.isNotEmpty()) deleteTarget = DeleteTarget("sources", -1, "${librarySelection.size} library item(s)", librarySelection.map { it.toLong() })
                    }, onQueryChange = { query ->
                        semanticQuery = query
                        importScope.launch {
                            semanticHits = if (query.isBlank()) emptyList() else runCatching { semanticIndexer.search(query, persistedSources.filter { it.sensitivity != "T3" && it.sensitivity != "T4" }) }.onFailure { Log.e("HOD_SEMANTIC", "search_failed", it) }.getOrDefault(emptyList())
                        }
                    }, onDelete = { source -> deleteTarget = DeleteTarget("source", source.id.toLong(), source.title) })
                }
            }
        }
    }

    if (showCapture) CaptureDialog(onDismiss = { showCapture = false }, onCamera = { showCapture = false; captureDocument() }, onImport = { importLauncher.launch(arrayOf("text/*", "image/*", "audio/*", "application/zip", "application/octet-stream", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }, onSave = { title, detail, type ->
        sources.add(0, DeskSource((sources.maxOfOrNull { it.id } ?: 0) + 1, title, detail, type))
        hodDeskViewModel.ingest(SourceEntity(title = title, body = detail, sourceType = type))
        showCapture = false
    })
    selectedSource?.let { source ->
        val persistedActionPoints by database.dao().observeActionPoints(source.id.toLong()).collectAsState(emptyList())
        SourceSheet(source, actionPoints = persistedActionPoints, allSources = persistedSources.filter { it.sensitivity != "T3" && it.sensitivity != "T4" }, semanticIndexer = semanticIndexer, embeddingReady = embeddingStatus == "EmbeddingGemma index ready", ranker = EmbeddingGemmaActionRanker(embeddingRuntime), instructionModelPath = installedInstructionModel, onReasoning = { output -> hodDeskViewModel.saveReasoning(source.id.toLong(), output) }, onStatus = { action, status -> hodDeskViewModel.setActionPointStatus(source.id.toLong(), action, status) }, onDismiss = { selectedSource = null }, onApprove = { actionTitle ->
            tasks.add(0, DeskTask((tasks.maxOfOrNull { it.id } ?: 0) + 1, actionTitle, source.type, "Needs scheduling", "Medium", sourceId = source.id.toLong()))
            hodDeskViewModel.approveAction(source.id.toLong(), actionTitle, source.type)
            val sourceIndex = sources.indexOfFirst { it.id == source.id }
            if (sourceIndex >= 0) sources[sourceIndex] = source.copy(reviewed = true)
            selectedSource = null
        })
    }
    selectedTask?.let { task ->
        val auditEntries by database.dao().observeAudit("task", task.id.toLong()).collectAsState(emptyList())
        TaskDetailDialog(task, source = persistedSources.firstOrNull { it.id.toLong() == task.sourceId }?.let { DeskSource(it.id.toInt(), it.title, it.body, it.sourceType, it.reviewed) }, auditEntries = auditEntries, onDismiss = { selectedTask = null }, onDone = { hodDeskViewModel.setDone(task.id.toLong(), it) }, onBlocker = { hodDeskViewModel.setBlocker(task.id.toLong(), it) }, onClarification = { hodDeskViewModel.clarify(task.id.toLong(), it) })
    }
    if (showScheduleConfirmation) CalendarConfirmationDialog(
        onDismiss = { showScheduleConfirmation = false },
        onConfirm = {
            val required = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            val granted = required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            if (!granted) {
                calendarPermissionLauncher.launch(required)
            } else {
                val scheduler = CalendarProviderScheduler.fromResolver(context.contentResolver) { permission -> context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED }
                val start = selectedDay.atTime(13, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val proposal = ScheduleProposal(EventEntity(title = "Protected Psychiatry HOD Desk focus block", startsAt = start, endsAt = start + 60 * 60 * 1000), emptyList())
                val calendarId = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
                if (calendarId != null) scheduler.writeConfirmed(proposal, calendarId, confirmed = true)
                showScheduleConfirmation = false
            }
        }
    )
    if (showPrivacy) SettingsDialog(privacyStore, page = settingsPage, onPage = { settingsPage = it }, onDismiss = { showPrivacy = false })
    importStatus?.let { status ->
        AlertDialog(onDismissRequest = { importStatus = null }, title = { Text("Import") }, text = { Text(status) }, confirmButton = { TextButton(onClick = { importStatus = null }) { Text("OK") } })
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.kind}?") },
            text = { Text("Delete \"${target.label}\" permanently from Psychiatry HOD Desk?") },
            confirmButton = { Button(onClick = { when (target.kind) { "source" -> hodDeskViewModel.deleteSource(target.id); "sources" -> target.ids.forEach { hodDeskViewModel.deleteSource(it) }; "task" -> hodDeskViewModel.deleteTask(target.id); "event" -> hodDeskViewModel.deleteEvent(target.id) }; if (target.kind == "sources") librarySelection = emptySet(); deleteTarget = null }, shape = Squircle) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
    proposedPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { proposedPlan = null },
            title = { Text("Proposed day plan") },
            text = {
                if (plan.isEmpty()) Text("No open tasks fit today’s available time. Add a due date or free a calendar block.")
                else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Review these blocks before they are added to your calendar.", color = Color(0xFF5F6D69), fontSize = 13.sp)
                    plan.forEach { block ->
                        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        Card(shape = FieldShape, colors = CardDefaults.cardColors(containerColor = Soft)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(block.task.title, color = Ink, fontWeight = FontWeight.SemiBold)
                                Text("${time.format(java.util.Date(block.start))} – ${time.format(java.util.Date(block.end))}", color = Teal, fontSize = 13.sp)
                                Text(block.rationale, color = Color(0xFF5F6D69), fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(enabled = plan.isNotEmpty(), onClick = { plan.forEach { hodDeskViewModel.schedulePlannedTask(it.task.id, it.task.title, it.start, it.end) }; proposedPlan = null }, shape = Squircle) { Text("Confirm plan") } },
            dismissButton = { TextButton(onClick = { proposedPlan = null }) { Text("Keep unscheduled") } }
        )
    }
}

private fun toggleTask(tasks: androidx.compose.runtime.snapshots.SnapshotStateList<DeskTask>, id: Int) {
    val index = tasks.indexOfFirst { it.id == id }
    if (index >= 0) tasks[index] = tasks[index].copy(done = !tasks[index].done)
}

@Composable
private fun TopBar(tab: Int, title: String, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().background(Mist).padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Psychiatry HOD Desk", color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
        IconButton(onClick = {}) { Icon(Icons.Outlined.NotificationsNone, "Notifications", tint = Ink) }
        IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreHoriz, "Privacy and settings", tint = Ink) }
    }
}

@Composable
private fun SettingsDialog(store: PrivacyStore, page: String, onPage: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), color = Mist) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page != "home") IconButton(onClick = { onPage("home") }) { Icon(Icons.Outlined.ArrowBack, "Back") }
                    Column(Modifier.weight(1f)) { Text("Psychiatry HOD Desk", color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(if (page == "home") "Settings" else when (page) { "privacy" -> "Privacy & Data"; "ai" -> "On-Device AI"; "activity" -> "Activity Log"; else -> "How to Import" }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 28.sp) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close settings") }
                }
                when (page) {
                    "privacy" -> PrivacySettingsDialogContent(store)
                    "ai" -> SettingsAiPage()
                    "activity" -> SettingsActivityPage()
                    "howto" -> SettingsHowToPage()
                    else -> SettingsHome(onPage)
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(onPage: (String) -> Unit) {
    var morning by rememberSaveable { mutableStateOf(true) }
    var interruptions by rememberSaveable { mutableStateOf(true) }
    var setupTeam by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(shape = Squircle, colors = CardDefaults.cardColors(containerColor = Soft), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Finish setup · 2 of 4", color = Teal, fontWeight = FontWeight.Bold); Text("Add your team and tag unknown senders so scheduling and urgency stay accurate.", color = Ink, fontSize = 13.sp); OutlinedButton(onClick = { setupTeam = !setupTeam }, shape = Squircle) { Text(if (setupTeam) "Setup checklist open" else "Continue setup") } } } }
        item { Text("Security & data", color = Color(0xFF6D7D79), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        item { SettingsRow("Privacy & Data", "Consent, redaction, retention and source locks", Icons.Outlined.Lock, onClick = { onPage("privacy") }) }
        item { SettingsRow("Clinical Vault", "Fingerprint unlock for sensitive clinical material", Icons.Outlined.Lock, onClick = {}) }
        item { SettingsRow("Activity Log", "Encrypted audit history and reviewer export", Icons.Outlined.Description, onClick = { onPage("activity") }) }
        item { SettingsRow("How to Import", "WhatsApp exports, screenshots, calls and documents", Icons.Outlined.Inbox, onClick = { onPage("howto") }) }
        item { Text("Intelligence", color = Color(0xFF6D7D79), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        item { SettingsRow("On-Device AI", "Search, speech, image reading and model health", Icons.Outlined.MenuBook, onClick = { onPage("ai") }) }
        item { SettingsRow("Department Profile", "OPD days, ward rounds, ECT and teaching slots", Icons.Outlined.CalendarMonth, onClick = {}) }
        item { SettingsRow("Team and urgency", "Roles, topics and who outranks whom", Icons.Outlined.TaskAlt, onClick = {}) }
        item { SettingsRow("Venues and travel", "Travel time, holidays and calendar destination", Icons.Outlined.EventNote, onClick = {}) }
        item { Text("Notifications", color = Color(0xFF6D7D79), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        item { SettingsSwitch("Morning Brief", "Choose the time in a future scheduling pass", morning) { morning = it } }
        item { SettingsSwitch("Protect focus from interruptions", "Limit interruptions per day", interruptions) { interruptions = it } }
        item { SettingsSwitch("Battery-aware processing", "Heavy work only while charging; pause below 20%", true) {} }
    }
}

@Composable private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = FieldShape, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Teal, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color(0xFF6D7D79), fontSize = 12.sp) }; Icon(Icons.Outlined.ArrowForward, null, tint = Color(0xFF87938F), modifier = Modifier.size(18.dp)) } }
}

@Composable private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(shape = FieldShape, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color(0xFF6D7D79), fontSize = 12.sp) }; Switch(checked = checked, onCheckedChange = onChecked) } }
}

@Composable private fun PrivacySettingsDialogContent(store: PrivacyStore) { Column(Modifier.verticalScroll(rememberScrollState()).padding(PagePadding)) { PrivacySettingsForm(store) } }

@Composable private fun PrivacySettingsForm(store: PrivacyStore) {
    var consent by remember { mutableStateOf(store.consentGranted) }
    var redact by remember { mutableStateOf(store.redactIdentifiers) }
    var calls by remember { mutableStateOf(store.scopeGranted(ConsentScope.CALLS)) }
    var messages by remember { mutableStateOf(store.scopeGranted(ConsentScope.MESSAGES)) }
    var screenshots by remember { mutableStateOf(store.scopeGranted(ConsentScope.SCREENSHOTS)) }
    var documents by remember { mutableStateOf(store.scopeGranted(ConsentScope.DOCUMENTS)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSwitch("Ask Before Saving Sensitive Imports", "Require consent before calls, messages, images and documents are stored", consent) { consent = it; store.consentGranted = it }
        SettingsSwitch("Hide phone numbers", "Redact identifiers before storage and indexing", redact) { redact = it; store.redactIdentifiers = it }
        SettingsSwitch("Hide email addresses", "Redact email identifiers before storage and indexing", redact) { redact = it; store.redactIdentifiers = it }
        Text("Source access", color = Teal, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        SettingsSwitch("Calls", "Call recordings and audio transcripts", calls) { calls = it; store.setScope(ConsentScope.CALLS, it) }
        SettingsSwitch("WhatsApp and messages", "Exported chats and copied messages", messages) { messages = it; store.setScope(ConsentScope.MESSAGES, it) }
        SettingsSwitch("Screenshots and camera", "Screenshots, photographs and OCR", screenshots) { screenshots = it; store.setScope(ConsentScope.SCREENSHOTS, it) }
        SettingsSwitch("Documents and Gmail", "PDF, Word, text and shared attachments", documents) { documents = it; store.setScope(ConsentScope.DOCUMENTS, it) }
        Text("Retention: ${store.retentionDays} days. Approved tasks are kept.", color = Color(0xFF6D7D79), fontSize = 12.sp)
    }
}

@Composable private fun SettingsAiPage() { LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SettingsRow("Semantic search", "EmbeddingGemma index · on this device", Icons.Outlined.Search, {}) }; item { SettingsRow("Speech", "Tamil-first offline transcription", Icons.Outlined.EventNote, {}) }; item { SettingsRow("Image reading", "Screenshot and camera OCR", Icons.Outlined.Description, {}) }; item { SettingsRow("Deeper Understanding", "Local Gemma instruction model · Get", Icons.Outlined.MenuBook, {}) }; item { Text("Last self-check: available after the next model/index check", color = Color(0xFF6D7D79), fontSize = 13.sp) }; item { Text("Technical Details", color = Teal, fontWeight = FontWeight.SemiBold); Text("Model, prompt registry, vector dimension and index version are stored locally for security review.", color = Color(0xFF6D7D79), fontSize = 13.sp) } } }

@Composable private fun SettingsActivityPage() { LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Encrypted audit history is recorded for imports, approvals, scheduling, completion and deletion.", color = Ink) }; item { Button(onClick = {}, shape = Squircle, modifier = Modifier.fillMaxWidth()) { Text("Export Activity Log for Review") } }; item { Text("Export creates a review copy only after explicit user action.", color = Color(0xFF6D7D79), fontSize = 12.sp) } } }

@Composable private fun SettingsHowToPage() { LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("WhatsApp", color = Teal, fontWeight = FontWeight.Bold); Text("Turn off Advanced Chat Privacy, export the chat, then choose the ZIP file with Import from device.", color = Ink) }; item { Text("Screenshots and camera", color = Teal, fontWeight = FontWeight.Bold); Text("Choose an image or use the camera tile. OCR text stays linked to the original image.", color = Ink) }; item { Text("Calls", color = Teal, fontWeight = FontWeight.Bold); Text("Choose an M4A/WAV recording. Tamil-first offline transcription runs before action review.", color = Ink) }; item { Text("Documents and Gmail", color = Teal, fontWeight = FontWeight.Bold); Text("Share or choose PDF, Word or text attachments. The original URI and page evidence are preserved.", color = Ink) } } }

@Composable
private fun PrivacySettingsDialog(store: PrivacyStore, onDismiss: () -> Unit) {
    var consent by remember { mutableStateOf(store.consentGranted) }
    var redact by remember { mutableStateOf(store.redactIdentifiers) }
    var retention by remember { mutableStateOf(store.retentionDays.toString()) }
    var calls by remember { mutableStateOf(store.scopeGranted(ConsentScope.CALLS)) }
    var messages by remember { mutableStateOf(store.scopeGranted(ConsentScope.MESSAGES)) }
    var screenshots by remember { mutableStateOf(store.scopeGranted(ConsentScope.SCREENSHOTS)) }
    var documents by remember { mutableStateOf(store.scopeGranted(ConsentScope.DOCUMENTS)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy and retention", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sensitive sources stay on this device and in the encrypted database.", color = Color(0xFF5F6D69), fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Consent to sensitive imports", color = Ink, fontWeight = FontWeight.Medium); Text("Calls, messages, screenshots and documents", color = Color(0xFF6D7D79), fontSize = 12.sp) }; Switch(checked = consent, onCheckedChange = { consent = it }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Redact phone numbers and email addresses", color = Ink, fontWeight = FontWeight.Medium); Text("Applied before sensitive text is stored", color = Color(0xFF6D7D79), fontSize = 12.sp) }; Switch(checked = redact, onCheckedChange = { redact = it }) }
                Text("Source permissions", color = Teal, fontWeight = FontWeight.SemiBold)
                listOf("Calls" to calls, "WhatsApp and messages" to messages, "Screenshots and camera" to screenshots, "Documents and Gmail" to documents).forEach { (label, enabled) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, color = Ink, modifier = Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { value -> when (label) { "Calls" -> calls = value; "WhatsApp and messages" -> messages = value; "Screenshots and camera" -> screenshots = value; else -> documents = value } }) }
                }
                OutlinedTextField(retention, { retention = it.filter(Char::isDigit).take(4) }, label = { Text("Retention period (days)") }, modifier = Modifier.fillMaxWidth(), shape = FieldShape, singleLine = true)
                Text("Current policy: ${retention.toIntOrNull()?.coerceIn(1, 3650) ?: 90} days. Cleanup is always explicit.", color = Color(0xFF6D7D79), fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { store.consentGranted = consent; store.redactIdentifiers = redact; store.retentionDays = retention.toIntOrNull()?.coerceIn(1, 3650) ?: 90; store.setScope(ConsentScope.CALLS, calls); store.setScope(ConsentScope.MESSAGES, messages); store.setScope(ConsentScope.SCREENSHOTS, screenshots); store.setScope(ConsentScope.DOCUMENTS, documents); onDismiss() }, shape = Squircle) { Text("Save policy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TodayScreen(tasks: List<DeskTask>, sources: List<DeskSource>, onReview: (DeskSource) -> Unit, onToggleTask: (Int) -> Unit, onDeleteTask: (DeskTask) -> Unit, onDeleteSource: (DeskSource) -> Unit, onPlanDay: () -> Unit, onTab: (Int) -> Unit) {
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Wednesday, 8 October", color = Color(0xFF6D7D79), fontSize = 14.sp) }
        item {
            Card(shape = Squircle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Teal)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Good morning, Dr. Krishnan", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Your day is clear enough for the work that needs judgment.", color = Color.White.copy(alpha = .82f))
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("${tasks.count { !it.done }}", "open tasks", Modifier.weight(1f))
                        Metric("${sources.count { !it.reviewed }}", "to review", Modifier.weight(1f))
                        Metric("3", "meetings", Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = onPlanDay, shape = Squircle, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Plan my day") }
                }
            }
        }
        item { SectionHeader("Next up", "See tasks", onClick = { onTab(2) }) }
        items(tasks.filter { !it.done }.take(3), key = { it.id }) { task -> TaskRow(task, onToggle = { onToggleTask(task.id) }, onDelete = { onDeleteTask(task) }) }
        item { SectionHeader("Needs your review", "Open inbox", onClick = { onTab(1) }) }
        items(sources.filter { !it.reviewed }.take(2), key = { it.id }) { source -> SourceRow(source, onReview, onDelete = { onDeleteSource(source) }) }
        item { Text("Today’s schedule", color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
        item { ScheduleCard() }
    }
}

@Composable private fun Metric(value: String, label: String, modifier: Modifier = Modifier) { Column(modifier) { Text(value, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(label, color = Color.White.copy(alpha = .78f), fontSize = 12.sp) } }

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f)); TextButton(onClick = onClick) { Text(action, color = Teal) } }
}

@Composable
private fun InboxScreen(sources: List<DeskSource>, onReview: (DeskSource) -> Unit, onDelete: (DeskSource) -> Unit) {
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(shape = Squircle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Soft)) { Row(Modifier.padding(CardPadding), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.FilterList, null, tint = Teal); Spacer(Modifier.width(10.dp)); Text("Unified intake · messages, calls, screenshots and documents", color = Ink) } } }
        item { Text("${sources.count { !it.reviewed }} items need review", color = Color(0xFF6D7D79), modifier = Modifier.padding(vertical = 4.dp)) }
        items(sources, key = { it.id }) { SourceRow(it, onReview, onDelete) }
    }
}

@Composable
private fun SourceRow(source: DeskSource, onReview: (DeskSource) -> Unit, onDelete: (DeskSource) -> Unit) {
    Card(shape = Squircle, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().clickable { onReview(source) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (source.reviewed) Soft else Amber, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Icon(if (source.type == "Screenshot") Icons.Outlined.Description else Icons.Outlined.Inbox, null, tint = Teal) } }
            Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(source.title, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); AssistChip(onClick = {}, label = { Text(source.bucket, fontSize = 10.sp) }, shape = RoundedCornerShape(10.dp)) }; Text(source.detail, color = Color(0xFF5F6D69), maxLines = 2, modifier = Modifier.padding(top = 4.dp)); Text(if (source.reviewed) "Reviewed" else if (source.priorityReasons.isNullOrBlank()) "Tap to extract actions" else "Because: ${source.priorityReasons}", color = Teal, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            IconButton(onClick = { onDelete(source) }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFB04B36)) }
        }
    }
}

@Composable
private fun TasksScreen(tasks: androidx.compose.runtime.snapshots.SnapshotStateList<DeskTask>, onToggle: (Int) -> Unit, onOpen: (DeskTask) -> Unit, onDelete: (DeskTask) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val visible = tasks.filter { filter == "All" || it.project == filter }
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Department", "Academic", "Operations").forEach { label -> AssistChip(onClick = { filter = label }, shape = FieldShape, label = { Text(label) }, leadingIcon = if (filter == label) ({ Icon(Icons.Outlined.CheckCircle, null) }) else null) } } }
        items(visible, key = { it.id }) { task -> TaskRow(task, onToggle = { onToggle(task.id) }, onOpen = { onOpen(task) }, onDelete = { onDelete(task) }) }
    }
}

@Composable
private fun TaskRow(task: DeskTask, onToggle: () -> Unit, onOpen: () -> Unit = {}, onDelete: () -> Unit = {}) {
    Card(shape = Squircle, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) { Text(task.title, color = if (task.done) Color.Gray else Ink, fontWeight = FontWeight.SemiBold); Text("${task.project} · ${task.due}", color = Color(0xFF6D7D79), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            Text(task.priority, color = if (task.priority == "High") Color(0xFFB04B36) else Teal, fontSize = 12.sp)
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFB04B36)) }
        }
    }
}

@Composable
private fun TaskDetailDialog(task: DeskTask, source: DeskSource?, auditEntries: List<AuditEntryEntity>, onDismiss: () -> Unit, onDone: (Boolean) -> Unit, onBlocker: (String?) -> Unit, onClarification: (String) -> Unit) {
    var blocker by remember(task.id) { mutableStateOf("") }
    var clarification by remember(task.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Task detail", color = Ink, fontWeight = FontWeight.Bold) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(task.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = {}, label = { Text(task.project) }); AssistChip(onClick = {}, label = { Text(task.priority) }); AssistChip(onClick = {}, label = { Text(if (task.done) "Completed" else "Open") }) }
            DetailLine("Due", task.due)
            Text("Workflow", color = Teal, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = task.done, onCheckedChange = onDone); Text(if (task.done) "Completed" else "Mark as completed", color = Ink) }
            OutlinedTextField(blocker, { blocker = it }, label = { Text("Blocker or dependency") }, modifier = Modifier.fillMaxWidth(), shape = FieldShape, minLines = 2)
            OutlinedTextField(clarification, { clarification = it }, label = { Text("Clarification request") }, modifier = Modifier.fillMaxWidth(), shape = FieldShape, minLines = 2)
            source?.let { Card(shape = FieldShape, colors = CardDefaults.cardColors(containerColor = Soft)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Source evidence", color = Teal, fontWeight = FontWeight.SemiBold); Text(it.type, color = Color(0xFF6D7D79), fontSize = 12.sp); Text(it.detail.take(420), color = Ink, fontSize = 13.sp) } } }
            if (auditEntries.isNotEmpty()) {
                Text("Activity history", color = Teal, fontWeight = FontWeight.SemiBold)
                auditEntries.take(8).forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.action.replace('_', ' ').replaceFirstChar(Char::uppercase), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(entry.createdAt))}${entry.detail?.let { " · $it" } ?: ""}", color = Color(0xFF6D7D79), fontSize = 11.sp)
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = { if (blocker.isNotBlank()) onBlocker(blocker); if (clarification.isNotBlank()) onClarification(clarification); onDismiss() }, shape = Squircle) { Text("Save changes") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable private fun DetailLine(label: String, value: String) { Row(Modifier.fillMaxWidth()) { Text(label, color = Color(0xFF6D7D79), modifier = Modifier.width(70.dp)); Text(value, color = Ink, fontWeight = FontWeight.Medium) } }

@Composable
private fun CalendarScreen(selectedDay: LocalDate, events: List<EventEntity>, onDayChange: (LocalDate) -> Unit, onAddBlock: () -> Unit, onDelete: (EventEntity) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    val weekStart = selectedDay.minusDays((selectedDay.dayOfWeek.value - 1).toLong())
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(selectedDay.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp); Spacer(Modifier.height(3.dp)); Text("${events.size} scheduled item(s)", color = Color(0xFF6D7D79)) }; OutlinedButton(onClick = onAddBlock, shape = Squircle, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) { Text("Add block") } } }
        item { WeekStrip(weekStart, selectedDay, onDayChange) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onDayChange(selectedDay.minusDays(1)) }) { Icon(Icons.Outlined.ArrowBack, "Previous day") }; Text(selectedDay.format(formatter), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f)); IconButton(onClick = { onDayChange(selectedDay.plusDays(1)) }) { Icon(Icons.Outlined.ArrowForward, "Next day") } } }
        item { Card(shape = Squircle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(horizontal = CardPadding, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { if (events.isEmpty()) Text("No events scheduled for this day.", color = Color(0xFF6D7D79)) else events.forEach { event -> EventCard(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(event.startsAt)), event.title, event.location ?: "HOD Desk event", Teal, onDelete = { onDelete(event) }) } } } }
    }
}

@Composable private fun WeekStrip(start: LocalDate, selected: LocalDate, onDayChange: (LocalDate) -> Unit) { Card(shape = Squircle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) { (0..6).map { start.plusDays(it.toLong()) }.forEach { day -> Surface(onClick = { onDayChange(day) }, shape = RoundedCornerShape(16.dp), color = if (day == selected) Teal else Color.Transparent, modifier = Modifier.size(width = 42.dp, height = 58.dp)) { Box(contentAlignment = Alignment.Center) { Text("${day.dayOfWeek.name.take(3).replaceFirstChar(Char::uppercase)}\n${day.dayOfMonth}", color = if (day == selected) Color.White else Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp) } } } } } }

@Composable private fun ScheduleCard() { Card(shape = Squircle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) { EventCard("09:00", "Ward rounds", "Psychiatry ward · 45 min", Teal); EventCard("13:00", "Protected focus", "Prepare service dashboard · 60 min", Color(0xFFB47725)) } } }

@Composable private fun EventCard(time: String, title: String, detail: String, color: Color, onDelete: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Text(time, color = Color(0xFF6D7D79), fontSize = 13.sp, modifier = Modifier.width(52.dp)); Box(Modifier.width(4.dp).height(48.dp).background(color, RoundedCornerShape(4.dp))); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.SemiBold); Text(detail, color = Color(0xFF6D7D79), fontSize = 12.sp) }; if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFB04B36)) } } }

@Composable
private fun LibraryScreen(
    sources: List<DeskSource>,
    query: String,
    semanticHits: List<SourceSemanticHit>,
    embeddingStatus: String,
    selectedIds: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onQueryChange: (String) -> Unit,
    onDelete: (DeskSource) -> Unit
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val filtered = if (query.isBlank() || semanticHits.isEmpty()) {
        sources.filter { query.isBlank() || "${it.title} ${it.detail} ${it.type}".contains(query, ignoreCase = true) }
    } else {
        semanticHits.mapNotNull { hit -> sources.firstOrNull { it.id.toLong() == hit.source.id } }
    }
    LazyColumn(contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectionMode) "${selectedIds.size} selected" else "Workspace library", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selectionMode) {
                    TextButton(onClick = { if (selectedIds.size == filtered.size) onClearSelection() else onSelectAll() }) { Text(if (selectedIds.size == filtered.size) "Clear all" else "Select all") }
                    TextButton(onClick = { selectionMode = false; onClearSelection() }) { Text("Done") }
                } else TextButton(onClick = { selectionMode = true }) { Text("Select") }
            }
        }
        item { OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(), shape = FieldShape, placeholder = { Text("Search your indexed workspace") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true) }
        item { Text(embeddingStatus, color = Color(0xFF6D7D79), fontSize = 12.sp) }
        item { Text(if (query.isBlank()) "Semantic library" else "Semantic matches", color = Color(0xFF6D7D79)) }
        items(filtered, key = { it.id }) { source -> Card(shape = Squircle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (source.id in selectedIds) Soft else Color.White)) { Row(Modifier.padding(CardPadding), verticalAlignment = Alignment.Top) { if (selectionMode) Checkbox(checked = source.id in selectedIds, onCheckedChange = { onToggleSelection(source.id) }); Column(Modifier.weight(1f).clickable { if (selectionMode) onToggleSelection(source.id) }, verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(source.title, color = Ink, fontWeight = FontWeight.SemiBold); Text(source.detail, color = Color(0xFF5F6D69), modifier = Modifier.padding(top = 5.dp)); Text(source.type, color = Teal, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }; if (!selectionMode) IconButton(onClick = { onDelete(source) }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFB04B36)) } } } }
        if (selectionMode && selectedIds.isNotEmpty()) item { Button(onClick = onDeleteSelected, modifier = Modifier.fillMaxWidth(), shape = Squircle, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFB04B36))) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete ${selectedIds.size} selected") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSheet(source: DeskSource, actionPoints: List<ActionPointEntity>, allSources: List<SourceEntity>, semanticIndexer: SourceSemanticIndexer, embeddingReady: Boolean, ranker: EmbeddingGemmaActionRanker, instructionModelPath: String?, onReasoning: (ActionReasoningOutput) -> Unit, onStatus: (String, String) -> Unit, onDismiss: () -> Unit, onApprove: (String) -> Unit) {
    val candidates = source.detail.lineSequence().filter { it.startsWith("Action candidate:") }.map { it.removePrefix("Action candidate:").trim() }.filter { it.isNotBlank() }.toList()
    val dates = source.detail.lineSequence().firstOrNull { it.startsWith("Dates/times to verify:") }?.removePrefix("Dates/times to verify:")?.trim().orEmpty()
    val times = source.detail.lineSequence().firstOrNull { it.startsWith("Clock times to verify:") }?.removePrefix("Clock times to verify:")?.trim().orEmpty()
    val locations = source.detail.lineSequence().firstOrNull { it.startsWith("Locations to verify:") }?.removePrefix("Locations to verify:")?.trim().orEmpty()
    val people = source.detail.lineSequence().firstOrNull { it.startsWith("People/officials to verify:") }?.removePrefix("People/officials to verify:")?.trim().orEmpty()
    val persistedActions = actionPoints.map { it.action }.filter { it.isNotBlank() }
    val extractedFallback = remember(source.id) { ActionPointExtractor().extract(source.detail).map { it.action }.filter { it.isNotBlank() } }
    val actions = when {
        persistedActions.isNotEmpty() -> persistedActions
        candidates.isNotEmpty() -> candidates
        extractedFallback.isNotEmpty() -> extractedFallback
        else -> listOf(source.detail.lines().firstOrNull { it.isNotBlank() } ?: source.title)
    }
    var rankedActions by remember(source.id) { mutableStateOf(actions) }
    var reasoning by remember(source.id) { mutableStateOf<ActionReasoningOutput?>(null) }
    var reasoningStatus by remember(source.id) { mutableStateOf("Deterministic review cues active") }
    var relatedEvidence by remember(source.id) { mutableStateOf<List<SourceSemanticHit>>(emptyList()) }
    var editingAction by remember(source.id) { mutableStateOf<String?>(null) }
    var editedAction by remember(source.id) { mutableStateOf("") }
    LaunchedEffect(source.id, actions) {
        if (embeddingReady && actions.size > 1) rankedActions = runCatching { ranker.rank(actions).map { it.first } }.getOrDefault(actions)
        if (embeddingReady && actions.isNotEmpty()) {
            relatedEvidence = runCatching { semanticIndexer.search(actions.first(), allSources).filter { it.source.id != source.id.toLong() }.take(3) }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(source.id, instructionModelPath, relatedEvidence) {
        if (instructionModelPath == null) return@LaunchedEffect
        reasoningStatus = "Reviewing cited evidence on device…"
        val evidenceBundle = buildString {
            append("PRIMARY SOURCE [").append(source.type).append("]\n")
            append(source.detail).append("\n")
            append("EXTRACTED ACTION CANDIDATES\n")
            actions.forEach { append("- ").append(it).append("\n") }
            if (relatedEvidence.isNotEmpty()) {
                append("RELATED SOURCES RETRIEVED BY EMBEDDINGGEMMA\n")
                relatedEvidence.forEach { hit ->
                    append("[").append(hit.source.sourceType).append("] ").append(hit.source.title).append("\n")
                    append(hit.source.body.take(2200)).append("\n")
                }
            }
        }
        runCatching { OnDeviceActionReasoner(instructionModelPath).reason(evidenceBundle.take(24000)) }
            .onSuccess { output -> reasoning = output; onReasoning(output); reasoningStatus = "Local Gemma reasoning validated" }
            .onFailure { reasoningStatus = "Local reasoning unavailable; manual approval required" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Review insight", color = Ink, fontWeight = FontWeight.Bold, fontSize = 23.sp, modifier = Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close") } }
            Text(source.type, color = Teal, fontSize = 13.sp); Text(source.detail.lines().take(8).joinToString("\n"), color = Ink, fontSize = 16.sp)
            HorizontalDivider(); Text("Action points for your review", color = Color(0xFF6D7D79), fontSize = 13.sp)
            if (embeddingReady) Text("Indexed by EmbeddingGemma on this device", color = Teal, fontSize = 12.sp)
            Text(reasoningStatus, color = if (reasoning != null) Teal else Color(0xFF6D7D79), fontSize = 12.sp)
            reasoning?.let { output ->
                Card(shape = FieldShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFF0ECFA)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Gemma evidence-backed suggestion", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(output.action, color = Ink)
                        Text("Confidence ${(output.confidence * 100).toInt()}% · ${output.citations.size} citation(s)", color = Teal, fontSize = 12.sp)
                        output.citations.forEach { citation -> Text("“$citation”", color = Color(0xFF5F6D69), fontSize = 12.sp) }
                    }
                }
            }
            if (relatedEvidence.isNotEmpty()) {
                Text("Related evidence found by EmbeddingGemma", color = Color(0xFF6D7D79), fontSize = 13.sp)
                relatedEvidence.forEach { hit ->
                    Card(shape = FieldShape, colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9F8)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(hit.source.title, color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(hit.source.sourceType, color = Teal, fontSize = 12.sp)
                            Text("${hit.source.body.take(180)}…", color = Color(0xFF5F6D69), fontSize = 12.sp)
                            Text("Semantic match ${(hit.confidence * 100).toInt()}%", color = Teal, fontSize = 11.sp)
                        }
                    }
                }
            }
            rankedActions.forEach { action ->
                val evidence = action.trim()
                val persisted = actionPoints.firstOrNull { it.action == action }
                val actionDate = Regex("(?iu)\\b(?:today|tomorrow|this week|next week|\\d{1,2}(?:st|nd|rd|th)?[ -](?:January|February|March|April|May|June|July|August|September|October|November|December)(?:[ -]\\d{4})?)\\b").find(evidence)?.value
                val actionTime = Regex("(?iu)\\b(?:at\\s*)?(?:[01]?\\d|2[0-3])(?::[0-5]\\d)?\\s*(?:AM|PM)\\b").find(evidence)?.value
                Card(shape = FieldShape, colors = CardDefaults.cardColors(containerColor = Soft), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Action candidate", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (editingAction == action) OutlinedTextField(editedAction, { editedAction = it }, label = { Text("Corrected action") }, modifier = Modifier.fillMaxWidth(), shape = FieldShape, minLines = 2)
                    else Text(action, color = Ink, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { AssistChip(onClick = {}, label = { Text((persisted?.category ?: "official_work").replace('_', ' ')) }); if (actionDate != null || persisted?.dateText != null) AssistChip(onClick = {}, label = { Text(persisted?.dateText ?: actionDate.orEmpty()) }); if (actionTime != null || persisted?.timeText != null) AssistChip(onClick = {}, label = { Text(persisted?.timeText ?: actionTime.orEmpty()) }); AssistChip(onClick = {}, label = { Text("${((persisted?.confidence ?: .76f) * 100).toInt()}% confidence") }) }
                    Text("Supporting evidence: \"${persisted?.evidence?.take(300) ?: evidence.take(240)}\"", color = Color(0xFF5F6D69), fontSize = 12.sp)
                    persisted?.ambiguity?.let { Text("Needs clarification: $it", color = Color(0xFFB47725), fontSize = 12.sp) }
                    Button(onClick = { onApprove(if (editingAction == action && editedAction.isNotBlank()) editedAction.trim() else action) }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp), shape = Squircle) { Icon(Icons.Outlined.TaskAlt, null); Spacer(Modifier.width(8.dp)); Text(if (editingAction == action) "Approve corrected action" else "Approve this action") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editingAction = action; editedAction = action }) { Text("Edit") }
                        TextButton(onClick = { onStatus(action, "needs_clarification") }) { Text("Clarify") }
                        TextButton(onClick = { onStatus(action, "rejected") }) { Text("Not an action") }
                    }
                } }
            }
            if (dates.isNotBlank()) Text("Dates to verify: $dates", color = Color(0xFF6D7D79), fontSize = 13.sp)
            if (times.isNotBlank()) Text("Times to verify: $times", color = Color(0xFF6D7D79), fontSize = 13.sp)
            if (locations.isNotBlank()) Text("Locations to verify: $locations", color = Color(0xFF6D7D79), fontSize = 13.sp)
            if (people.isNotBlank()) Text("People/officials to verify: $people", color = Color(0xFF6D7D79), fontSize = 13.sp)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Keep in inbox") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CaptureDialog(onDismiss: () -> Unit, onCamera: () -> Unit, onImport: () -> Unit, onSave: (String, String, String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Manual note") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Capture to Psychiatry HOD Desk", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste a message, meeting note or action cue, or import a user-selected file.", color = Color(0xFF6D7D79), fontSize = 13.sp)
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = FieldShape)
                OutlinedTextField(detail, { detail = it }, label = { Text("What needs attention?") }, modifier = Modifier.fillMaxWidth(), minLines = 3, shape = FieldShape)
                OutlinedButton(onClick = {
                    clipboard.getText()?.text?.let { pasted ->
                        if (pasted.isNotBlank()) {
                            detail = pasted
                            if (title.isBlank()) title = "WhatsApp message"
                            type = "WhatsApp"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = Squircle) {
                    Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(8.dp)); Text("Paste copied WhatsApp text")
                }
                Text("Source type", color = Color(0xFF6D7D79), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Manual note", "Screenshot", "WhatsApp").forEach { label ->
                        AssistChip(onClick = { type = label }, shape = FieldShape, label = { Text(label) }, leadingIcon = if (type == label) ({ Icon(Icons.Outlined.CheckCircle, null) }) else null)
                    }
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = Squircle) {
                    Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(8.dp)); Text("Import from device")
                }
                OutlinedButton(onClick = onCamera, modifier = Modifier.fillMaxWidth(), shape = Squircle) {
                    Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(8.dp)); Text("Take document photo")
                }
            }
        },
        confirmButton = { Button(onClick = { if (title.isNotBlank() && detail.isNotBlank()) onSave(title, detail, type) }, shape = Squircle) { Text("Save to Inbox") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Teal) } }
    )
}

@Composable
private fun CalendarConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add protected focus block?") }, text = { Text("HOD Desk will request calendar access, check the selected calendar and write a 60-minute block only after you confirm. Existing events remain unchanged.") }, confirmButton = { Button(onClick = onConfirm, shape = Squircle) { Text("Confirm and add") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}


