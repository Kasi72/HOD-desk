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
            "reasoning_model", "AI reading model needs a new download",
            "The installed model was replaced by an update or failed its safety check. Until you download it again, letters are read with the basic on-phone rules.",
            Severity.ATTENTION, Fix.AI_SETTINGS, "Open",
        ))
    }
}
