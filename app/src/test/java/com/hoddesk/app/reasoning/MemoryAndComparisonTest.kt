package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.imports.ActionPointExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAndComparisonTest {
    private val gb = 1024L * 1024L * 1024L
    private val model = (2.6 * gb).toLong()

    @Test fun loadsOnlyWithEnoughFreeMemory() {
        val required = ReasoningMemoryPolicy.requiredBytes(model)
        assertEquals(model / 2 + 768L * 1024 * 1024, required)
        assertTrue(ReasoningMemoryPolicy.decide(MemorySnapshot(4 * gb, 8 * gb, false), model, alreadyLoaded = false).allowed)
        val short = ReasoningMemoryPolicy.decide(MemorySnapshot(1 * gb, 8 * gb, false), model, alreadyLoaded = false)
        assertFalse(short.allowed)
        assertTrue(short.reason!!, short.reason!!.startsWith("Only 1.0 GB free"))
    }

    @Test fun loadedEngineIsReusedButLowMemoryAlwaysRefuses() {
        assertTrue(ReasoningMemoryPolicy.decide(MemorySnapshot(1 * gb, 8 * gb, false), model, alreadyLoaded = true).allowed)
        assertFalse(ReasoningMemoryPolicy.decide(MemorySnapshot(6 * gb, 8 * gb, true), model, alreadyLoaded = true).allowed)
    }

    @Test fun smallModelsStillNeedTheMinimumWorkingSet() {
        assertEquals(gb + 768L * 1024 * 1024, ReasoningMemoryPolicy.requiredBytes(100L * 1024 * 1024))
    }

    private val original = "Kindly attend the NHM review meeting on 18.11.2026 at 10:00 AM in Collectorate Hall."
    private val current = ActionPointExtractor().extract(original).first().toEntity(1)
    private fun newer(body: String, title: String = "Revised notice") = SourceEntity(id = 2, title = title, body = body, sourceType = "Document", capturedAt = 2_000)

    @Test fun showsChangedDateTimeAndVenueSideBySide() {
        val c = SourceComparison.compare(current, newer("Revised: Kindly attend the NHM review meeting on 20.11.2026 at 3:00 PM in Dean Office Hall."))
        val rows = c.rows.associateBy { it.field }
        assertEquals("18.11.2026", rows.getValue("date").current)
        assertEquals("20.11.2026", rows.getValue("date").newer)
        assertTrue(rows.getValue("date").differs)
        assertTrue(rows.getValue("time").differs)
        assertTrue(rows.getValue("location").differs)
        assertFalse(c.cancels)
        assertEquals(mapOf("date" to "20.11.2026", "time" to "3:00 PM", "location" to "Dean Office Hall"),
            SourceComparison.applyNewer(c).filterKeys { it in setOf("date", "time", "location") })
    }

    @Test fun sameDetailsMeanNoChange() {
        val c = SourceComparison.compare(current, newer("Reminder: Kindly attend the NHM review meeting on 18.11.2026 at 10:00 AM in Collectorate Hall."))
        assertTrue(c.changedRows.isEmpty())
        assertFalse(c.hasChanges)
    }

    @Test fun detectsCancellationEvenWithoutAMatchingAction() {
        val c = SourceComparison.compare(current, newer("The NHM review meeting scheduled on 18.11.2026 stands cancelled.", "Meeting cancelled"))
        assertTrue(c.cancels)
        assertTrue(c.hasChanges)
    }

    @Test fun unrelatedActionDoesNotLendItsFields() {
        val c = SourceComparison.compare(current, newer("Kindly submit the pharmacy stock statement on 25.11.2026 at 4:00 PM. Kindly verify the drug register on 26.11.2026."))
        assertEquals(null, c.matchedAction)
        assertTrue(c.rows.none { it.field == "location" && it.differs })
        assertFalse(c.rows.first { it.field == "date" }.differs) // two dates: no single document-level value
    }

    @Test fun venueAfterTheIsExtracted() {
        val point = ActionPointExtractor().extract("Kindly attend the NHM review meeting on 18.11.2026 at 10:00 AM in the Collectorate Hall.").first()
        assertEquals("Collectorate Hall", point.locationText)
        assertEquals("DME Office", ActionPointExtractor().extract("Kindly submit the files at the DME Office on 20.11.2026.").first().locationText)
    }
}
