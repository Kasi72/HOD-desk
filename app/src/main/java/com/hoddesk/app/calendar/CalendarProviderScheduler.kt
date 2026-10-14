package com.hoddesk.app.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContentUris
import android.provider.CalendarContract
import com.hoddesk.app.repository.ScheduleProposal

sealed interface CalendarWriteResult {
    data class NeedsConfirmation(val proposal: ScheduleProposal) : CalendarWriteResult
    data class Written(val calendarEventId: Long) : CalendarWriteResult
    data class Denied(val reason: String, val conflicts: List<String> = emptyList()) : CalendarWriteResult
}

class CalendarProviderScheduler(private val resolver: ContentResolver, private val permissionChecker: (String) -> Boolean) {
    fun writeConfirmed(proposal: ScheduleProposal, calendarId: Long, confirmed: Boolean, localEventId: Long? = null): CalendarWriteResult {
        if (!confirmed) return CalendarWriteResult.NeedsConfirmation(proposal)
        if (localEventId == null || localEventId <= 0) return CalendarWriteResult.Denied("Save a durable local calendar proposal before writing")
        if (proposal.event.endsAt <= proposal.event.startsAt) return CalendarWriteResult.Denied("Event must end after it starts")
        if (!permissionChecker(Manifest.permission.WRITE_CALENDAR)) return CalendarWriteResult.Denied("Calendar write permission is not granted")
        if (!permissionChecker(Manifest.permission.READ_CALENDAR)) return CalendarWriteResult.Denied("Calendar read permission is needed to verify conflicts and duplicate writes")
        val existing = try { findMatchingEventId(proposal.event.title, proposal.event.startsAt, proposal.event.endsAt, localEventId) }
        catch (_: RuntimeException) { return CalendarWriteResult.Denied("Calendar could not be checked; no event was written") }
        if (existing != null) return CalendarWriteResult.Written(existing)
        val localConflicts = proposal.conflicts.map { event -> event.title }
        val providerConflicts = try { findConflicts(proposal.event.startsAt, proposal.event.endsAt) }
        catch (_: RuntimeException) { return CalendarWriteResult.Denied("Calendar conflicts could not be checked; no event was written") }
        val conflictTitles = (localConflicts + providerConflicts).distinct()
        if (conflictTitles.isNotEmpty()) return CalendarWriteResult.Denied("Conflicting events require an explicit reschedule decision", conflictTitles)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, proposal.event.title)
            put(CalendarContract.Events.DTSTART, proposal.event.startsAt)
            put(CalendarContract.Events.DTEND, proposal.event.endsAt)
            put(CalendarContract.Events.EVENT_LOCATION, proposal.event.location)
            put(CalendarContract.Events.DESCRIPTION, "Created by Psychiatry HOD Desk\n${CalendarWriteIdentity.marker(localEventId)}")
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        val uri = try {
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (security: SecurityException) {
            return CalendarWriteResult.Denied("Calendar permission was revoked")
        } catch (provider: RuntimeException) {
            return CalendarWriteResult.Denied("Calendar provider rejected the event: ${provider.message ?: "unknown error"}")
        } ?: return CalendarWriteResult.Denied("Calendar provider rejected the event")
        val id = uri.lastPathSegment?.toLongOrNull()
            ?: return CalendarWriteResult.Denied("Calendar provider returned an invalid event id")
        return CalendarWriteResult.Written(id)
    }

    fun findConflicts(startsAt: Long, endsAt: Long, excludeEventId: Long? = null): List<String> {
        check(permissionChecker(Manifest.permission.READ_CALENDAR)) { "Calendar read permission is not granted" }
        val rangeUri = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(startsAt.toString()).appendPath(endsAt.toString()).build()
        val titles = mutableListOf<String>()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Events.AVAILABILITY)
        val cursor = checkNotNull(resolver.query(rangeUri, projection, null, null, null)) { "Calendar provider returned no conflict query" }
        cursor.use {
            val titleColumn = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val id = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val begin = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val end = it.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val availability = it.getColumnIndex(CalendarContract.Events.AVAILABILITY)
            while (it.moveToNext()) {
                if (excludeEventId != null && it.getLong(id) == excludeEventId) continue
                if (availability >= 0 && it.getInt(availability) == CalendarContract.Events.AVAILABILITY_FREE) continue
                if (it.getLong(begin) < endsAt && it.getLong(end) > startsAt) titles += it.getString(titleColumn).orEmpty().ifBlank { "Busy calendar event" }
            }
        }
        return titles
    }

    /** Finds a provider event left behind if the app died after insert. */
    fun findMatchingEventId(title: String, startsAt: Long, endsAt: Long, localEventId: Long? = null): Long? {
        // Same-title events in somebody else's calendar are not evidence of our write.
        if (localEventId == null || !permissionChecker(Manifest.permission.READ_CALENDAR)) return null
        val rangeUri = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(startsAt.toString()).appendPath(endsAt.toString()).build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Events.DESCRIPTION)
        val cursor = checkNotNull(resolver.query(rangeUri, projection, null, null, null)) { "Calendar provider returned no reconciliation query" }
        cursor.use {
            val id = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleColumn = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val begin = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val end = it.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val description = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            while (cursor.moveToNext()) {
                if (it.getString(titleColumn) == title && it.getLong(begin) == startsAt && it.getLong(end) == endsAt && CalendarWriteIdentity.matches(it.getString(description), localEventId)) return it.getLong(id)
            }
        }
        return null
    }

    /** Explicit provider reschedule; a task due edit alone cannot invoke this. */
    fun updateConfirmed(providerEventId: Long, proposal: ScheduleProposal, confirmed: Boolean): CalendarWriteResult {
        if (!confirmed) return CalendarWriteResult.NeedsConfirmation(proposal)
        if (providerEventId <= 0 || proposal.event.endsAt <= proposal.event.startsAt) return CalendarWriteResult.Denied("Invalid calendar change")
        if (!permissionChecker(Manifest.permission.WRITE_CALENDAR) || !permissionChecker(Manifest.permission.READ_CALENDAR)) return CalendarWriteResult.Denied("Calendar read and write permissions are required")
        return try {
            val conflicts = (proposal.conflicts.map { it.title } + findConflicts(proposal.event.startsAt, proposal.event.endsAt, providerEventId)).distinct()
            if (conflicts.isNotEmpty()) return CalendarWriteResult.Denied("Calendar change conflicts with protected time", conflicts)
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, proposal.event.title)
                put(CalendarContract.Events.DTSTART, proposal.event.startsAt)
                put(CalendarContract.Events.DTEND, proposal.event.endsAt)
                put(CalendarContract.Events.EVENT_LOCATION, proposal.event.location)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            val count = resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerEventId), values, null, null)
            if (count == 1) CalendarWriteResult.Written(providerEventId) else CalendarWriteResult.Denied("Calendar event no longer exists")
        } catch (_: RuntimeException) { CalendarWriteResult.Denied("Calendar change could not be completed") }
    }

    fun deleteConfirmed(providerEventId: Long, confirmed: Boolean): Boolean {
        if (!confirmed || providerEventId <= 0 || !permissionChecker(Manifest.permission.WRITE_CALENDAR)) return false
        return try { resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerEventId), null, null) == 1 }
        catch (_: RuntimeException) { false }
    }

    companion object {
        fun fromResolver(resolver: ContentResolver, checker: (String) -> Boolean): CalendarProviderScheduler = CalendarProviderScheduler(resolver, checker)
    }
}
