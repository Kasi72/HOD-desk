package com.hoddesk.app.reasoning

import com.hoddesk.app.commitments.CommitmentLedger
import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.CommitmentEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.imports.ActionDueParser
import com.hoddesk.app.triage.PriorityReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseATest {
    private val source = SourceEntity(id = 1, title = "DME circular", body = "Submit the audit report by 20 Nov 2026.", sourceType = "Document")
    private val point = ActionPointEntity(id = 5, sourceId = 1, action = "Submit the audit report", dateText = "20 Nov 2026", evidence = "Submit the audit report by 20 Nov 2026.", confidence = 0.9f)

    @Test fun previewDescribesEveryEffectOfApproval() {
        val preview = ApprovalPreviewBuilder.build(point, point, source, assignee = null, dueAt = 42L, dueLabel = { "Fri 20 Nov" })
        val effects = preview.effects.associate { it.label to it.value }
        assertEquals("Submit the audit report", effects["New task"])
        assertEquals("You", effects["Owner"])
        assertEquals("Fri 20 Nov", effects["Due"])
        assertEquals("Adds a 1-hour event", effects["Calendar"])
        assertEquals("Something you owe", effects["Tracked as"])
        assertTrue(preview.edits.isEmpty())
        assertFalse(preview.needsConfirmation)
        assertEquals("Submit the audit report by 20 Nov 2026.", preview.evidence)
    }

    @Test fun previewListsReviewerEditsAndDelegation() {
        val edited = point.copy(dateText = "22 Nov 2026", locationText = "Online")
        val preview = ApprovalPreviewBuilder.build(point, edited, source, assignee = "Dr Priya", dueAt = null, dueLabel = { "" })
        assertEquals(listOf("date", "location"), preview.edits.map { it.field })
        assertEquals("20 Nov 2026", preview.edits.first().previous)
        val effects = preview.effects.associate { it.label to it.value }
        assertEquals("Dr Priya", effects["Owner"])
        assertEquals("Waiting on Dr Priya", effects["Tracked as"])
        assertEquals("No change", effects["Calendar"])
        assertTrue(preview.warnings.any { it.contains("could not be confirmed") })
    }

    @Test fun uncertainFieldsAndNewerSourcesRequireConfirmation() {
        val preview = ApprovalPreviewBuilder.build(point, point, source, null, 1L, { "" }, uncertainFields = listOf("Time"), newerSources = listOf("Revised circular"))
        assertTrue(preview.needsConfirmation)
        assertEquals(listOf("Not confirmed: time", "A newer source may change this: Revised circular"), preview.warnings)
    }

    @Test fun reasonsArePlainAndDeadlineComesFirst() {
        val now = 1_800_000_000_000L
        val stored = "high-authority sender or office · date or time cue · action language"
        assertEquals(listOf("Senior office", "Mentions a date", "Asks for action"), PriorityReasons.humanize(stored))
        assertEquals(listOf("Due within a day", "Conflicting details", "Senior office"),
            PriorityReasons.forItem(stored, dueAt = now + 3_600_000L, hasConflict = true, now = now))
        assertEquals("Overdue", PriorityReasons.forItem(stored, dueAt = now - 1, now = now).first())
        assertEquals(listOf("Due in 2 days"), PriorityReasons.forItem(null, dueAt = now + 30 * 3_600_000L, now = now))
        assertTrue(PriorityReasons.forItem("", now = now).isEmpty())
    }

    @Test fun dateChipsProduceParseableDates() {
        val monday = LocalDate.of(2026, 11, 16)
        val chips = CorrectionChips.dates(monday)
        assertEquals(listOf("16 Nov 2026", "17 Nov 2026", "23 Nov 2026", "23 Nov 2026"), chips.map { it.value })
        chips.forEach { assertNotNull(it.label, ActionDueParser.parse(it.value, null)) }
        CorrectionChips.times.forEach { assertNotNull(it, ActionDueParser.parse("17 Nov 2026", it)) }
    }

    @Test fun ledgerSplitsWhatIOweFromWhatOthersOwe() {
        val items = listOf(
            CommitmentEntity(id = 1, direction = "OWED_BY_ME", summary = "Send roster", dueAt = 20, evidence = "e"),
            CommitmentEntity(id = 2, direction = "OWED_TO_ME", owner = "Dr Priya", summary = "Audit data", dueAt = 5, evidence = "e"),
            CommitmentEntity(id = 3, direction = "OWED_BY_ME", summary = "Reply to DME", dueAt = 10, evidence = "e"),
            CommitmentEntity(id = 4, direction = "OWED_BY_ME", summary = "Done", status = "completed", evidence = "e"),
        )
        assertEquals(listOf(3L, 1L), CommitmentLedger.pendingForMe(items).map { it.id })
        assertEquals(listOf(2L), CommitmentLedger.waitingOnOthers(items).map { it.id })
        assertEquals(listOf(2L, 3L), CommitmentLedger.overdue(items, now = 15).map { it.id })
    }
}
