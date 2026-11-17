package com.hoddesk.app.health

/**
 * Start-up self-check. Turns a snapshot of the phone's state into plain-language problems, so a
 * broken part shows one clear banner instead of a feature quietly failing. Pure: no Android calls.
 */
object SystemHealth {
    enum class Fix { NONE, APP_SETTINGS, SECURITY_SETTINGS, AI_SETTINGS }
    enum class Severity { BLOCKED, ATTENTION }

    data class Issue(val id: String, val title: String, val message: String, val severity: Severity, val fix: Fix, val fixLabel: String? = null)

    data class Snapshot(
        /** null = the database check has not finished yet. */
        val databaseOk: Boolean?,
        val calendarChosen: Boolean,
        val calendarPermission: Boolean,
        val remindersOn: Boolean,
        val notificationPermission: Boolean,
        val autoLockOn: Boolean,
        val deviceSecure: Boolean,
        val freeBytes: Long,
        /** The AI reading model was removed because it failed its check (e.g. after a model update). */
        val reasoningModelNeedsDownload: Boolean,
    )

    /** Below this, imports, the model download and backups are likely to fail. */
    const val LOW_STORAGE_BYTES = 500L * 1024 * 1024

    fun check(s: Snapshot): List<Issue> = buildList {
        if (s.databaseOk == false) add(Issue(
            "database", "Your data could not be read",
            "HOD Desk could not open its encrypted storage. Do not uninstall the app. Restart the phone; if this stays, restore from your latest backup.",
            Severity.BLOCKED, Fix.NONE,
        ))
        if (s.freeBytes in 0 until LOW_STORAGE_BYTES) add(Issue(
            "storage", "Phone storage is almost full",
            "Less than 500 MB is free. Imports, backups and model downloads may fail until you free some space.",
            Severity.ATTENTION, Fix.NONE,
        ))
        if (s.calendarChosen && !s.calendarPermission) add(Issue(
            "calendar", "Calendar access is turned off",
            "Approved meetings and tasks cannot be written to your calendar. Allow Calendar for HOD Desk in Android settings.",
            Severity.ATTENTION, Fix.APP_SETTINGS, "Fix",
        ))
        if (s.remindersOn && !s.notificationPermission) add(Issue(
            "notifications", "Reminders cannot ring",
            "Notifications are turned off for HOD Desk, so the morning brief and due-soon alerts will not appear.",
            Severity.ATTENTION, Fix.APP_SETTINGS, "Fix",
        ))
        if (s.autoLockOn && !s.deviceSecure) add(Issue(
            "autolock", "Auto-lock is not protecting the app",
            "This phone has no screen lock, so HOD Desk cannot lock itself. Set a PIN, pattern or fingerprint.",
            Severity.ATTENTION, Fix.SECURITY_SETTINGS, "Set up",
        ))
        if (s.reasoningModelNeedsDownload) add(Issue(
            "reasoning_model", "${com.hoddesk.app.reasoning.ReasoningModelInfo.NAME} needs a new download",
            "The installed model was replaced by an update or failed its safety check. Until you download it again, letters are read with the basic on-phone rules.",
            Severity.ATTENTION, Fix.AI_SETTINGS, "Open",
        ))
    }

    // --- Health page: every part of the app with its current state, not only the broken ones. ---

    enum class Level { OK, ATTENTION, BLOCKED, OFF }

    data class Row(val group: String, val label: String, val value: String, val level: Level, val detail: String? = null)

    data class Details(
        val databaseBytes: Long,
        val sourceCount: Int,
        val taskCount: Int,
        val lastBackupAt: Long?,
        val modelInstalled: Boolean,
        val modelBytes: Long,
        val memory: com.hoddesk.app.reasoning.MemorySnapshot?,
        val searchReady: Boolean,
        val searchLine: String,
        val now: Long,
    )

    /** A backup older than this is flagged; the data on a lost phone cannot be recovered without one. */
    const val BACKUP_STALE_DAYS = 14

    fun report(s: Snapshot, d: Details): List<Row> = buildList {
        val day = 24L * 60 * 60 * 1000
        // Data
        add(when (s.databaseOk) {
            null -> Row("Data", "Encrypted storage", "Checking…", Level.OFF)
            true -> Row("Data", "Encrypted storage", "Healthy", Level.OK, "${d.sourceCount} items · ${d.taskCount} tasks · ${size(d.databaseBytes)}")
            false -> Row("Data", "Encrypted storage", "Cannot be read", Level.BLOCKED, "Restart the phone; if this stays, restore from your latest backup.")
        })
        add(if (s.freeBytes in 0 until LOW_STORAGE_BYTES) Row("Data", "Phone storage", "${size(s.freeBytes)} free", Level.ATTENTION, "Imports, backups and model downloads may fail.")
            else Row("Data", "Phone storage", if (s.freeBytes < 0) "Unknown" else "${size(s.freeBytes)} free", Level.OK))
        val backupDays = d.lastBackupAt?.let { ((d.now - it) / day).toInt() }
        add(when {
            backupDays == null -> Row("Data", "Last backup", "Never", if (d.sourceCount + d.taskCount > 0) Level.ATTENTION else Level.OFF, "Create an encrypted backup so a lost phone does not lose your desk.")
            backupDays >= BACKUP_STALE_DAYS -> Row("Data", "Last backup", "$backupDays days ago", Level.ATTENTION, "Older than $BACKUP_STALE_DAYS days.")
            else -> Row("Data", "Last backup", when (backupDays) { 0 -> "Today"; 1 -> "Yesterday"; else -> "$backupDays days ago" }, Level.OK)
        })
        // On-device AI
        add(when {
            s.reasoningModelNeedsDownload -> Row("On-Device AI", com.hoddesk.app.reasoning.ReasoningModelInfo.NAME_WITH_MODEL, "Needs a new download", Level.ATTENTION, "Letters are read with the basic on-phone rules until then.")
            d.modelInstalled -> Row("On-Device AI", com.hoddesk.app.reasoning.ReasoningModelInfo.NAME_WITH_MODEL, "Installed", Level.OK, size(d.modelBytes))
            else -> Row("On-Device AI", com.hoddesk.app.reasoning.ReasoningModelInfo.NAME_WITH_MODEL, "Not installed", Level.OFF, "Optional. The basic on-phone rules are used.")
        })
        d.memory?.let { m ->
            val needed = com.hoddesk.app.reasoning.ReasoningMemoryPolicy.requiredBytes(if (d.modelInstalled) d.modelBytes else 0L)
            val free = com.hoddesk.app.reasoning.ReasoningMemoryPolicy.gb(m.availableBytes)
            add(when {
                !d.modelInstalled -> Row("On-Device AI", "Free memory", free, Level.OFF)
                m.lowMemory || m.availableBytes < needed -> Row("On-Device AI", "Free memory", free, Level.ATTENTION, "Gemma needs about ${com.hoddesk.app.reasoning.ReasoningMemoryPolicy.gb(needed)}. Close other apps before using it.")
                else -> Row("On-Device AI", "Free memory", free, Level.OK, "Enough to run Gemma.")
            })
        }
        add(Row("On-Device AI", "Search", d.searchLine, if (d.searchReady) Level.OK else Level.ATTENTION))
        // Reminders & calendar
        add(when {
            !s.remindersOn -> Row("Alerts", "Reminders", "Off", Level.OFF)
            !s.notificationPermission -> Row("Alerts", "Reminders", "Blocked", Level.ATTENTION, "Notifications are turned off for HOD Desk.")
            else -> Row("Alerts", "Reminders", "On", Level.OK)
        })
        add(when {
            !s.calendarChosen -> Row("Alerts", "Calendar", "Not chosen", Level.OFF, "Approved meetings stay inside HOD Desk.")
            !s.calendarPermission -> Row("Alerts", "Calendar", "Access off", Level.ATTENTION)
            else -> Row("Alerts", "Calendar", "Connected", Level.OK)
        })
        // Security
        add(when {
            !s.autoLockOn -> Row("Security", "Auto-lock", "Off", Level.OFF)
            !s.deviceSecure -> Row("Security", "Auto-lock", "No screen lock", Level.ATTENTION, "Set a PIN, pattern or fingerprint on the phone.")
            else -> Row("Security", "Auto-lock", "On", Level.OK)
        })
        add(Row("Security", "Your data", "Stays on this phone", Level.OK, "Only AI models are downloaded; nothing you import is uploaded."))
    }

    /** Overall state: the worst level among the rows (OFF counts as fine). */
    fun overall(rows: List<Row>): Level = when {
        rows.any { it.level == Level.BLOCKED } -> Level.BLOCKED
        rows.any { it.level == Level.ATTENTION } -> Level.ATTENTION
        else -> Level.OK
    }

    fun size(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            bytes < 0 -> "Unknown"
            mb >= 1024 -> String.format(java.util.Locale.ENGLISH, "%.1f GB", mb / 1024)
            mb >= 1 -> String.format(java.util.Locale.ENGLISH, "%.0f MB", mb)
            else -> String.format(java.util.Locale.ENGLISH, "%.0f KB", bytes / 1024.0)
        }
    }
}
