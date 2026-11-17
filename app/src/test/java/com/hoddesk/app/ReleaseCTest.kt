package com.hoddesk.app

import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.EventEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.TaskEntity
import com.hoddesk.app.health.SystemHealth
import com.hoddesk.app.privacy.Redactor
import com.hoddesk.app.reasoning.MemorySnapshot
import com.hoddesk.app.workflow.WeeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCTest {
    // --- Redaction preview ---

    @Test fun masksPhonesEmailsAndAadhaarAndCountsThem() {
        val p = Redactor.preview("Call 9876543210 or +91 98765 43210, mail dme@tn.gov.in. Aadhaar 2345 6789 0123.")
        assertEquals("Call [PHONE] or [PHONE], mail [EMAIL]. Aadhaar [AADHAAR].", p.redacted)
        assertEquals(2, p.count(Redactor.Kind.PHONE))
        assertEquals("1 email address, 1 Aadhaar number and 2 phone numbers will be hidden", p.summary())
    }

    @Test fun leavesDatesTimesReferencesAndPatientIdsAlone() {
        val text = "Rc.No.1234/2026 dated 18.11.2026 at 10:30 AM. UHID 123456. Ref 20261118."
        val p = Redactor.preview(text)
        assertFalse(p.hasHits)
        assertEquals(text, p.redacted)
        assertNull(p.summary())
    }

    @Test fun oldTenDigitRuleStillApplies() {
        assertEquals("Phone [PHONE] now", Redactor.redact("Phone 9443012345 now"))
        assertEquals("ID A1234567890", Redactor.redact("ID A1234567890")) // part of a word, as before
    }

    // --- Weekly review ---

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test fun weeklyReviewSortsWorkIntoBuckets() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "Done", project = "p", status = "completed", completedAt = now - 2 * day),
            TaskEntity(id = 2, title = "Done long ago", project = "p", status = "completed", completedAt = now - 30 * day),
            TaskEntity(id = 3, title = "Slipped", project = "p", dueAt = now - day),
            TaskEntity(id = 4, title = "Due soon", project = "p", dueAt = now + 3 * day),
            TaskEntity(id = 5, title = "Later", project = "p", dueAt = now + 20 * day),
            TaskEntity(id = 6, title = "No date", project = "p"),
            TaskEntity(id = 7, title = "Deleted", project = "p", status = "deleted", dueAt = now - day),
        )
        val commitments = listOf(
            CommitmentEntity(id = 1, direction = "OWED_TO_ME", summary = "Report from RMO", evidence = "e", dueAt = now - day),
            CommitmentEntity(id = 2, direction = "OWED_BY_ME", summary = "Reply to DME", evidence = "e", dueAt = now + day),
            CommitmentEntity(id = 3, direction = "OWED_BY_ME", summary = "Closed", evidence = "e", status = "done"),
        )
        val sources = listOf(
            SourceEntity(id = 1, title = "Old", body = "x", sourceType = "Document", capturedAt = now - 5 * day),
            SourceEntity(id = 2, title = "Snoozed", body = "x", sourceType = "Document", snoozedUntil = now + day),
            SourceEntity(id = 3, title = "Reviewed", body = "x", sourceType = "Document", reviewed = true),
        )
        val events = listOf(EventEntity(title = "NHM review", startsAt = now + day, endsAt = now + day + 3_600_000))
        val r = WeeklyReview.build(tasks, commitments, sources, events, now)
        assertEquals(listOf(1L), r.done.map { it.id })
        assertEquals(listOf(3L), r.slipped.map { it.id })
        assertEquals(listOf(4L), r.dueThisWeek.map { it.id })
        assertEquals(listOf(6L), r.undated.map { it.id })
        assertEquals(1, r.waitingOn.size)
        assertEquals(1, r.iOwe.size)
        assertEquals(1, r.overdueCommitments)
        assertEquals(1, r.reviewBacklog)
        assertEquals(5, r.oldestReviewDays)
        assertEquals(1, r.meetingsAhead)
        assertEquals("1 slipped · 1 due this week · 1 waiting in Review", r.headline())
    }

    @Test fun emptyWeekAndNudgeTiming() {
        val r = WeeklyReview.build(emptyList(), emptyList(), emptyList(), emptyList(), now)
        assertTrue(r.isEmpty)
        assertEquals("Nothing is open. A quiet week.", r.headline())
        assertTrue(WeeklyReview.isDue(null, now))
        assertFalse(WeeklyReview.isDue(now - 2 * day, now))
        assertTrue(WeeklyReview.isDue(now - 7 * day, now))
    }

    // --- Health page ---

    private val gb = 1024L * 1024 * 1024
    private val healthy = SystemHealth.Snapshot(
        databaseOk = true, calendarChosen = true, calendarPermission = true, remindersOn = true, notificationPermission = true,
        autoLockOn = true, deviceSecure = true, freeBytes = 20 * gb, reasoningModelNeedsDownload = false,
    )
    private fun details(lastBackupAt: Long? = now - day, memory: MemorySnapshot? = MemorySnapshot(4 * gb, 8 * gb, false)) = SystemHealth.Details(
        databaseBytes = 3L * 1024 * 1024, sourceCount = 12, taskCount = 4, lastBackupAt = lastBackupAt,
        modelInstalled = true, modelBytes = (2.6 * gb).toLong(), memory = memory, searchReady = true, searchLine = "Ready", now = now,
    )

    @Test fun healthyPhoneIsAllGreen() {
        val rows = SystemHealth.report(healthy, details())
        assertEquals(SystemHealth.Level.OK, SystemHealth.overall(rows))
        assertEquals("Yesterday", rows.first { it.label == "Last backup" }.value)
        assertEquals("12 items · 4 tasks · 3 MB", rows.first { it.label == "Encrypted storage" }.detail)
    }

    @Test fun staleBackupAndShortMemoryNeedAttention() {
        val rows = SystemHealth.report(healthy, details(lastBackupAt = now - 20 * day, memory = MemorySnapshot(1 * gb, 8 * gb, false)))
        assertEquals(SystemHealth.Level.ATTENTION, rows.first { it.label == "Last backup" }.level)
        assertEquals(SystemHealth.Level.ATTENTION, rows.first { it.label == "Free memory" }.level)
        assertEquals(SystemHealth.Level.ATTENTION, SystemHealth.overall(rows))
    }

    @Test fun brokenDatabaseIsBlockedAndSwitchedOffPartsAreNotProblems() {
        val off = healthy.copy(databaseOk = false, remindersOn = false, calendarChosen = false, autoLockOn = false)
        val rows = SystemHealth.report(off, details())
        assertEquals(SystemHealth.Level.BLOCKED, SystemHealth.overall(rows))
        assertEquals(SystemHealth.Level.OFF, rows.first { it.label == "Reminders" }.level)
        val fine = SystemHealth.report(healthy.copy(remindersOn = false, autoLockOn = false), details())
        assertEquals(SystemHealth.Level.OK, SystemHealth.overall(fine))
    }
}
