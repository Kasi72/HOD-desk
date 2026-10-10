package com.hoddesk.app.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.provider.CalendarContract
import com.hoddesk.app.repository.ScheduleProposal

sealed interface CalendarWriteResult {
    data class NeedsConfirmation(val proposal: ScheduleProposal) : CalendarWriteResult
    data class Written(val calendarEventId: Long) : CalendarWriteResult
    data class Denied(val reason: String, val conflicts: List<String> = emptyList()) : CalendarWriteResult
}

class CalendarProviderScheduler(private val resolver: ContentResolver, private val permissionChecker: (String) -> Boolean) {
    fun writeConfirmed(proposal: ScheduleProposal, calendarId: Long, confirmed: Boolean): CalendarWriteResult {
        if (!confirmed) return CalendarWriteResult.NeedsConfirmation(proposal)
        if (!permissionChecker(Manifest.permission.WRITE_CALENDAR)) return CalendarWriteResult.Denied("Calendar write permission is not granted")
        val localConflicts = proposal.conflicts.map { event -> event.title }
        val providerConflicts = findConflicts(proposal.event.startsAt, proposal.event.endsAt)
        val conflictTitles = (localConflicts + providerConflicts).distinct()
        if (conflictTitles.isNotEmpty()) return CalendarWriteResult.Denied("Conflicting events require an explicit reschedule decision", conflictTitles)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, proposal.event.title)
            put(CalendarContract.Events.DTSTART, proposal.event.startsAt)
            put(CalendarContract.Events.DTEND, proposal.event.endsAt)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return CalendarWriteResult.Denied("Calendar provider rejected the event")
        return CalendarWriteResult.Written(uri.lastPathSegment?.toLongOrNull() ?: -1L)
    }

    fun findConflicts(startsAt: Long, endsAt: Long): List<String> {
        if (!permissionChecker(Manifest.permission.READ_CALENDAR)) return emptyList()
        val rangeUri = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(startsAt.toString()).appendPath(endsAt.toString()).build()
        val titles = mutableListOf<String>()
        resolver.query(rangeUri, arrayOf(CalendarContract.Instances.TITLE), null, null, null)?.use { cursor ->
            val titleColumn = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            while (cursor.moveToNext() && titleColumn >= 0) titles += cursor.getString(titleColumn).orEmpty()
        }
        return titles
    }

    companion object {
        fun fromResolver(resolver: ContentResolver, checker: (String) -> Boolean): CalendarProviderScheduler = CalendarProviderScheduler(resolver, checker)
    }
}
