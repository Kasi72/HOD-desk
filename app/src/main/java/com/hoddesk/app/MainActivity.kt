package com.hoddesk.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hoddesk.app.calendar.CalendarProviderScheduler
import com.hoddesk.app.calendar.CalendarWriteResult
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.HodDeskDatabase
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.imports.ActionPointExtractor
import com.hoddesk.app.imports.AudioTranscriptImportAdapter
import com.hoddesk.app.imports.ImportedSource
import com.hoddesk.app.imports.OfficialDocumentImportAdapter
import com.hoddesk.app.imports.OfflineAudioTranscriptionRuntime
import com.hoddesk.app.imports.ScreenshotImportAdapter
import com.hoddesk.app.imports.WhatsAppExportAdapter
import com.hoddesk.app.privacy.ConsentScope
import com.hoddesk.app.privacy.PrivacyStore
import com.hoddesk.app.reasoning.InstructionModelStore
import com.hoddesk.app.repository.HodDeskRepository
import com.hoddesk.app.repository.ScheduleProposal
import com.hoddesk.app.scheduling.SmartScheduling
import com.hoddesk.app.semantic.EmbeddingGemmaActionRanker
import com.hoddesk.app.semantic.EmbeddingGemmaLiteRtRuntime
import com.hoddesk.app.semantic.EmbeddingPromptRegistry
import com.hoddesk.app.semantic.HybridMatch
import com.hoddesk.app.semantic.HybridRetriever
import com.hoddesk.app.semantic.IndexHealth
import com.hoddesk.app.semantic.IndexHealthChecker
import com.hoddesk.app.semantic.SourceSemanticIndexer
import com.hoddesk.app.semantic.MatterGrouper
import com.hoddesk.app.semantic.MatterTimelineBuilder
import com.hoddesk.app.settings.DeskSettingsStore
import com.hoddesk.app.ui.components.ActionSheetContent
import com.hoddesk.app.ui.components.EvidenceText
import com.hoddesk.app.ui.components.FloatingTabBar
import com.hoddesk.app.ui.components.GroupedPage
import com.hoddesk.app.ui.components.HodButton
import com.hoddesk.app.ui.components.HodButtonStyle
import com.hoddesk.app.ui.components.SheetAction
import com.hoddesk.app.ui.components.TabItem
import com.hoddesk.app.ui.components.pressable
import com.hoddesk.app.ui.components.SegmentedControl
import com.hoddesk.app.ui.screens.ActivityPage
import com.hoddesk.app.ui.screens.AiPage
import com.hoddesk.app.ui.screens.AiStatus
import com.hoddesk.app.ui.screens.CalendarScreen
import com.hoddesk.app.ui.screens.CaptureKind
import com.hoddesk.app.ui.screens.CaptureSheet
import com.hoddesk.app.ui.screens.DepartmentPage
import com.hoddesk.app.ui.screens.HowToPage
import com.hoddesk.app.ui.screens.LibraryScreen
import com.hoddesk.app.ui.screens.MatterPage
import com.hoddesk.app.ui.screens.NoteDialog
import com.hoddesk.app.ui.screens.PrivacyPage
import com.hoddesk.app.ui.screens.ProcessingItem
import com.hoddesk.app.ui.screens.ProposedBlock
import com.hoddesk.app.ui.screens.ReviewScreen
import com.hoddesk.app.ui.screens.SettingsHome
import com.hoddesk.app.ui.screens.SettingsSummary
import com.hoddesk.app.ui.screens.SheetDialog
import com.hoddesk.app.ui.screens.TaskDetailScreen
import com.hoddesk.app.ui.screens.TasksScreen
import com.hoddesk.app.ui.screens.TimeDialog
import com.hoddesk.app.ui.screens.TodayScreen
import com.hoddesk.app.ui.screens.VaultPage
import com.hoddesk.app.ui.screens.cleanEvidence
import com.hoddesk.app.ui.screens.dueLabel
import com.hoddesk.app.ui.screens.isClinical
import com.hoddesk.app.ui.screens.sourceLook
import com.hoddesk.app.ui.screens.timeLabel
import com.hoddesk.app.ui.screens.workdayLine
import com.hoddesk.app.secretary.SecretaryStateBuilder
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.HodTheme
import com.hoddesk.app.ui.theme.Squircles
import com.hoddesk.app.viewmodel.HodDeskViewModel
import com.hoddesk.app.viewmodel.HodDeskViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val sharedUri = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null
        setContent { HodTheme { HODDeskApp(sharedUri) } }
    }
}

/** A delete waiting out its Undo window. Nothing is removed until the window closes. */
private data class PendingDelete(val kind: String, val ids: List<Long>, val label: String)

private const val TAB_TODAY = 0
private const val TAB_REVIEW = 1
private const val TAB_TASKS = 2
private const val TAB_CALENDAR = 3
private const val TAB_LIBRARY = 4

@Composable
fun HODDeskApp(initialSharedUri: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privacyStore = remember { PrivacyStore(context) }
    val settings = remember { DeskSettingsStore(context) }
    val database = remember { HodDeskDatabase.get(context) }
    val dao = remember { database.dao() }
    val repository = remember { HodDeskRepository(dao) }
    val viewModel: HodDeskViewModel = viewModel(factory = HodDeskViewModelFactory(repository))
    val embeddingRuntime = remember { EmbeddingGemmaLiteRtRuntime(context) }
    val instructionModelStore = remember { InstructionModelStore(context, BuildConfig.INSTRUCTION_MODEL_URL, BuildConfig.INSTRUCTION_MODEL_SHA256.ifBlank { null }) }
    var installedInstructionModel by remember { mutableStateOf(instructionModelStore.installedPath()) }
    val semanticIndexer = remember { SourceSemanticIndexer(dao, embeddingRuntime) }
    val audioRuntime = remember { OfflineAudioTranscriptionRuntime(context) }
    DisposableEffect(Unit) { onDispose { embeddingRuntime.close() } }

    val allSources by viewModel.sources.collectAsState()
    val allTasks by viewModel.tasks.collectAsState()
    val auditLog by viewModel.auditLog.collectAsState()
    val learningCorrectionCount by remember { dao.observeLearningCorrectionCount() }.collectAsState(0)
    val commitments by remember { dao.observeCommitments() }.collectAsState(emptyList())
    LaunchedEffect(Unit) {
        // Enforce the configured retention policy after every process start.
        // Sources backing approved tasks are deliberately retained by the DAO.
        repository.purgeExpiredSources(privacyStore.retentionCutoff())
        // Older builds could turn the fallback diagnostic into a real task.
        // Remove only those synthetic, still-open tasks during migration.
        repository.removePendingAudioPlaceholders()
        // Populate the in-app itinerary for tasks created by earlier builds.
        repository.backfillTaskEvents()
    }

    // --- Undo-able deletes: hidden immediately, committed after the Undo window. ---
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    val hiddenSources = pendingDelete?.takeIf { it.kind == "source" }?.ids.orEmpty().toSet()
    val hiddenTasks = pendingDelete?.takeIf { it.kind == "task" }?.ids.orEmpty().toSet()
    val hiddenEvents = pendingDelete?.takeIf { it.kind == "event" }?.ids.orEmpty().toSet()
    val sources = allSources.filter { it.id !in hiddenSources }
    val tasks = allTasks.filter { it.id !in hiddenTasks }
    fun commitDelete(p: PendingDelete) = when (p.kind) {
        "source" -> p.ids.forEach { viewModel.deleteSource(it) }
        "task" -> p.ids.forEach { viewModel.deleteTask(it) }
        else -> p.ids.forEach { viewModel.deleteEvent(it) }
    }
    fun requestDelete(p: PendingDelete) { pendingDelete?.let(::commitDelete); pendingDelete = p }
    LaunchedEffect(pendingDelete) { val p = pendingDelete ?: return@LaunchedEffect; delay(6_000); commitDelete(p); pendingDelete = null }

    // --- Navigation ---
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }
    var reviewStartId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var tasksSegment by rememberSaveable { mutableIntStateOf(0) }
    val settingsStack = remember { mutableStateListOf<String>() }
    var openSource by remember { mutableStateOf<SourceEntity?>(null) }
    var matterSourceId by remember { mutableStateOf<Long?>(null) }
    var sourceSheetTab by remember { mutableIntStateOf(0) }
    var showCapture by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val processing = remember { mutableStateListOf<ProcessingItem>() }
    var settingsVersion by remember { mutableIntStateOf(0) }

    // --- Calendar state ---
    var selectedDayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val selectedDay = LocalDate.ofEpochDay(selectedDayEpoch)
    val zone = ZoneId.systemDefault()
    val dayStart = selectedDay.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = selectedDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val calendarEvents by remember(dayStart) { dao.observeEvents(dayStart, dayEnd) }.collectAsState(emptyList())
    val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val todayEvents by remember(todayStart) { dao.observeEvents(todayStart, todayStart + 86_400_000L) }.collectAsState(emptyList())
    var proposals by remember { mutableStateOf<List<ProposedBlock>>(emptyList()) }
    var openEvent by remember { mutableStateOf<EventEntity?>(null) }
    var protectTimePicker by remember { mutableStateOf(false) }
    val team = remember(settingsVersion) { settings.team }
    val todayBlocks = remember(settingsVersion) { settings.blocksOn(LocalDate.now().dayOfWeek) }
    val selectedDayBlocks = remember(settingsVersion, selectedDayEpoch) { settings.blocksOn(selectedDay.dayOfWeek) }

    // --- Semantic index (EmbeddingGemma); clinical tiers are never indexed. ---
    val searchable = allSources.filter { !it.isClinical() }
    var embeddingReady by remember { mutableStateOf(false) }
    var embeddingLine by remember { mutableStateOf("Preparing search on this phone") }
    var indexHealth by remember { mutableStateOf<IndexHealth?>(null) }
    var aiBusy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(allSources) {
        if (searchable.isEmpty()) { embeddingLine = "Search ready"; return@LaunchedEffect }
        embeddingLine = "Indexing ${searchable.size} item(s) on this phone…"
        runCatching { semanticIndexer.indexAll(searchable) }
            .onSuccess { embeddingReady = true; embeddingLine = "${searchable.size} items searchable on this phone"; Log.i("HOD_SEMANTIC", "index_ready_sources=${searchable.size}") }
            .onFailure { embeddingReady = false; embeddingLine = "Smart search paused · keyword search still works"; Log.e("HOD_SEMANTIC", "index_failed", it) }
        indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull()
    }

    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var libraryMatches by remember { mutableStateOf<List<HybridMatch>>(emptyList()) }
    LaunchedEffect(libraryQuery, allSources, embeddingReady) {
        if (libraryQuery.isBlank()) { libraryMatches = emptyList(); return@LaunchedEffect }
        delay(250)
        val hits = if (embeddingReady) runCatching { semanticIndexer.search(libraryQuery, searchable, limit = 20) }.onFailure { Log.e("HOD_SEMANTIC", "search_failed", it) }.getOrDefault(emptyList()) else emptyList()
        libraryMatches = HybridRetriever.rank(libraryQuery, searchable, hits, limit = 20)
    }

    // --- Import pipeline (unchanged behaviour: consent gate → redact → extract → ingest) ---
    fun scopeFor(type: String): ConsentScope? {
        val kind = type.lowercase()
        return when {
            kind.contains("call") || kind.contains("audio") || kind.contains("voice") -> ConsentScope.CALLS
            kind.contains("whatsapp") || kind.contains("message") -> ConsentScope.MESSAGES
            kind.contains("screenshot") || kind.contains("camera") -> ConsentScope.SCREENSHOTS
            kind.contains("document") || kind.contains("gmail") || kind.contains("pdf") -> ConsentScope.DOCUMENTS
            else -> null
        }
    }
    fun allowed(scope: ConsentScope?): Boolean = scope == null || (privacyStore.canImportSensitive() && privacyStore.scopeGranted(scope))
    fun saveImported(item: ImportedSource, uri: Uri?) {
        if (!allowed(scopeFor(item.type))) {
            status = "${item.type} imports are turned off. Turn them on in Settings › Privacy & Data."
            return
        }
        val redactedInput = privacyStore.redact(item.body)
        val pendingAudio = item.type.equals("Audio pending transcription", ignoreCase = true) || redactedInput.startsWith("Audio is queued", ignoreCase = true)
        val body = when {
            pendingAudio -> redactedInput
            redactedInput.contains("--- HOD Desk review cues") -> redactedInput
            else -> ActionPointExtractor().annotate(redactedInput)
        }
        val needsReview = pendingAudio || body.contains("pending OCR", ignoreCase = true) || body.contains("no readable text", ignoreCase = true) || body.contains("No definite action", ignoreCase = true)
        val source = SourceEntity(title = item.title, body = body, sourceType = item.type, sourceUri = uri?.toString(), processingStatus = if (needsReview) "needs_review" else "ready")
        // A failure message is evidence of a processing error, never an action.
        val actions = if (pendingAudio) emptyList() else ActionPointExtractor().extract(redactedInput)
        viewModel.ingestWithActions(source, actions)
    }
    fun saveImportedBatch(items: List<ImportedSource>, uri: Uri) = items.forEach { saveImported(it, uri) }
    fun transcribe(uri: Uri, title: String, type: String) {
        val item = ProcessingItem(title, "Transcribing Tamil and English on this phone…", null)
        processing += item
        scope.launch {
            runCatching { audioRuntime.transcribeWav(uri) }
                .onSuccess { text -> saveImported(ImportedSource(title, text.ifBlank { "No speech detected" }, type, uri), uri); status = "$title added for review" }
                .onFailure { error ->
                    Log.e("HOD_AUDIO", "transcription_failed", error)
                    saveImported(AudioTranscriptImportAdapter(context.contentResolver).importAudio(uri), uri)
                    status = "Recording saved; transcription unavailable: ${error.message ?: error.javaClass.simpleName}"
                }
            processing.remove(item)
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = cameraUri
        if (captured && uri != null) {
            status = "Reading the photo…"
            ScreenshotImportAdapter().recognize(context, uri)
                .addOnSuccessListener { item -> saveImported(item.copy(title = "Photo · ${item.title}", type = "Camera document"), uri); status = "Photo text added for review" }
                .addOnFailureListener { saveImported(ImportedSource("Camera document", "OCR could not read this image; manual review required.", "Camera document", uri), uri); status = "Photo saved for manual review" }
        }
        cameraUri = null
    }
    fun captureDocument() {
        val file = File(context.cacheDir, "camera").apply { mkdirs() }.let { File.createTempFile("hod-document-", ".jpg", it) }
        val outputUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = outputUri
        cameraLauncher.launch(outputUri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) captureDocument() else status = "Camera permission is needed to photograph a document." }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri).orEmpty()
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val extension = uri.toString().substringBefore('?').substringAfterLast('.').lowercase()
        val audioSelection = mime.startsWith("audio/") || extension in setOf("wav", "wave", "m4a", "mp4", "aac", "mp3", "flac", "ogg", "opus", "amr") || runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                val count = input.read(header)
                count >= 4 && (
                    String(header, 0, 4, Charsets.US_ASCII) == "RIFF" ||
                    String(header, 0, 4, Charsets.US_ASCII) == "fLaC" ||
                    String(header, 0, 3, Charsets.US_ASCII) == "ID3" ||
                    (count >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp")
                )
            } ?: false
        }.getOrDefault(false)
        Log.i("HOD_AUDIO", "audio_selection_mime=$mime,extension=$extension,detected=$audioSelection")
        when {
            audioSelection -> transcribe(uri, "Call recording · ${timeLabel(System.currentTimeMillis())}", "Audio transcript")
            mime.startsWith("image/") -> {
                status = "Reading screenshot…"
                ScreenshotImportAdapter().recognize(context, uri)
                    .addOnSuccessListener { item -> saveImported(item, uri); status = "Screenshot added for review" }
                    .addOnFailureListener { status = "Couldn't read text; screenshot saved for manual review"; saveImported(ScreenshotImportAdapter().create(uri), uri) }
            }
            mime.contains("pdf") || mime.contains("word") || mime.contains("officedocument") || extension in setOf("pdf", "doc", "docx") ->
                runCatching { OfficialDocumentImportAdapter(context).import(uri) }
                    .onSuccess { item -> saveImported(item, uri); status = "Document added for review" }
                    .onFailure { status = "Document could not be read: ${it.message ?: it.javaClass.simpleName}" }
            mime == "text/vtt" || mime == "text/plain" -> {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val whatsappItems = WhatsAppExportAdapter().parse(text)
                if (whatsappItems.isNotEmpty()) { saveImportedBatch(whatsappItems, uri); status = "${whatsappItems.size} WhatsApp messages added for review" }
                else { saveImported(AudioTranscriptImportAdapter(context.contentResolver).importTranscript(uri), uri); status = "Text added for review" }
            }
            mime == "application/zip" || mime == "application/x-zip-compressed" || mime == "application/octet-stream" -> {
                val items = context.contentResolver.openInputStream(uri)?.use { WhatsAppExportAdapter().parseZip(it) }.orEmpty()
                saveImportedBatch(items, uri)
                status = if (items.isEmpty()) "No WhatsApp chat text found in that ZIP" else "${items.size} WhatsApp messages added for review"
            }
            else -> context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val items = WhatsAppExportAdapter().parse(reader.readText())
                saveImportedBatch(items, uri)
                status = if (items.isEmpty()) "No WhatsApp messages found" else "${items.size} WhatsApp messages added for review"
            }
        }
    }
    LaunchedEffect(initialSharedUri) {
        initialSharedUri?.let { uri ->
            runCatching { OfficialDocumentImportAdapter(context).import(uri, "Gmail attachment") }
                .onSuccess { item -> saveImported(item, uri); status = "Shared attachment added for review" }
                .onFailure { status = "Shared attachment could not be read: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    // --- Voice note ---
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(recorder) { recordSeconds = 0; while (recorder != null) { delay(1_000); recordSeconds++ } }
    fun startRecording() {
        val file = File(context.cacheDir, "voice").apply { mkdirs() }.let { File(it, "voice-${System.currentTimeMillis()}.m4a") }
        val r = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
        }
        runCatching { r.prepare(); r.start() }
            .onSuccess { recorder = r; recordingFile = file }
            .onFailure { r.release(); status = "Couldn't start recording: ${it.message ?: it.javaClass.simpleName}" }
    }
    fun stopRecording() {
        val r = recorder ?: return
        val file = recordingFile
        runCatching { r.stop() }
        r.release(); recorder = null
        if (file != null && file.length() > 0) {
            showCapture = false
            transcribe(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), "Voice note · ${timeLabel(System.currentTimeMillis())}", "Voice note")
        }
    }
    DisposableEffect(Unit) { onDispose { recorder?.let { runCatching { it.stop() }; it.release() } } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startRecording() else status = "Microphone permission is needed for voice notes." }

    // --- Calendar: permission, target calendar, writes ---
    var calendarChoices by remember { mutableStateOf<List<Pair<Long, String>>?>(null) }
    fun hasCalendarPermission() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun loadCalendars() {
        val list = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) list += c.getLong(0) to "${c.getString(1)} · ${c.getString(2)}"
        }
        calendarChoices = list
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) loadCalendars() else status = "Calendar permission is needed to add events."
    }
    val scheduler = remember { CalendarProviderScheduler.fromResolver(context.contentResolver) { p -> context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED } }
    LaunchedEffect(scheduler) {
        repository.reconcilePendingCalendarEvents { event -> scheduler.findMatchingEventId(event.title, event.startsAt, event.endsAt) }
    }
    fun clashesFor(start: Long, end: Long, events: List<EventEntity>): List<String> {
        val day = java.time.Instant.ofEpochMilli(start).atZone(zone)
        val s = day.toLocalTime().let { it.hour * 60 + it.minute }
        val e = s + ((end - start) / 60_000L).toInt()
        val fixed = settings.blocksOn(day.dayOfWeek).filter { s < it.endMinute && e > it.startMinute }.map { it.label }
        val local = events.filter { start < it.endsAt && end > it.startsAt }.map { it.title }
        val provider = runCatching { scheduler.findConflicts(start, end) }.getOrDefault(emptyList())
        return (fixed + local + provider).distinct()
    }
    fun planDay() {
        val workStart = dayStart + settings.workdayStartMinute * 60_000L
        val workEnd = dayStart + settings.workdayEndMinute * 60_000L
        val fixedAsEvents = settings.blocksOn(selectedDay.dayOfWeek).map { b -> EventEntity(title = b.label, startsAt = dayStart + b.startMinute * 60_000L, endsAt = dayStart + b.endMinute * 60_000L) }
        val earliest = maxOf(workStart, if (selectedDay == LocalDate.now()) System.currentTimeMillis() else workStart)
        val planned = SmartScheduling.propose(allTasks.filter { it.assignee.isNullOrBlank() }, calendarEvents + fixedAsEvents, earliest, workEnd)
        val freeMinutes = ((workEnd - earliest) / 60_000L) - fixedAsEvents.sumOf { (it.endsAt - it.startsAt) / 60_000L }
        val budget = (freeMinutes * (100 - settings.slackPercent) / 100).coerceAtLeast(0)
        var used = 0L
        proposals = planned.takeWhile { b -> used += (b.end - b.start) / 60_000L; used <= budget }.map { b ->
            ProposedBlock(b.task.id, b.task.title, b.start, b.end, clashesFor(b.start, b.end, calendarEvents), b.rationale)
        }
        tab = TAB_CALENDAR
        if (proposals.isEmpty()) status = "Nothing to plan: no open tasks fit the free time today (fixed clinical time and ${settings.slackPercent}% free time are protected)."
    }
    fun writeProposals() {
        val calendarId = settings.calendarId
        val toWrite = proposals.filter { it.clashesWith.isEmpty() }
        if (calendarId == null || !hasCalendarPermission()) {
            status = "Choose which calendar to write to first."
            settingsStack.clear(); settingsStack += "home"; settingsStack += "calendar"
            return
        }
        scope.launch {
            var written = 0
            val denied = mutableListOf<String>()
            toWrite.forEach { p ->
                val proposal = ScheduleProposal(EventEntity(taskId = p.taskId, title = p.title, startsAt = p.start, endsAt = p.end), emptyList())
                // Persist first. If the process dies after the provider insert,
                // the pending row is available for reconciliation on next start.
                val localId = repository.beginCalendarWrite(proposal.event)
                when (val r = scheduler.writeConfirmed(proposal, calendarId, confirmed = true)) {
                    is CalendarWriteResult.Written -> {
                        repository.finishCalendarWrite(localId, r.calendarEventId)
                        written++
                    }
                    is CalendarWriteResult.Denied -> {
                        repository.cancelCalendarWrite(localId)
                        denied += "${p.title}: ${r.reason}"
                    }
                    is CalendarWriteResult.NeedsConfirmation -> {
                        repository.cancelCalendarWrite(localId)
                        denied += p.title
                    }
                }
            }
            proposals = emptyList()
            status = buildString {
                append("$written block(s) added to ${settings.calendarName ?: "your calendar"}.")
                if (denied.isNotEmpty()) append(" Skipped: ").append(denied.joinToString("; "))
            }
        }
    }

    // --- Vault unlock (device credential) and audit export ---
    val vaultUnlock = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) settingsStack += "vault"
    }
    fun openVault() {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val intent = if (km.isDeviceSecure) km.createConfirmDeviceCredentialIntent("Clinical Vault", "Confirm it's you to view clinical material") else null
        if (intent != null) vaultUnlock.launch(intent) else { status = "Set a screen lock on this phone to protect the Clinical Vault."; settingsStack += "vault" }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val entries = dao.allAudit()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    w.appendLine("Psychiatry HOD Desk — activity log export (${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())})")
                    entries.forEach { e -> w.appendLine("${java.time.Instant.ofEpochMilli(e.createdAt)}\t${e.entityType}#${e.entityId}\t${e.action}\t${e.detail.orEmpty()}") }
                }
            }.onSuccess { status = "Activity log exported (${entries.size} entries)." }.onFailure { status = "Export failed: ${it.message}" }
        }
    }

    // --- Derived status ---
    val reviewQueue = sources.filter { !it.reviewed && !it.isClinical() }.sortedWith(compareByDescending<SourceEntity> { it.priorityScore }.thenByDescending { it.capturedAt })
    val aiHealthy = embeddingReady && (indexHealth?.healthy != false)
    val secretaryState = remember(sources, tasks, todayEvents, commitments) {
        SecretaryStateBuilder.build(sources, tasks, todayEvents, commitments)
    }
    var reasoningProgress by remember { mutableStateOf<Int?>(null) }

    val c = Hod.colors
    Box(Modifier.fillMaxSize()) {
        GroupedPage {
            when (tab) {
                TAB_TODAY -> TodayScreen(
                    sources = sources, tasks = tasks, todayEvents = todayEvents.filter { it.id !in hiddenEvents }, todayBlocks = todayBlocks,
                    onDeviceLine = "On this phone · $embeddingLine", onDeviceHealthy = aiHealthy || searchable.isEmpty(),
                    status = null, onDismissStatus = {},
                    onOpenSettings = { settingsStack.clear(); settingsStack += "home" },
                    onSearch = { tab = TAB_LIBRARY },
                    onOpenReview = { s -> reviewStartId = s?.id; tab = TAB_REVIEW },
                    onOpenTask = { openTaskId = it.id },
                    onOpenCalendar = { selectedDayEpoch = LocalDate.now().toEpochDay(); tab = TAB_CALENDAR },
                    onOpenWaiting = { tasksSegment = 1; tab = TAB_TASKS },
                    secretaryState = secretaryState,
                )
                TAB_REVIEW -> ReviewScreen(
                    queue = reviewQueue, startSourceId = reviewStartId, team = team,
                    observeActionPoints = { id -> dao.observeActionPoints(id) },
                    searchRelated = { text, id -> if (embeddingReady) semanticIndexer.search(text, searchable).filter { it.source.id != id } else emptyList() },
                    rankActions = { list -> if (embeddingReady) EmbeddingGemmaActionRanker(embeddingRuntime).rank(list).map { it.first } else list },
                    instructionModelPath = installedInstructionModel,
                    onReasoning = { id, output -> viewModel.saveReasoning(id, output) },
                    onUpdateActionPoint = { point -> viewModel.updateActionPoint(point) },
                    onApproveEdited = { source, point, action, assignee -> viewModel.approveEditedAction(point, source.id, source.sourceType, action, assignee) },
                    onApprove = { source, action, assignee -> viewModel.approveAction(source.id, action, source.sourceType, assignee = assignee) },
                    onActionStatus = { id, action, s -> viewModel.setActionPointStatus(id, action, s) },
                    onDismissSource = { id, reason -> viewModel.dismissSource(id, reason) },
                    onBack = { tab = TAB_TODAY },
                )
                TAB_TASKS -> TasksScreen(tasks, tasksSegment, onToggleDone = { t -> viewModel.setDone(t.id, t.status != "completed") }, onOpen = { openTaskId = it.id })
                TAB_CALENDAR -> CalendarScreen(
                    selectedDay = selectedDay, events = calendarEvents.filter { it.id !in hiddenEvents }, fixedBlocks = selectedDayBlocks,
                    proposals = proposals, onSelectDay = { selectedDayEpoch = it.toEpochDay(); proposals = emptyList() },
                    onPlanDay = ::planDay, onProtectTime = { protectTimePicker = true }, onOpenEvent = { openEvent = it },
                    onConfirmProposals = ::writeProposals, onDiscardProposals = { proposals = emptyList() },
                )
                else -> LibraryScreen(
                    sources = sources, query = libraryQuery, matches = libraryMatches.filter { it.source.id !in hiddenSources },
                    statusLine = if (embeddingReady) "Tamil + English, on this phone" else "keyword search",
                    onQueryChange = { libraryQuery = it }, onOpen = { sourceSheetTab = 0; openSource = it },
                    onDeleteSelected = { ids -> requestDelete(PendingDelete("source", ids, "${ids.size} item(s)")) },
                )
            }
        }

        if (tab != TAB_REVIEW) FloatingTabBar(
            items = listOf(
                TabItem("Today", Icons.Rounded.WbSunny),
                TabItem("Review", Icons.Rounded.Inbox, badge = reviewQueue.size),
                TabItem("Tasks", Icons.Rounded.CheckCircleOutline, Icons.Rounded.CheckCircle),
                TabItem("Calendar", Icons.Rounded.CalendarMonth),
                TabItem("Library", Icons.Rounded.LibraryBooks),
            ),
            selected = tab, onSelect = { tab = it; if (it == TAB_REVIEW) reviewStartId = null },
            onCapture = { showCapture = true }, captureIcon = Icons.Rounded.Add,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // --- Pushed screens ---
        openTaskId?.let { id ->
            val task = allTasks.firstOrNull { it.id == id }
            if (task == null || id in hiddenTasks) { LaunchedEffect(id) { openTaskId = null } } else {
                val audit by remember(id) { dao.observeAudit("task", id) }.collectAsState(emptyList())
                TaskDetailScreen(
                    task = task, source = allSources.firstOrNull { it.id == task.sourceId }, audit = audit, team = team,
                    onBack = { openTaskId = null },
                    onSetDone = { viewModel.setDone(task.id, it) },
                    onReschedule = { viewModel.setTaskDue(task.id, it) },
                    onAssign = { viewModel.setAssignee(task.id, it) },
                    onBlocker = { viewModel.setBlocker(task.id, it) },
                    onClarify = { viewModel.clarify(task.id, it) },
                    onDelete = { openTaskId = null; requestDelete(PendingDelete("task", listOf(task.id), "“${task.title}”")) },
                    onOpenSource = { sourceSheetTab = 0; openSource = it },
                )
            }
        }
        if (settingsStack.isNotEmpty()) {
            fun pop() { settingsStack.removeAt(settingsStack.lastIndex) }
            fun push(route: String) {
                when (route) {
                    "vault" -> openVault()
                    "calendar" -> if (hasCalendarPermission()) loadCalendars() else calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                    "workday", "slack" -> settingsStack += route
                    else -> settingsStack += route
                }
            }
            when (val route = settingsStack.last()) {
                "privacy" -> PrivacyPage(privacyStore, onBack = ::pop, onOpenActivity = { settingsStack += "activity" }, onDeleteAll = {
                    viewModel.deleteAllWorkData(); status = "All work data deleted from this phone."; settingsStack.clear()
                })
                "ai" -> {
                    LaunchedEffect(Unit) { indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull() }
                    val h = indexHealth
                    AiPage(
                        status = AiStatus(
                            healthy = aiHealthy,
                            headline = if (aiHealthy) "Everything is working" else "Smart search needs attention",
                            detail = if (aiHealthy) "Search, speech and reading run on this phone" else embeddingLine,
                            searchLine = h?.let { "${it.indexedSourceCount} of ${it.sourceCount} items indexed · Tamil + English" } ?: "Checking…",
                            speechReady = File(context.filesDir, "ggml-small.bin").length() >= 400_000_000L,
                            reasoningInstalled = installedInstructionModel != null,
                            reasoningProgress = reasoningProgress,
                            technical = listOfNotNull(
                                "Embedding model" to SourceSemanticIndexer.MODEL_VERSION,
                                "Prompt registry" to "v${EmbeddingPromptRegistry.VERSION}",
                                h?.let { "Indexed / eligible" to "${it.indexedSourceCount} / ${it.sourceCount}" },
                                h?.let { "Chunks" to it.chunkCount.toString() },
                                h?.let { "Stale / invalid vectors" to "${it.staleSourceIds.size} / ${it.invalidSourceIds.size}" },
                                "Reasoning model" to (installedInstructionModel?.substringAfterLast('/') ?: "Not installed"),
                                "App" to "${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""}",
                            ),
                            busy = aiBusy,
                            correctionCount = learningCorrectionCount,
                            clinicalCount = allSources.count { it.isClinical() },
                        ),
                        onBack = ::pop,
                        onSelfCheck = { scope.launch { aiBusy = "Checking…"; indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull(); aiBusy = indexHealth?.let { if (it.healthy) "Self-check passed." else "Self-check found ${it.staleSourceIds.size} stale and ${it.invalidSourceIds.size} invalid item(s). Rebuild the index." } ?: "Self-check could not run." } },
                        onRebuild = { scope.launch { aiBusy = "Rebuilding search index…"; runCatching { semanticIndexer.indexAll(searchable) }.onSuccess { embeddingReady = true; aiBusy = "Search index rebuilt." }.onFailure { aiBusy = "Rebuild failed: ${it.message}" }; indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull() } },
                        onInstallReasoning = {
                            scope.launch {
                                reasoningProgress = 0
                                runCatching { instructionModelStore.install { p -> reasoningProgress = p } }
                                    .onSuccess { installedInstructionModel = it; status = "Deeper understanding installed." }
                                    .onFailure { status = "Install failed: ${it.message ?: it.javaClass.simpleName}" }
                                reasoningProgress = null
                            }
                        },
                    )
                }
                "activity" -> ActivityPage(auditLog, onBack = ::pop, onExport = { exportLauncher.launch("hod-desk-activity-${LocalDate.now()}.txt") })
                "matter" -> {
                    val sourceId = matterSourceId
                    val relations by remember(sourceId) { sourceId?.let { dao.observeRelations(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
                    val matter = sourceId?.let { id -> MatterGrouper.group(allSources, relations).firstOrNull { it.sources.any { source -> source.id == id } } }
                    if (matter != null) MatterPage(MatterTimelineBuilder.build(matter, allTasks, calendarEvents), onBack = ::pop, onReviewChange = { tab = TAB_REVIEW; pop() })
                    else com.hoddesk.app.ui.components.EmptyState("Matter not available", "The related sources have not been indexed yet.")
                }
                "howto" -> HowToPage(onBack = ::pop)
                "vault" -> VaultPage(allSources.filter { it.isClinical() }, onBack = ::pop)
                "department" -> DepartmentPage(settings, onBack = ::pop, onChanged = { settingsVersion++ })
                else -> SettingsHome(
                    summary = run {
                        settingsVersion
                        val blocks = settings.fixedBlocks
                        SettingsSummary(
                            teamCount = settings.team.size, venueCount = settings.venues.size,
                            blockSummary = blocks.firstOrNull()?.let { "${it.label} ${it.daysLabel()}" + if (blocks.size > 1) " +${blocks.size - 1}" else "" } ?: "None",
                            calendarName = settings.calendarName,
                            workday = workdayLine(settings.workdayStartMinute, settings.workdayEndMinute),
                            slackPercent = settings.slackPercent,
                            clinicalCount = allSources.count { it.isClinical() },
                            aiHealthy = aiHealthy || searchable.isEmpty(), aiLine = if (aiHealthy || searchable.isEmpty()) "Working" else "Check",
                            versionLine = "Psychiatry HOD Desk ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · test build" else ""}",
                            setupDone = listOf(blocks != DeskSettingsStore.DEFAULT_BLOCKS, settings.team.isNotEmpty(), settings.venues.isNotEmpty(), settings.calendarId != null).count { it },
                        )
                    },
                    onBack = { settingsStack.clear() },
                    onOpen = ::push,
                )
            }
            when (settingsStack.last()) {
                "workday" -> {
                    var picking by remember { mutableStateOf("start") }
                    TimeDialog(if (picking == "start") "Workday starts" else "Workday ends", if (picking == "start") settings.workdayStartMinute else settings.workdayEndMinute, onDismiss = ::pop) { m ->
                        if (picking == "start") { settings.workdayStartMinute = m; picking = "end" } else { settings.workdayEndMinute = m; settingsVersion++; pop() }
                    }
                }
                "slack" -> SheetDialog(::pop) {
                    ActionSheetContent("Keep this much of the day free", "Plan My Day books at most the rest.", listOf(0, 20, 30, 40, 50).map { p ->
                        SheetAction("$p%", emphasized = p == settings.slackPercent) { settings.slackPercent = p; settingsVersion++; pop() }
                    }, onCancel = ::pop)
                }
            }
        }

        // --- Overlays ---
        openSource?.let { s ->
            SheetDialog({ openSource = null }) {
                val look = sourceLook(s.sourceType)
                // The source sheet owns the single vertical scroll container.
                // Keeping the evidence card non-scrollable avoids nested
                // scroll measurement crashes while ensuring the action row is
                // reachable on short displays.
                Column(Modifier.fillMaxWidth().clip(Squircles.sheetTop).background(c.groupedBackground).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.title, style = Hod.type.title3, color = c.label)
                    Text("${look.label} · ${dueLabel(s.capturedAt)}", style = Hod.type.footnote, color = c.secondaryLabel)
                    SegmentedControl(listOf("Actions", "Evidence", "Original"), sourceSheetTab, { sourceSheetTab = it }, Modifier.fillMaxWidth())
                    when (sourceSheetTab) {
                        0 -> {
                            if (!s.isClinical()) HodButton("Open matter file", { matterSourceId = s.id; openSource = null; settingsStack.clear(); settingsStack += "matter" }, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.fillMaxWidth())
                            if (!s.isClinical()) HodButton("Review and extract actions", { openSource = null; openTaskId = null; reviewStartId = s.id; tab = TAB_REVIEW }, height = 46.dp, modifier = Modifier.fillMaxWidth())
                            if (!s.isClinical()) HodButton("Edit and save in Review", { openSource = null; openTaskId = null; reviewStartId = s.id; tab = TAB_REVIEW }, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.fillMaxWidth())
                            HodButton("Send to Inbox", { viewModel.returnToInbox(s.id); openSource = null; tab = TAB_REVIEW }, style = HodButtonStyle.Tinted, height = 46.dp, modifier = Modifier.fillMaxWidth())
                            HodButton("Delete", { openSource = null; requestDelete(PendingDelete("source", listOf(s.id), "“${s.title}”")) }, style = HodButtonStyle.DestructiveTinted, height = 46.dp, modifier = Modifier.fillMaxWidth())
                        }
                        1 -> Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface).padding(16.dp)) {
                            EvidenceText(cleanEvidence(s.body).ifBlank { "No readable text." }, emptyList(), maxLines = 40)
                        }
                        else -> {
                            s.sourceUri?.let { uri -> HodButton("Open Original", { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { status = "No app can open this file." } }, style = HodButtonStyle.Gray, height = 46.dp, modifier = Modifier.fillMaxWidth()) }
                                ?: Text("The original file is no longer available on this phone.", style = Hod.type.subheadline, color = c.secondaryLabel)
                        }
                    }
                }
            }
        }
        openEvent?.let { e ->
            SheetDialog({ openEvent = null }) {
                ActionSheetContent(e.title, "${timeLabel(e.startsAt)} – ${timeLabel(e.endsAt)}${e.location?.let { " · $it" } ?: ""}\nRemoving here doesn't change your Android calendar.", listOf(
                    SheetAction("Remove from HOD Desk", destructive = true) { openEvent = null; requestDelete(PendingDelete("event", listOf(e.id), "“${e.title}”")) },
                ), onCancel = { openEvent = null })
            }
        }
        if (protectTimePicker) TimeDialog("Protect an hour from", 14 * 60, onDismiss = { protectTimePicker = false }) { m ->
            protectTimePicker = false
            val start = dayStart + m * 60_000L
            proposals = proposals + ProposedBlock(null, "Protected focus time", start, start + 3_600_000L, clashesFor(start, start + 3_600_000L, calendarEvents))
        }
        calendarChoices?.let { choices ->
            SheetDialog({ calendarChoices = null }) {
                ActionSheetContent("Write confirmed events to", if (choices.isEmpty()) "No writable calendar found on this phone." else null, choices.map { (id, name) ->
                    SheetAction(name, emphasized = id == settings.calendarId) { settings.calendarId = id; settings.calendarName = name.substringBefore(" · "); settingsVersion++; calendarChoices = null }
                }, onCancel = { calendarChoices = null })
            }
        }
        if (showCapture) CaptureSheet(
            allowed = ::allowed, recording = recorder != null, recordSeconds = recordSeconds, processing = processing,
            onDismiss = { if (recorder == null) showCapture = false },
            onToggleVoice = {
                if (recorder != null) stopRecording()
                else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onPick = { kind ->
                showCapture = false
                when (kind) {
                    CaptureKind.Camera -> if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) captureDocument() else cameraPermission.launch(Manifest.permission.CAMERA)
                    CaptureKind.Screenshot -> importLauncher.launch(arrayOf("image/*"))
                    CaptureKind.Document -> importLauncher.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"))
                    CaptureKind.WhatsApp -> importLauncher.launch(arrayOf("text/plain", "application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    CaptureKind.Call -> importLauncher.launch(arrayOf("audio/*"))
                    CaptureKind.Note -> showNote = true
                }
            },
            onBlocked = { kind -> showCapture = false; status = "${kind.label} imports are off."; settingsStack.clear(); settingsStack += "home"; settingsStack += "privacy" },
            onOpenPrivacy = { showCapture = false; settingsStack.clear(); settingsStack += "home"; settingsStack += "privacy" },
        )
        if (showNote) NoteDialog(onDismiss = { showNote = false }) { title, body, type ->
            showNote = false
            if (type == "WhatsApp") saveImported(ImportedSource(title, body, type), null)
            else viewModel.ingestWithActions(SourceEntity(title = title, body = ActionPointExtractor().annotate(privacyStore.redact(body)), sourceType = type), ActionPointExtractor().extract(privacyStore.redact(body)))
            status = "Note added for review"
        }

        // Snackbar area, drawn above every screen: Undo for deletes, otherwise the latest status message.
        val overTabBar = tab != TAB_REVIEW && openTaskId == null && settingsStack.isEmpty()
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = when { overTabBar -> 88.dp; tab == TAB_REVIEW && openTaskId == null && settingsStack.isEmpty() -> 150.dp; else -> 76.dp }), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            status?.let { s ->
                Row(Modifier.fillMaxWidth().clip(Squircles.card).background(c.label).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s, style = Hod.type.subheadline, color = c.surface, modifier = Modifier.weight(1f))
                    Text("OK", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.padding(start = 12.dp).pressable { status = null })
                }
            }
            pendingDelete?.let { p ->
                Row(Modifier.fillMaxWidth().clip(Squircles.card).background(c.label).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Deleted ${p.label}", style = Hod.type.subheadline, color = c.surface, modifier = Modifier.weight(1f))
                    Text("Undo", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.pressable { pendingDelete = null })
                }
            }
        }
        LaunchedEffect(status) { if (status != null) { delay(8_000); status = null } }
    }
    BackHandler(enabled = tab != TAB_TODAY && openTaskId == null && settingsStack.isEmpty() && tab != TAB_REVIEW) { tab = TAB_TODAY }
}
