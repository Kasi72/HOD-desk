package com.hoddesk.app.backup

import android.content.Context
import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.AuditEntryEntity
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.EvidenceEntity
import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.LearningCorrectionEntity
import com.hoddesk.app.data.ReasoningRunEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.SourceRelationEntity
import com.hoddesk.app.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What a backup holds, shown to the user before anything is replaced. */
data class BackupSummary(val createdAt: Long, val appVersion: String, val sources: Int, val tasks: Int, val events: Int, val auditEntries: Int)

/** A decrypted, fully parsed backup. Nothing on the phone has changed yet. */
class ParsedBackup internal constructor(
    val summary: BackupSummary,
    internal val sources: List<SourceEntity>,
    internal val tasks: List<TaskEntity>,
    internal val events: List<EventEntity>,
    internal val evidence: List<EvidenceEntity>,
    internal val actionPoints: List<ActionPointEntity>,
    internal val reasoningRuns: List<ReasoningRunEntity>,
    internal val relations: List<SourceRelationEntity>,
    internal val audit: List<AuditEntryEntity>,
    internal val commitments: List<CommitmentEntity>,
    internal val learningCorrections: List<LearningCorrectionEntity>,
    internal val prefs: JSONObject,
)

class BackupTooNewException : Exception("This backup was made by a newer version of HOD Desk. Update the app first.")

/**
 * Exports all work data and settings into one password-encrypted file, and restores it.
 * The database key, model files and search vectors are never exported; device-specific
 * settings (which Android calendar to write to) are not restored onto another phone.
 */
class BackupManager(private val context: Context, private val dao: HodDeskDao) {
    companion object {
        private const val FORMAT = "hoddesk-backup"
        private const val FORMAT_VERSION = 1
        /** Shared-preference files that hold user settings (never the database key file). */
        private val PREF_FILES = listOf("hod_desk_settings", "hod_desk_privacy")
        /** Keys that only make sense on the phone they were set on. */
        private val DEVICE_SPECIFIC_KEYS = setOf("calendar_id", "calendar_name")
        private const val LAST_BACKUP_PREFS = "hod_desk_backup"
    }

    private val lastBackupPrefs = context.getSharedPreferences(LAST_BACKUP_PREFS, Context.MODE_PRIVATE)
    val lastBackupAt: Long? get() = lastBackupPrefs.getLong("last_backup_at", -1L).takeIf { it > 0 }

    suspend fun export(password: CharArray, appVersion: String): Pair<ByteArray, BackupSummary> = withContext(Dispatchers.IO) {
        val sources = dao.allSources()
        val tasks = dao.allTasks()
        val events = dao.allEvents()
        val audit = dao.allAudit()
        val now = System.currentTimeMillis()
        val root = JSONObject()
            .put("format", FORMAT).put("formatVersion", FORMAT_VERSION).put("createdAt", now).put("appVersion", appVersion)
            .put("sources", JSONArray(sources.map(::sourceJson)))
            .put("tasks", JSONArray(tasks.map(::taskJson)))
            .put("events", JSONArray(events.map(::eventJson)))
            .put("evidence", JSONArray(dao.allEvidence().map(::evidenceJson)))
            .put("actionPoints", JSONArray(dao.allActionPoints().map(::actionPointJson)))
            .put("reasoningRuns", JSONArray(dao.allReasoningRuns().map(::reasoningJson)))
            .put("relations", JSONArray(dao.allSourceRelations().map(::relationJson)))
            .put("audit", JSONArray(audit.map(::auditJson)))
            .put("commitments", JSONArray(dao.allCommitments().map(::commitmentJson)))
            .put("learningCorrections", JSONArray(dao.allLearningCorrections().map(::learningJson)))
            .put("prefs", exportPrefs())
        val bytes = BackupCodec.encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
        bytes to BackupSummary(now, appVersion, sources.size, tasks.size, events.size, audit.size)
    }

    fun markBackedUp() = lastBackupPrefs.edit().putLong("last_backup_at", System.currentTimeMillis()).apply()

    /** Decrypts and parses completely before anything is touched, so a bad file or password changes nothing. */
    suspend fun read(file: ByteArray, password: CharArray): ParsedBackup = withContext(Dispatchers.Default) {
        val json = String(BackupCodec.decrypt(file, password), Charsets.UTF_8)
        try {
            parse(JSONObject(json))
        } catch (error: org.json.JSONException) {
            throw NotABackupException()
        }
    }

    private fun parse(root: JSONObject): ParsedBackup {
        if (root.optString("format") != FORMAT) throw NotABackupException()
        if (root.optInt("formatVersion", 0) > FORMAT_VERSION) throw BackupTooNewException()
        val sources = root.list("sources", ::sourceFrom)
        val tasks = root.list("tasks", ::taskFrom)
        val events = root.list("events", ::eventFrom)
        val audit = root.list("audit", ::auditFrom)
        return ParsedBackup(
            summary = BackupSummary(root.getLong("createdAt"), root.optString("appVersion"), sources.size, tasks.size, events.size, audit.size),
            sources = sources, tasks = tasks, events = events,
            evidence = root.list("evidence", ::evidenceFrom),
            actionPoints = root.list("actionPoints", ::actionPointFrom),
            reasoningRuns = root.list("reasoningRuns", ::reasoningFrom),
            relations = root.list("relations", ::relationFrom),
            audit = audit,
            commitments = root.list("commitments", ::commitmentFrom),
            learningCorrections = root.list("learningCorrections", ::learningFrom),
            prefs = root.optJSONObject("prefs") ?: JSONObject(),
        )
    }

    suspend fun restore(backup: ParsedBackup) = withContext(Dispatchers.IO) {
        dao.replaceAllData(backup.sources, backup.tasks, backup.events, backup.evidence, backup.actionPoints, backup.reasoningRuns, backup.relations, backup.audit, backup.commitments, backup.learningCorrections)
        restorePrefs(backup.prefs)
        dao.insertAudit(AuditEntryEntity(entityType = "app", entityId = 0, action = "backup_restored", detail = "backup of ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(backup.summary.createdAt))}"))
    }

    // --- Settings ---

    private fun exportPrefs(): JSONObject {
        val out = JSONObject()
        PREF_FILES.forEach { name ->
            val file = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (key in DEVICE_SPECIFIC_KEYS) return@forEach
                when (value) {
                    is Boolean -> file.put(key, JSONObject().put("t", "b").put("v", value))
                    is Int -> file.put(key, JSONObject().put("t", "i").put("v", value))
                    is Long -> file.put(key, JSONObject().put("t", "l").put("v", value))
                    is Float -> file.put(key, JSONObject().put("t", "f").put("v", value.toDouble()))
                    is String -> file.put(key, JSONObject().put("t", "s").put("v", value))
                    is Set<*> -> file.put(key, JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() })))
                }
            }
            out.put(name, file)
        }
        return out
    }

    private fun restorePrefs(prefs: JSONObject) {
        PREF_FILES.forEach { name ->
            val file = prefs.optJSONObject(name) ?: return@forEach
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            file.keys().forEach { key ->
                if (key in DEVICE_SPECIFIC_KEYS) return@forEach
                val entry = file.optJSONObject(key) ?: return@forEach
                when (entry.optString("t")) {
                    "b" -> editor.putBoolean(key, entry.getBoolean("v"))
                    "i" -> editor.putInt(key, entry.getInt("v"))
                    "l" -> editor.putLong(key, entry.getLong("v"))
                    "f" -> editor.putFloat(key, entry.getDouble("v").toFloat())
                    "s" -> editor.putString(key, entry.getString("v"))
                    "ss" -> editor.putStringSet(key, entry.getJSONArray("v").let { a -> (0 until a.length()).map(a::getString).toSet() })
                }
            }
            editor.commit()
        }
    }

    // --- Entity <-> JSON ---

    // Every column is written by name, so adding a field to an entity cannot shift values into the wrong column.
    // Fields missing from an older backup fall back to the entity's own defaults.

    private fun sourceJson(s: SourceEntity) = JSONObject().put("id", s.id).put("title", s.title).put("body", s.body).put("sourceType", s.sourceType)
        .putOpt("sourceUri", s.sourceUri).put("capturedAt", s.capturedAt).put("reviewed", s.reviewed).put("sensitivity", s.sensitivity)
        .put("processingStatus", s.processingStatus).put("lifecycleState", s.lifecycleState).put("triageBucket", s.triageBucket)
        .put("priorityScore", s.priorityScore).putOpt("priorityReasons", s.priorityReasons).put("intentKind", s.intentKind)
        .putOpt("rawText", s.rawText).putOpt("correctedText", s.correctedText).putOpt("detectedLanguage", s.detectedLanguage)
        .putOpt("transcriptConfidence", s.transcriptConfidence?.toDouble()).putOpt("evidenceLocator", s.evidenceLocator).putOpt("snoozedUntil", s.snoozedUntil)
    private fun sourceFrom(o: JSONObject): SourceEntity {
        val d = SourceEntity(title = "", body = "", sourceType = "")
        return SourceEntity(
            id = o.getLong("id"), title = o.getString("title"), body = o.getString("body"), sourceType = o.getString("sourceType"),
            sourceUri = o.str("sourceUri"), capturedAt = o.getLong("capturedAt"), reviewed = o.optBoolean("reviewed", d.reviewed),
            sensitivity = o.optString("sensitivity", d.sensitivity), processingStatus = o.optString("processingStatus", d.processingStatus),
            lifecycleState = o.optString("lifecycleState", d.lifecycleState), triageBucket = o.optString("triageBucket", d.triageBucket),
            priorityScore = o.optInt("priorityScore", d.priorityScore), priorityReasons = o.str("priorityReasons"),
            intentKind = o.optString("intentKind", d.intentKind), rawText = o.str("rawText"), correctedText = o.str("correctedText"),
            detectedLanguage = o.str("detectedLanguage"), transcriptConfidence = o.float("transcriptConfidence"), evidenceLocator = o.str("evidenceLocator"),
            snoozedUntil = o.long("snoozedUntil"),
        )
    }

    private fun taskJson(t: TaskEntity) = JSONObject().put("id", t.id).putOpt("sourceId", t.sourceId).put("title", t.title).put("project", t.project)
        .putOpt("dueAt", t.dueAt).put("priority", t.priority).put("status", t.status).putOpt("blocker", t.blocker).putOpt("clarification", t.clarification)
        .putOpt("assignee", t.assignee).putOpt("recurrenceRule", t.recurrenceRule).put("createdAt", t.createdAt).putOpt("completedAt", t.completedAt)
        .putOpt("approvalOperationId", t.approvalOperationId)
    private fun taskFrom(o: JSONObject): TaskEntity {
        val d = TaskEntity(title = "", project = "")
        return TaskEntity(
            id = o.getLong("id"), sourceId = o.long("sourceId"), title = o.getString("title"), project = o.optString("project"),
            dueAt = o.long("dueAt"), priority = o.optString("priority", d.priority), status = o.optString("status", d.status),
            blocker = o.str("blocker"), clarification = o.str("clarification"), assignee = o.str("assignee"), recurrenceRule = o.str("recurrenceRule"),
            createdAt = o.getLong("createdAt"), completedAt = o.long("completedAt"), approvalOperationId = o.str("approvalOperationId"),
        )
    }

    private fun eventJson(e: EventEntity) = JSONObject().put("id", e.id).putOpt("taskId", e.taskId).put("title", e.title).put("startsAt", e.startsAt)
        .put("endsAt", e.endsAt).putOpt("location", e.location).put("confirmed", e.confirmed).putOpt("calendarEventId", e.calendarEventId)
    private fun eventFrom(o: JSONObject) = EventEntity(
        id = o.getLong("id"), taskId = o.long("taskId"), title = o.getString("title"), startsAt = o.getLong("startsAt"), endsAt = o.getLong("endsAt"),
        location = o.str("location"), confirmed = o.optBoolean("confirmed", false), calendarEventId = o.long("calendarEventId"),
    )

    private fun evidenceJson(e: EvidenceEntity) = JSONObject().put("id", e.id).put("taskId", e.taskId).put("uri", e.uri).put("type", e.type)
        .putOpt("note", e.note).put("capturedAt", e.capturedAt)
    private fun evidenceFrom(o: JSONObject) = EvidenceEntity(
        id = o.getLong("id"), taskId = o.getLong("taskId"), uri = o.getString("uri"), type = o.getString("type"), note = o.str("note"), capturedAt = o.getLong("capturedAt"),
    )

    private fun actionPointJson(a: ActionPointEntity) = JSONObject().put("id", a.id).put("sourceId", a.sourceId).put("action", a.action).put("category", a.category)
        .putOpt("dateText", a.dateText).putOpt("timeText", a.timeText).putOpt("locationText", a.locationText).putOpt("peopleText", a.peopleText)
        .put("evidence", a.evidence).put("confidence", a.confidence.toDouble()).put("status", a.status).putOpt("ambiguity", a.ambiguity)
        .putOpt("ownerText", a.ownerText).putOpt("referenceNumber", a.referenceNumber).putOpt("recurrenceRule", a.recurrenceRule)
        .put("intentKind", a.intentKind).put("fieldConfidenceJson", a.fieldConfidenceJson).put("fieldCitationsJson", a.fieldCitationsJson)
        .put("conflictJson", a.conflictJson).put("createdAt", a.createdAt)
    private fun actionPointFrom(o: JSONObject): ActionPointEntity {
        val d = ActionPointEntity(sourceId = 0, action = "", evidence = "", confidence = 0f)
        return ActionPointEntity(
            id = o.getLong("id"), sourceId = o.getLong("sourceId"), action = o.getString("action"), category = o.optString("category", d.category),
            dateText = o.str("dateText"), timeText = o.str("timeText"), locationText = o.str("locationText"), peopleText = o.str("peopleText"),
            evidence = o.getString("evidence"), confidence = o.getDouble("confidence").toFloat(), status = o.optString("status", d.status),
            ambiguity = o.str("ambiguity"), ownerText = o.str("ownerText"), referenceNumber = o.str("referenceNumber"), recurrenceRule = o.str("recurrenceRule"),
            intentKind = o.optString("intentKind", d.intentKind), fieldConfidenceJson = o.optString("fieldConfidenceJson", d.fieldConfidenceJson),
            fieldCitationsJson = o.optString("fieldCitationsJson", d.fieldCitationsJson), conflictJson = o.optString("conflictJson", d.conflictJson),
            createdAt = o.getLong("createdAt"),
        )
    }

    private fun reasoningJson(r: ReasoningRunEntity) = JSONObject().put("id", r.id).put("sourceId", r.sourceId).put("modelVersion", r.modelVersion).put("action", r.action)
        .put("category", r.category).putOpt("dateText", r.dateText).putOpt("timeText", r.timeText).putOpt("locationText", r.locationText)
        .put("peopleJson", r.peopleJson).put("ambiguitiesJson", r.ambiguitiesJson).put("citationsJson", r.citationsJson).put("confidence", r.confidence.toDouble())
        .put("fieldConfidenceJson", r.fieldConfidenceJson).put("fieldCitationsJson", r.fieldCitationsJson).put("intentKind", r.intentKind).put("createdAt", r.createdAt)
    private fun reasoningFrom(o: JSONObject): ReasoningRunEntity {
        val d = ReasoningRunEntity(sourceId = 0, modelVersion = "", action = "", category = "", peopleJson = "", ambiguitiesJson = "", citationsJson = "", confidence = 0f)
        return ReasoningRunEntity(
            id = o.getLong("id"), sourceId = o.getLong("sourceId"), modelVersion = o.getString("modelVersion"), action = o.getString("action"),
            category = o.getString("category"), dateText = o.str("dateText"), timeText = o.str("timeText"), locationText = o.str("locationText"),
            peopleJson = o.getString("peopleJson"), ambiguitiesJson = o.getString("ambiguitiesJson"), citationsJson = o.getString("citationsJson"),
            confidence = o.getDouble("confidence").toFloat(), fieldConfidenceJson = o.optString("fieldConfidenceJson", d.fieldConfidenceJson),
            fieldCitationsJson = o.optString("fieldCitationsJson", d.fieldCitationsJson), intentKind = o.optString("intentKind", d.intentKind),
            createdAt = o.getLong("createdAt"),
        )
    }

    private fun relationJson(r: SourceRelationEntity) = JSONObject().put("id", r.id).put("sourceId", r.sourceId).put("relatedSourceId", r.relatedSourceId)
        .put("relationType", r.relationType).put("score", r.score.toDouble()).put("rationale", r.rationale).put("createdAt", r.createdAt)
    private fun relationFrom(o: JSONObject) = SourceRelationEntity(
        id = o.getLong("id"), sourceId = o.getLong("sourceId"), relatedSourceId = o.getLong("relatedSourceId"), relationType = o.getString("relationType"),
        score = o.getDouble("score").toFloat(), rationale = o.getString("rationale"), createdAt = o.getLong("createdAt"),
    )

    private fun auditJson(a: AuditEntryEntity) = JSONObject().put("id", a.id).put("entityType", a.entityType).put("entityId", a.entityId).put("action", a.action)
        .putOpt("detail", a.detail).put("createdAt", a.createdAt)
    private fun auditFrom(o: JSONObject) = AuditEntryEntity(
        id = o.getLong("id"), entityType = o.getString("entityType"), entityId = o.getLong("entityId"), action = o.getString("action"),
        detail = o.str("detail"), createdAt = o.getLong("createdAt"),
    )

    private fun commitmentJson(c: CommitmentEntity) = JSONObject().put("id", c.id).putOpt("sourceId", c.sourceId).putOpt("taskId", c.taskId)
        .put("direction", c.direction).putOpt("owner", c.owner).putOpt("counterparty", c.counterparty).put("summary", c.summary).putOpt("dueAt", c.dueAt)
        .put("status", c.status).put("evidence", c.evidence).put("confidence", c.confidence.toDouble()).put("createdAt", c.createdAt).put("updatedAt", c.updatedAt)
    private fun commitmentFrom(o: JSONObject) = CommitmentEntity(
        id = o.getLong("id"), sourceId = o.long("sourceId"), taskId = o.long("taskId"), direction = o.getString("direction"), owner = o.str("owner"),
        counterparty = o.str("counterparty"), summary = o.getString("summary"), dueAt = o.long("dueAt"), status = o.optString("status", "open"),
        evidence = o.getString("evidence"), confidence = o.optDouble("confidence", 1.0).toFloat(), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
    )

    private fun learningJson(l: LearningCorrectionEntity) = JSONObject().put("id", l.id).put("actionPointId", l.actionPointId).put("field", l.field)
        .putOpt("before", l.before).putOpt("after", l.after).put("createdAt", l.createdAt)
    private fun learningFrom(o: JSONObject) = LearningCorrectionEntity(
        id = o.getLong("id"), actionPointId = o.getLong("actionPointId"), field = o.getString("field"), before = o.str("before"), after = o.str("after"),
        createdAt = o.getLong("createdAt"),
    )

    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.float(key: String): Float? = if (has(key) && !isNull(key)) getDouble(key).toFloat() else null
    private fun <T> JSONObject.list(key: String, parse: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { parse(array.getJSONObject(it)) }
    }
}
