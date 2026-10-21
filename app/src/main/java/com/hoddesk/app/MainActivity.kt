package com.hoddesk.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
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
import com.hoddesk.app.learning.SecretaryLearning
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
import com.hoddesk.app.semantic.IndexSelfCheck
import com.hoddesk.app.semantic.IndexSnapshotManager
import com.hoddesk.app.semantic.SourceSemanticIndexer
import com.hoddesk.app.observability.PerformanceMetrics
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
import com.hoddesk.app.workers.IndexMaintenanceScheduler
import com.hoddesk.app.ui.screens.AiPage
import com.hoddesk.app.ui.screens.AiStatus
import com.hoddesk.app.ui.screens.CalendarScreen
import com.hoddesk.app.ui.screens.CaptureKind
import com.hoddesk.app.ui.screens.CaptureSheet
import com.hoddesk.app.ui.screens.DepartmentPage
import com.hoddesk.app.ui.screens.HowToPage
import com.hoddesk.app.ui.screens.LibraryScreen
import com.hoddesk.app.ui.screens.MatterPage
import com.hoddesk.app.ui.screens.FollowUpPage
import com.hoddesk.app.ui.screens.SecretaryPage
import com.hoddesk.app.ui.screens.SecretaryControlCentre
import com.hoddesk.app.ui.screens.EvaluationLabPage
import com.hoddesk.app.followup.FollowUpEngine
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
    val privacyStore = remember { PrivacyStore(context) }
    val settings = remember { DeskSettingsStore(context) }
    val database = remember { HodDeskDatabase.get(context) }
    val dao = remember { database.dao() }
    val repository = remember { HodDeskRepository(dao) }
    val viewModel: HodDeskViewModel = viewModel(factory = HodDeskViewModelFactory(repository))
    val embeddingRuntime = remember { EmbeddingGemmaLiteRtRuntime(context) }
    val instructionModelStore = remember { InstructionModelStore(context, BuildConfig.INSTRUCTION_MODEL_URL, BuildConfig.INSTRUCTION_MODEL_SHA256.ifBlank { null }) }
    var installedInstructionModel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(instructionModelStore) {
        installedInstructionModel = withContext(Dispatchers.IO) { instructionModelStore.installedPath() }
    }
    val semanticIndexer = remember { SourceSemanticIndexer(dao, embeddingRuntime) }
    val audioRuntime = remember { OfflineAudioTranscriptionRuntime(context) }
    var verifiedTamilSpeechReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { embeddingRuntime.close() } }

    val allSources by viewModel.sources.collectAsState()
    val allTasks by viewModel.tasks.collectAsState()
    LaunchedEffect(allSources) {
        viewModel.refreshGeneratedCandidates(allSources).join()
    }
    val auditLog by viewModel.auditLog.collectAsState()
    val learningCorrectionCount by remember { dao.observeLearningCorrectionCount() }.collectAsState(0)
    val commitments by remember { dao.observeCommitments() }.collectAsState(emptyList())
    LaunchedEffect(Unit) {
        // Let the first Compose frame render before maintenance performs Room
        // migrations, retention work or WorkManager registration. This keeps
        // cold start responsive while preserving the same startup guarantees.
        delay(250)
        // Maintenance is best-effort. A revoked permission, a damaged legacy
        // row, or a scheduler refusal must never prevent the UI from opening.
        runCatching { IndexMaintenanceScheduler.schedule(context) }
            .onFailure { Log.e("HOD_DESK", "maintenance_schedule_failed", it) }
        runCatching { repository.purgeExpiredSources(privacyStore.retentionCutoff()) }
            .onFailure { Log.e("HOD_DB", "retention_purge_failed", it) }
        runCatching { repository.removePendingAudioPlaceholders() }
            .onFailure { Log.e("HOD_DB", "audio_placeholder_cleanup_failed", it) }
        runCatching { repository.backfillTaskEvents() }
            .onFailure { Log.e("HOD_DB", "event_backfill_failed", it) }
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
    LaunchedEffect(viewModel) { viewModel.operationErrors.collect { status = it } }
    // UI actions launch database/provider/model work from this scope.  A single
    // bad URI, revoked permission or provider failure must be reported in the
    // UI rather than escaping the composition and terminating the process.
    val scope = rememberCoroutineScope {
        kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
            Log.e("HOD_DESK", "uncaught_ui_coroutine_failure", error)
            status = "That operation failed safely: ${error.message ?: error.javaClass.simpleName}"
        }
    }
    var secretaryTranscript by rememberSaveable { mutableStateOf("") }
    var secretaryEvidence by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
    var secretaryProposalDiff by remember { mutableStateOf<String?>(null) }
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
    val secretaryWindowStart = remember { System.currentTimeMillis() - 7L * 86_400_000L }
    val secretaryEvents by remember(secretaryWindowStart) {
        dao.observeEvents(secretaryWindowStart, secretaryWindowStart + 372L * 86_400_000L)
    }.collectAsState(emptyList())
    var proposals by remember { mutableStateOf<List<ProposedBlock>>(emptyList()) }
    var planningWarning by remember { mutableStateOf<String?>(null) }
    var openEvent by remember { mutableStateOf<EventEntity?>(null) }
    var protectTimePicker by remember { mutableStateOf(false) }
    val team = remember(settingsVersion) { settings.team }
    val todayBlocks = remember(settingsVersion) { settings.blocksOn(LocalDate.now().dayOfWeek) }
    val selectedDayBlocks = remember(settingsVersion, selectedDayEpoch) { settings.blocksOn(selectedDay.dayOfWeek) }

    // --- Semantic index (EmbeddingGemma); clinical tiers are never indexed. ---
    // Do not construct the heavyweight multimodal runtime during app startup.
    // UniversalEmbedder maps the text, vision and audio encoders together; doing
    // that from the first composition caused Android's low-memory killer to take
    // down the entire activity while the user was simply switching tabs.
    val searchable = allSources.filter { !it.isClinical() }
    var embeddingReady by remember { mutableStateOf(false) }
    var embeddingLine by remember { mutableStateOf("Preparing search on this phone") }
    var indexHealth by remember { mutableStateOf<IndexHealth?>(null) }
    var aiBusy by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(secretaryTranscript, embeddingReady, allSources) {
        if (secretaryTranscript.isBlank()) { secretaryEvidence = emptyList(); return@LaunchedEffect }
        secretaryEvidence = if (embeddingReady) {
            runCatching { semanticIndexer.search(secretaryTranscript, searchable, limit = 5).map { it.source } }.getOrDefault(emptyList())
        } else {
            val terms = secretaryTranscript.lowercase().split(Regex("\\s+" )).filter { it.length >= 3 }
            searchable.filter { source -> terms.any { source.title.lowercase().contains(it) || source.body.lowercase().contains(it) } }.take(5)
        }
    }
    LaunchedEffect(secretaryTranscript, secretaryEvents, allTasks, settingsVersion) {
        val intent = com.hoddesk.app.secretary.VoiceSecretary.parse(secretaryTranscript)
        secretaryProposalDiff = if (!intent.requiresConfirmation) null else {
            val activeTasks = allTasks.filter { it.status !in setOf("completed", "deleted", "archived") }
            val eventMatch = com.hoddesk.app.secretary.VoiceCommandTargeting.resolve(
                intent.query, secretaryEvents, "event", { it.id }, { "${it.title} ${it.location.orEmpty()}" })
            val taskMatch = com.hoddesk.app.secretary.VoiceCommandTargeting.resolve(
                intent.query, activeTasks, "task", { it.id }, { "${it.title} ${it.project}" },
                if (intent.kind == com.hoddesk.app.secretary.SecretaryIntent.Kind.DELEGATE_TASK) settings.team.map { it.name } else emptyList())
            val matchedEvent = eventMatch.record
            val matchedTask = taskMatch.record
            val requestedTime = intent.parameters["time"]
            when (intent.kind) {
                com.hoddesk.app.secretary.SecretaryIntent.Kind.MOVE_TASKS,
                com.hoddesk.app.secretary.SecretaryIntent.Kind.UPDATE_TASK,
                com.hoddesk.app.secretary.SecretaryIntent.Kind.SCHEDULE_TASK -> {
                    when {
                        requestedTime == null -> "No exact time was detected; the request will remain unchanged until clarified."
                        matchedEvent != null -> "${matchedEvent.title}: ${timeLabel(matchedEvent.startsAt)} → $requestedTime"
                        matchedTask != null -> "${matchedTask.title}: ${matchedTask.dueAt?.let(::timeLabel) ?: "unscheduled"} → $requestedTime"
                        else -> eventMatch.error ?: taskMatch.error ?: "No matching meeting or task was found; nothing will be changed."
                    }
                }
                com.hoddesk.app.secretary.SecretaryIntent.Kind.DELEGATE_TASK -> {
                    val member = com.hoddesk.app.secretary.VoiceCommandTargeting.uniqueAssignee(intent.query, settings.team.map { it.name })
                    if (matchedTask != null && member != null) "${matchedTask.title}: ${matchedTask.assignee ?: "Me"} → $member" else "Select a matching task and configured team member before delegating."
                }
                com.hoddesk.app.secretary.SecretaryIntent.Kind.CREATE_TASK -> "Create task: ${intent.parameters["target"].orEmpty().removePrefix("to ").trim()}"
                else -> null
            }
        }
    }
    LaunchedEffect(allSources) {
        embeddingReady = false
        embeddingLine = if (searchable.isEmpty()) "Search ready" else "Search ready · semantic search starts when you search"
        indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull()
    }
    LaunchedEffect(Unit) {
        // Never load the multimodal runtime while the foreground UI is
        // starting. Verify the bundle/hash cheaply here; the eight-vector
        // semantic self-test runs in the charging-constrained worker.
        if (embeddingRuntime.isInstalled()) {
            val result = embeddingRuntime.verifyInstalledModel()
            PerformanceMetrics.record(dao, "startup_model_file_check", if (result.isSuccess) 1 else 0, result.exceptionOrNull()?.javaClass?.simpleName ?: "passed")
            if (result.isFailure) Log.w("HOD_SEMANTIC", "startup_model_file_check_failed", result.exceptionOrNull())
        }
    }

    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var libraryMatches by remember { mutableStateOf<List<HybridMatch>>(emptyList()) }
    LaunchedEffect(tab, libraryQuery, allSources, embeddingReady) {
        if (tab != TAB_LIBRARY || libraryQuery.isBlank()) { libraryMatches = emptyList(); return@LaunchedEffect }
        delay(250)
        if (!embeddingReady && searchable.isNotEmpty()) {
            embeddingLine = "Preparing semantic search…"
            runCatching { semanticIndexer.indexAll(searchable) }
                .onSuccess {
                    embeddingReady = true
                    embeddingLine = "${searchable.size} items searchable on this phone"
                    Log.i("HOD_SEMANTIC", "index_ready_sources=${searchable.size}")
                }
                .onFailure {
                    embeddingReady = false
                    embeddingLine = "Smart search paused · keyword search still works"
                    Log.e("HOD_SEMANTIC", "index_failed", it)
                }
            indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull()
        }
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
    suspend fun saveImported(item: ImportedSource, uri: Uri?) {
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
        val extractor = ActionPointExtractor()
        val actions = if (pendingAudio || extractor.isProcessingFailure(redactedInput) || needsReview && redactedInput.contains("OCR", ignoreCase = true)) emptyList() else extractor.extract(redactedInput)
        repository.ingestWithActions(source, actions)
    }
    suspend fun saveImportedBatch(items: List<ImportedSource>, uri: Uri) {
        items.forEach { saveImported(it, uri) }
    }
    fun transcribe(uri: Uri, title: String, type: String, onTranscript: ((String) -> Unit)? = null) {
        val item = ProcessingItem(title, "Transcribing Tamil and English on this phone…", null)
        processing += item
        scope.launch {
            val started = System.currentTimeMillis()
            try {
                val text = try {
                    audioRuntime.transcribeWav(uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (transcriptionFailure: Exception) {
                    Log.e("HOD_AUDIO", "transcription_failed", transcriptionFailure)
                    try {
                        val fallback = AudioTranscriptImportAdapter(context.contentResolver).importAudio(uri)
                        saveImported(fallback, uri)
                        status = "Recording saved for retry; transcription unavailable: ${transcriptionFailure.message ?: transcriptionFailure.javaClass.simpleName}"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (fallbackFailure: Exception) {
                        Log.e("HOD_AUDIO", "fallback_save_failed", fallbackFailure)
                        status = "Recording could not be saved: ${fallbackFailure.message ?: fallbackFailure.javaClass.simpleName}"
                    }
                    runCatching { PerformanceMetrics.record(dao, "audio_transcription_failure", 1, transcriptionFailure.javaClass.simpleName) }
                    return@launch
                }
                try {
                    saveImported(ImportedSource(title, text.ifBlank { "No speech detected" }, type, uri), uri)
                    onTranscript?.invoke(text)
                    status = "$title added for review"
                    runCatching { PerformanceMetrics.record(dao, "audio_transcription_latency_ms", System.currentTimeMillis() - started, "type=$type") }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (saveFailure: Exception) {
                    Log.e("HOD_AUDIO", "transcription_result_save_failed", saveFailure)
                    status = "Recording transcribed but could not be saved: ${saveFailure.message ?: saveFailure.javaClass.simpleName}"
                    runCatching { PerformanceMetrics.record(dao, "audio_transcription_save_failure", 1, saveFailure.javaClass.simpleName) }
                }
            } finally {
                processing.remove(item)
            }
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = cameraUri
        if (captured && uri != null) {
            status = "Reading the photo…"
            ScreenshotImportAdapter().recognize(context, uri)
                .addOnSuccessListener { item ->
                    scope.launch {
                        runCatching { saveImported(item.copy(title = "Photo · ${item.title}", type = "Camera document"), uri); status = "Photo text added for review" }
                            .onFailure { error -> Log.e("HOD_IMPORT", "camera_result_save_failed", error); status = "Photo was read but could not be saved safely." }
                    }
                }
                .addOnFailureListener {
                    scope.launch {
                        runCatching { saveImported(ImportedSource("Camera document", "OCR could not read this image; manual review required.", "Camera document", uri), uri) }
                            .onSuccess { status = "Photo saved for manual review" }
                            .onFailure { error -> Log.e("HOD_IMPORT", "camera_fallback_save_failed", error); status = "Photo could not be saved safely." }
                    }
                }
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
        scope.launch {
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
        runCatching {
            when {
            audioSelection -> transcribe(uri, "Call recording · ${timeLabel(System.currentTimeMillis())}", "Audio transcript")
            mime.startsWith("image/") -> {
                status = "Reading screenshot…"
                ScreenshotImportAdapter().recognize(context, uri)
                    .addOnSuccessListener { item ->
                        scope.launch {
                            runCatching { saveImported(item, uri); status = "Screenshot added for review" }
                                .onFailure { error -> Log.e("HOD_IMPORT", "screenshot_result_save_failed", error); status = "Screenshot was read but could not be saved safely." }
                        }
                    }
                    .addOnFailureListener {
                        scope.launch {
                            runCatching { saveImported(ScreenshotImportAdapter().create(uri), uri) }
                                .onSuccess { status = "Couldn't read text; screenshot saved for manual review" }
                                .onFailure { error -> Log.e("HOD_IMPORT", "screenshot_fallback_save_failed", error); status = "Screenshot could not be saved safely." }
                        }
                    }
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
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.e("HOD_IMPORT", "content_import_failed", error)
            status = "This file could not be imported safely: ${error.message ?: error.javaClass.simpleName}"
        }
        }
    }
    LaunchedEffect(initialSharedUri) {
        initialSharedUri?.let { uri ->
            runCatching {
                val item = OfficialDocumentImportAdapter(context).import(uri, "Gmail attachment")
                saveImported(item, uri)
                status = "Shared attachment added for review"
            }
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
                transcribe(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), "Voice note · ${timeLabel(System.currentTimeMillis())}", "Voice note") { secretaryTranscript = it }
        }
    }
    DisposableEffect(Unit) { onDispose { recorder?.let { runCatching { it.stop() }; it.release() } } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startRecording() else status = "Microphone permission is needed for voice notes." }

    // --- Calendar: permission, target calendar, writes ---
    var calendarChoices by remember { mutableStateOf<List<Pair<Long, String>>?>(null) }
    fun hasCalendarPermission() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun loadCalendars() {
        val list = mutableListOf<Pair<Long, String>>()
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL), null, null, null)?.use { c ->
                while (c.moveToNext()) if (c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) list += c.getLong(0) to "${c.getString(1)} · ${c.getString(2)}"
            }
        }.onFailure { status = "Calendars are unavailable. Check Calendar permission in Settings."; Log.e("HOD_CALENDAR", "calendar_query_failed", it) }
        calendarChoices = list
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) loadCalendars() else status = "Calendar permission is needed to add events."
    }
    val scheduler = remember { CalendarProviderScheduler.fromResolver(context.contentResolver) { p -> context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED } }
    LaunchedEffect(scheduler) {
        runCatching {
            repository.reconcilePendingCalendarEvents { event -> scheduler.findMatchingEventId(event.title, event.startsAt, event.endsAt, event.id) }
        }.onFailure { Log.e("HOD_CALENDAR", "calendar_reconcile_failed", it) }
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
        val candidates = allTasks.filter { it.assignee.isNullOrBlank() && it.status != "completed" && it.status != "deleted" && it.status != "archived" }
        val venueTravel = settings.venues.associate { it.name to it.travelMinutes.toLong() }
        val planned = SmartScheduling.propose(candidates, calendarEvents + fixedAsEvents, earliest, workEnd, venueTravel, allTasks)
        val freeMinutes = ((workEnd - earliest) / 60_000L).coerceAtLeast(0L) - fixedAsEvents.sumOf { (it.endsAt - it.startsAt).coerceAtLeast(0L) / 60_000L }
        val budget = (freeMinutes * (100 - settings.slackPercent) / 100).coerceAtLeast(0L)
        val capacity = SmartScheduling.capacity(candidates, calendarEvents + fixedAsEvents, earliest, workEnd, venueTravel, allTasks)
        planningWarning = if (capacity.overcommitted) {
            "Overloaded by ${capacity.overcommittedMinutes} min. Showing the highest-priority fit; alternatives: move low-priority work, delegate, or plan another day."
        } else null
        var used = 0L
        proposals = planned.takeWhile { b -> used += (b.end - b.start) / 60_000L + b.travelMinutes; used <= budget }.map { b ->
            ProposedBlock(b.task.id, b.task.title, b.start, b.end, clashesFor(b.start, b.end, calendarEvents), b.rationale, b.task.dueAt)
        }
        tab = TAB_CALENDAR
        if (proposals.isEmpty()) status = "Nothing to plan: no open tasks fit the free time today (fixed clinical time and ${settings.slackPercent}% free time are protected)."
        else if (planningWarning != null) status = planningWarning
    }
    // A visible proposal is a draft of the current world. If a meeting moves,
    // a task completes, or a block changes while it is open, rebuild that draft
    // automatically; no calendar write occurs until the user confirms again.
    LaunchedEffect(calendarEvents, allTasks, selectedDayEpoch) {
        if (proposals.isNotEmpty()) planDay()
    }
    fun writeProposals(selected: List<ProposedBlock>? = null) {
        val calendarId = settings.calendarId
        val chosen = selected ?: proposals
        val toWrite = chosen.filter { it.clashesWith.isEmpty() }
        if (calendarId == null || !hasCalendarPermission()) {
            status = "Choose which calendar to write to first."
            settingsStack.clear(); settingsStack += "home"; settingsStack += "calendar"
            return
        }
        scope.launch {
            try {
                var written = 0
                val denied = mutableListOf<String>()
                toWrite.forEach { p ->
                    val taskId = p.taskId
                    if (taskId != null && dao.eventsForTask(taskId).any { it.startsAt == p.start && it.endsAt == p.end && it.confirmed && it.calendarEventId != null }) return@forEach
                    val proposal = ScheduleProposal(EventEntity(taskId = p.taskId, title = p.title, startsAt = p.start, endsAt = p.end), emptyList())
                    // Persist first. If the process dies after the provider insert,
                    // the pending row is available for reconciliation on next start.
                    val localId = repository.beginCalendarWrite(proposal.event)
                    when (val r = scheduler.writeConfirmed(proposal, calendarId, confirmed = true, localEventId = localId)) {
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
                proposals = proposals.filterNot { it in chosen }
                status = buildString {
                    append("$written block(s) added to ${settings.calendarName ?: "your calendar"}.")
                    if (denied.isNotEmpty()) append(" Skipped: ").append(denied.joinToString("; "))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e("HOD_CALENDAR", "calendar_write_failed", error)
                status = "Calendar update stopped: ${error.message ?: error.javaClass.simpleName}. Confirmed writes are retained; pending writes will be reconciled."
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
        if (intent != null) vaultUnlock.launch(intent) else { status = "Set a screen lock on this phone before opening the Clinical Vault." }
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
    val reviewQueue = sources.filter { it.processingStatus !in setOf("deleted", "archived") && it.body.isNotBlank() && !it.reviewed && !it.isClinical() }
        .sortedWith(compareByDescending<SourceEntity> { it.priorityScore }.thenByDescending { it.capturedAt })
    val aiHealthy = embeddingReady && (indexHealth?.healthy != false)
    val secretaryState = remember(sources, tasks, todayEvents, commitments) {
        SecretaryStateBuilder.build(sources, tasks, todayEvents, commitments)
    }
    val learningExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val corrections = dao.allLearningCorrections()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.appendLine("Psychiatry HOD Desk — redacted learning profile")
                    writer.appendLine("This export contains local corrections only; no automatic sharing occurs.")
                    SecretaryLearning.exportLines(corrections).forEach(writer::appendLine)
                }
            }.onSuccess { status = "Learning profile exported (${corrections.size} corrections)." }
                .onFailure { status = "Learning export failed: ${it.message}" }
        }
    }
    var reasoningProgress by remember { mutableStateOf<Int?>(null) }

    val c = Hod.colors
    Box(Modifier.fillMaxSize()) {
        GroupedPage {
            when (tab) {
                TAB_TODAY -> TodayScreen(
                    sources = sources, tasks = tasks, todayEvents = todayEvents.filter { it.id !in hiddenEvents }, commitments = commitments, todayBlocks = todayBlocks,
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
                    searchRelated = { text, id -> semanticIndexer.search(text, searchable).filter { it.source.id != id } },
                    rankActions = { list -> if (embeddingReady) EmbeddingGemmaActionRanker(embeddingRuntime).rank(list).map { it.first } else list },
                    instructionModelPath = installedInstructionModel,
                    onReasoning = { id, output -> viewModel.saveReasoning(id, output) },
                    onUpdateActionPoint = { point -> viewModel.saveReviewPoint(point) },
                    onApproveEdited = { source, point, action, assignee -> viewModel.approveReviewPoint(point, source.id, source.sourceType, action, assignee) },
                    onApprove = { source, action, assignee, dueAt -> viewModel.approveReviewAction(source.id, action, source.sourceType, dueAt, assignee) },
                    onActionStatus = { id, action, s -> viewModel.statusReviewAction(id, action, s) },
                    onDismissSource = { id, reason -> viewModel.dismissReviewSource(id, reason) },
                    onBack = { tab = TAB_TODAY },
                )
                TAB_TASKS -> TasksScreen(tasks, tasksSegment, onToggleDone = { t -> viewModel.setDone(t.id, t.status != "completed") }, onOpen = { openTaskId = it.id })
                TAB_CALENDAR -> CalendarScreen(
                    selectedDay = selectedDay, events = calendarEvents.filter { it.id !in hiddenEvents }, fixedBlocks = selectedDayBlocks,
                    proposals = proposals, onSelectDay = { selectedDayEpoch = it.toEpochDay(); proposals = emptyList(); planningWarning = null },
                    onPlanDay = ::planDay, onProtectTime = { protectTimePicker = true }, onOpenEvent = { openEvent = it },
                    onConfirmProposals = { writeProposals() }, onConfirmProposal = { writeProposals(listOf(it)) }, onDiscardProposals = { proposals = emptyList(); planningWarning = null },
                    planningWarning = planningWarning,
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
                    LaunchedEffect(audioRuntime) { verifiedTamilSpeechReady = audioRuntime.tamilModelReady() }
                    val h = indexHealth
                    val latestSnapshot = IndexSnapshotManager.snapshots(context).firstOrNull()
                    val activeSnapshot = IndexSnapshotManager.active(context)
                    AiPage(
                        status = AiStatus(
                            healthy = aiHealthy,
                            headline = if (aiHealthy) "Everything is working" else "Smart search needs attention",
                            detail = if (aiHealthy) "Search, speech and reading run on this phone" else embeddingLine,
                            searchLine = h?.let { "${it.indexedSourceCount} of ${it.sourceCount} items indexed · Tamil + English" } ?: "Checking…",
                            speechReady = verifiedTamilSpeechReady,
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
                        onSelfCheck = { scope.launch { aiBusy = "Checking…"; val modelCheck = IndexSelfCheck.run(embeddingRuntime); indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull(); aiBusy = if (modelCheck.isFailure) "Model self-check failed · keyword search remains available." else indexHealth?.let { if (it.healthy) "Self-check passed." else "Self-check found ${it.staleSourceIds.size} stale and ${it.invalidSourceIds.size} invalid item(s). Rebuild the index." } ?: "Index self-check could not run." } },
                        onRebuild = { scope.launch { aiBusy = "Rebuilding search index…"; runCatching { semanticIndexer.indexAll(searchable) }.onSuccess { embeddingReady = true; aiBusy = "Search index rebuilt." }.onFailure { aiBusy = "Rebuild failed: ${it.message}" }; indexHealth = runCatching { IndexHealthChecker(dao).inspect(allSources) }.getOrNull() } },
                        snapshotSummary = latestSnapshot?.let { "${if (it.id == activeSnapshot) "Active · " else "Rollback · "}${it.modelVersion} · ${it.sourceCount} sources" },
                        onCreateSnapshot = { scope.launch {
                            val embeddings = dao.allSemanticEmbeddings()
                            val chunks = dao.allSourceChunks(SourceSemanticIndexer.MODEL_VERSION)
                            val snapshot = IndexSnapshotManager.create(SourceSemanticIndexer.MODEL_VERSION, EmbeddingPromptRegistry.VERSION, embeddings.map { it.sourceId }, chunks.size)
                            IndexSnapshotManager.save(context, dao, snapshot)
                            IndexSnapshotManager.activate(context, snapshot)
                            status = "Search index snapshot saved."; settingsVersion++
                        } },
                        onRestoreSnapshot = latestSnapshot?.let { snapshot -> { scope.launch {
                            if (IndexSnapshotManager.restore(context, dao, snapshot)) { status = "Search index restored from snapshot."; settingsVersion++ } else status = "Snapshot restore rejected; current index is unchanged."
                        } } },
                        onResetLearning = { scope.launch { repository.resetLearningProfile(); status = "Learned preferences reset." } },
                        onExportLearning = { learningExportLauncher.launch("hod-desk-learning-${LocalDate.now()}.txt") },
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
                    if (matter != null) MatterPage(
                        MatterTimelineBuilder.build(matter, allTasks, calendarEvents),
                        onBack = ::pop,
                        onReviewChange = { tab = TAB_REVIEW; pop() },
                        onAcceptChange = { taskId -> scope.launch { repository.resolveChangeProposal(taskId, "accepted"); status = "Change accepted."; pop() } },
                        onKeepExisting = { taskId -> scope.launch { repository.resolveChangeProposal(taskId, "kept_existing"); status = "Existing task kept."; pop() } },
                        onCreateNew = { taskId -> scope.launch { allTasks.firstOrNull { it.id == taskId }?.sourceId?.let { repository.recordMatterDecision(it, "create_new") }; status = "Create-new proposal recorded for review."; pop() } },
                        onKeepSeparate = { taskId -> scope.launch { allTasks.firstOrNull { it.id == taskId }?.sourceId?.let { repository.recordMatterDecision(it, "keep_separate") }; status = "Keep-separate decision recorded."; pop() } },
                    )
                    else com.hoddesk.app.ui.components.EmptyState("Matter not available", "The related sources have not been indexed yet.")
                }
                "followups" -> FollowUpPage(
                    candidates = FollowUpEngine.candidates(commitments),
                    onBack = ::pop,
                    onDraftGenerated = { candidate -> scope.launch { dao.insertAudit(com.hoddesk.app.data.AuditEntryEntity(entityType = "commitment", entityId = candidate.commitmentId, action = "followup_draft_generated", detail = candidate.draft.take(1000))) } },
                    onDraftSaved = { candidate -> status = "Draft saved for review."; scope.launch { dao.insertAudit(com.hoddesk.app.data.AuditEntryEntity(entityType = "commitment", entityId = candidate.commitmentId, action = "followup_draft_saved", detail = candidate.draft.take(1000))) } },
                    onCopy = { candidate -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("HOD Desk follow-up draft", candidate.draft)); status = "Draft copied for review."; scope.launch { dao.insertAudit(com.hoddesk.app.data.AuditEntryEntity(entityType = "commitment", entityId = candidate.commitmentId, action = "followup_copied", detail = candidate.draft.take(1000))) } },
                    onShare = { candidate -> status = "Review the draft in your sharing app before sending."; scope.launch { dao.insertAudit(com.hoddesk.app.data.AuditEntryEntity(entityType = "commitment", entityId = candidate.commitmentId, action = "followup_share_started", detail = candidate.draft.take(1000))) }; runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, candidate.draft) }, "Review follow-up before sending")) }.onFailure { status = "No sharing app is available; copy the draft instead." } },
                    onDismiss = { candidate -> scope.launch { dao.setCommitmentStatus(candidate.commitmentId, "dismissed"); dao.insertAudit(com.hoddesk.app.data.AuditEntryEntity(entityType = "commitment", entityId = candidate.commitmentId, action = "followup_dismissed", detail = candidate.evidence)); status = "Follow-up dismissed." } },
                )
                "secretary" -> SecretaryPage(initialTranscript = secretaryTranscript, supportingSources = secretaryEvidence, proposalDiff = secretaryProposalDiff, onBack = ::pop,
                    onRecord = { if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    onTranscriptChanged = { secretaryTranscript = it }, onRun = { intent ->
                    fun auditCommand(action: String, detail: String) { scope.launch { dao.insertAudit(com.hoddesk.app.data.AuditEntryEntity(entityType = "secretary", entityId = 0, action = action, detail = detail.take(1000))) } }
                    val evidenceIds = secretaryEvidence.map { it.id }
                    val commandDetail = "${intent.query}; evidence=$evidenceIds".take(1000)
                    val citationDigest = secretaryEvidence.take(5).joinToString(" • ") { source ->
                        "${source.title}: ${source.body.trim().replace(Regex("\\s+"), " ").take(120)}"
                    }.ifBlank { "No cited source evidence was retrieved." }
                    when (intent.kind) {
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.SHOW_WAITING -> { tasksSegment = 1; tab = TAB_TASKS; pop(); status = "Showing work you are waiting on."; auditCommand("secretary_read_waiting", intent.query) }
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.SHOW_MEETINGS -> { if (intent.query.contains("tomorrow", true) || intent.query.contains("நாளை")) selectedDayEpoch = LocalDate.now().plusDays(1).toEpochDay(); tab = TAB_CALENDAR; pop(); status = "Showing your calendar."; auditCommand("secretary_read_meetings", intent.query) }
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.SEARCH -> { libraryQuery = intent.query; tab = TAB_LIBRARY; pop(); status = "Searching your indexed sources."; auditCommand("secretary_read_search", "query=${intent.query};evidence=${secretaryEvidence.map { it.id }}") }
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.PLAN_DAY -> { planDay(); pop(); status = "Review the proposed day plan before applying it."; auditCommand("secretary_proposed_plan", intent.query) }
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.CREATE_TASK -> {
                            val title = intent.parameters["target"].orEmpty().removePrefix("to ").trim()
                            if (title.isBlank()) { status = "The task wording is missing; nothing was created."; auditCommand("secretary_rejected_create", commandDetail) } else scope.launch {
                                try {
                                    repository.createSecretaryTask(title, commandDetail)
                                    tasksSegment = 0; tab = TAB_TASKS; status = "Task created after confirmation. Evidence: $citationDigest"; pop()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { status = failure.message ?: "The task could not be created; no partial change was kept." }
                            }
                        }
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.DELEGATE_TASK -> {
                            val active = allTasks.filter { it.status !in setOf("completed", "deleted", "archived") }
                            val match = com.hoddesk.app.secretary.VoiceCommandTargeting.resolve(intent.query, active, "task", { it.id }, { "${it.title} ${it.project}" }, settings.team.map { it.name })
                            val task = match.record
                            val memberName = com.hoddesk.app.secretary.VoiceCommandTargeting.uniqueAssignee(intent.query, settings.team.map { it.name })
                            if (task == null || memberName == null) { status = match.error ?: "Name one configured team member; no task was changed."; auditCommand("secretary_rejected_delegate", commandDetail) } else scope.launch {
                                try {
                                    repository.delegateSecretaryTask(task, memberName, "$commandDetail; ${task.title} -> $memberName")
                                    tasksSegment = 1; tab = TAB_TASKS; status = "${task.title} delegated to $memberName after confirmation. Evidence: $citationDigest"; pop()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { status = failure.message ?: "Delegation failed; no partial change was kept." }
                            }
                        }
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.MOVE_TASKS,
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.UPDATE_TASK,
                        com.hoddesk.app.secretary.SecretaryIntent.Kind.SCHEDULE_TASK -> {
                            val time = com.hoddesk.app.secretary.VoiceSecretary.time(intent.query)
                            val active = allTasks.filter { it.status !in setOf("completed", "deleted", "archived") }
                            val eventMatch = com.hoddesk.app.secretary.VoiceCommandTargeting.resolve(intent.query, secretaryEvents, "event", { it.id }, { "${it.title} ${it.location.orEmpty()}" })
                            val taskMatch = com.hoddesk.app.secretary.VoiceCommandTargeting.resolve(intent.query, active, "task", { it.id }, { "${it.title} ${it.project}" })
                            val event = eventMatch.record
                            val task = taskMatch.record
                            if (time == null || (event == null && (intent.kind != com.hoddesk.app.secretary.SecretaryIntent.Kind.SCHEDULE_TASK || task == null))) { status = if (time == null) "State an exact time; no change was made." else eventMatch.error ?: taskMatch.error ?: "Name one exact meeting or task; no change was made."; auditCommand("secretary_rejected_schedule", commandDetail) } else scope.launch {
                                val date = if (event != null) java.time.Instant.ofEpochMilli(event.startsAt).atZone(zone).toLocalDate() else if (intent.query.contains("tomorrow", true)) LocalDate.now().plusDays(1) else LocalDate.now()
                                val newStart = date.atTime(time.first, time.second).atZone(zone).toInstant().toEpochMilli()
                                try {
                                    if (event != null) repository.moveSecretaryEvent(event, newStart, "$commandDetail; ${event.startsAt} -> $newStart")
                                    else repository.scheduleSecretaryTask(requireNotNull(task), newStart, "$commandDetail; scheduled=$newStart")
                                    selectedDayEpoch = date.toEpochDay(); tab = TAB_CALENDAR
                                    status = "${event?.title ?: task?.title} changed after confirmation. Evidence: $citationDigest"; pop()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { status = failure.message ?: "The schedule was not changed; no partial change was kept." }
                            }
                        }
                        else -> { status = "This secretary action is not supported yet; no data was changed."; auditCommand("secretary_rejected_unsupported", intent.query) }
                    }
                })
                "control" -> SecretaryControlCentre(
                    state = secretaryState,
                    aiHealthy = aiHealthy,
                    onBack = ::pop,
                    onOpenReview = { tab = TAB_REVIEW; pop() },
                    onOpenCalendar = { tab = TAB_CALENDAR; pop() },
                    onOpenWaiting = { tasksSegment = 1; tab = TAB_TASKS; pop() },
                )
                "evaluation" -> EvaluationLabPage(sources = allSources, onBack = ::pop)
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
                    SheetAction("Mark overrun until now") { scope.launch { val extended = maxOf(e.endsAt, System.currentTimeMillis()); if (extended > e.endsAt) { dao.updateEvent(e.copy(endsAt = extended)); status = "Overrun protected; planning proposals refreshed." } else status = "This event has not overrun yet." }; openEvent = null },
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
            scope.launch {
                try {
                    saveImported(ImportedSource(title, body, type), null)
                    status = "Note added for review"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { status = "Note could not be saved: ${failure.message ?: failure.javaClass.simpleName}" }
            }
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
