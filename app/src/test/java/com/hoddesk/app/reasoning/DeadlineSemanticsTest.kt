package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.imports.ActionPointExtractor
import com.hoddesk.app.reasoning.DeadlineSemantics.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DeadlineSemanticsTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 11, 16, 9, 0).atZone(ist).toInstant().toEpochMilli()
    private fun kind(text: String, date: String?) = DeadlineSemantics.read(text, date).kind
    private fun point(evidence: String, date: String?, time: String? = null, ambiguity: String? = null) =
        ActionPointEntity(sourceId = 1, action = "Submit report", dateText = date, timeText = time, evidence = evidence, confidence = .9f, ambiguity = ambiguity)
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, mi: Int = 0, zone: ZoneId = ist) = LocalDateTime.of(y, m, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun readsTheWordBeforeTheDate() {
        assertEquals(Kind.BY, kind("Submit the report by 20.11.2026.", "20.11.2026"))
        assertEquals(Kind.BY, kind("Send it on or before 20 Nov 2026", "20 Nov 2026"))
        assertEquals(Kind.BY, kind("Reply not later than 20 Nov 2026", "20 Nov 2026"))
        assertEquals(Kind.BY, kind("Submit by Friday, 20 Nov 2026", "20 Nov 2026"))
        assertEquals(Kind.BEFORE, kind("Submit before 15 Nov 2026", "15 Nov 2026"))
        assertEquals(Kind.BEFORE, kind("Forward prior to the 15.11.2026 meeting", "15.11.2026"))
        assertEquals(Kind.AFTER, kind("Collect the files after 15 Nov 2026", "15 Nov 2026"))
        assertEquals(Kind.FROM, kind("Revised OPD timing w.e.f. 01.12.2026", "01.12.2026"))
        assertEquals(Kind.ON, kind("Attend the review on 18 Nov 2026", "18 Nov 2026"))
        assertEquals(Kind.NONE, kind("Meeting 18 Nov 2026", "18 Nov 2026"))
        assertEquals(Kind.NONE, kind("Submit by 20 Nov 2026", null))
    }

    @Test fun readsTamilPostpositions() {
        assertEquals(Kind.BY, kind("அறிக்கையை 15.11.2026க்குள் அனுப்ப வேண்டும்", "15.11.2026"))
        assertEquals(Kind.BEFORE, kind("15.11.2026க்கு முன் சமர்ப்பிக்க", "15.11.2026"))
        assertEquals(Kind.AFTER, kind("15.11.2026க்குப் பிறகு வரவும்", "15.11.2026"))
        assertEquals(listOf("15.11.2026"), TemporalEvidence.dates("அறிக்கையை 15.11.2026க்குள் அனுப்ப").map { it.text })
    }

    @Test fun readsRelativeDeadlines() {
        val week = DeadlineSemantics.read("Send the reply within 7 days of receipt.", null)
        assertEquals(Kind.WITHIN, week.kind); assertEquals(7, week.amount)
        assertEquals(LocalDate.of(2026, 11, 23), DeadlineSemantics.relativeDue(LocalDate.of(2026, 11, 16), week))
        val working = DeadlineSemantics.read("Comply within five working days", null)
        assertEquals(LocalDate.of(2026, 11, 27), DeadlineSemantics.relativeDue(LocalDate.of(2026, 11, 20), working))
        val weeks = DeadlineSemantics.read("Submit within two weeks", "20 Nov 2026")
        assertEquals(LocalDate.of(2026, 11, 30), DeadlineSemantics.relativeDue(LocalDate.of(2026, 11, 16), weeks))
        assertEquals(10, DeadlineSemantics.read("10 நாட்களுக்குள் அனுப்பவும்", null).amount)
        assertTrue(DeadlineSemantics.reviewNote(week)!!.contains("requires confirmation"))
    }

    @Test fun beforeMeansTheDayBeforeUnlessATimeIsStated() {
        assertEquals(at(2026, 11, 14), ReviewApprovalPolicy.dueAt(point("Submit before 15 Nov 2026", "15 Nov 2026"), ist, now))
        assertEquals(at(2026, 11, 15, 10), ReviewApprovalPolicy.dueAt(point("Submit before 15 Nov 2026 10:00 AM", "15 Nov 2026", "10:00 AM"), ist, now))
        assertEquals(at(2026, 11, 20), ReviewApprovalPolicy.dueAt(point("Submit by 20 Nov 2026", "20 Nov 2026"), ist, now))
    }

    @Test fun statedZoneWinsOverPhoneZone() {
        val london = ZoneId.of("Europe/London")
        val p = point("Webinar on 18 Nov 2026 at 10:00 AM IST", "18 Nov 2026", "10:00 AM")
        assertEquals(at(2026, 11, 18, 10), ReviewApprovalPolicy.dueAt(p, london, now))
        assertEquals(at(2026, 11, 18, 10, zone = london), ReviewApprovalPolicy.dueAt(p.copy(evidence = "Webinar on 18 Nov 2026 at 10:00 AM"), london, now))
    }

    @Test fun extractionHoldsStartDatesForConfirmation() {
        val after = ActionPointExtractor().extract("Kindly submit the audit report after 15.11.2026.").single().toEntity(1)
        assertTrue(after.ambiguity.orEmpty(), after.ambiguity.orEmpty().contains("start date"))
        assertNull(ReviewApprovalPolicy.dueAt(after, ist, now))
        val before = ActionPointExtractor().extract("Kindly submit the audit report before 15.11.2026.").single().toEntity(1)
        assertNull(before.ambiguity?.takeIf { it.contains("start date") })
        assertEquals(at(2026, 11, 14), ReviewApprovalPolicy.dueAt(before, ist, now))
    }

    @Test fun previewExplainsTheReading() {
        val source = SourceEntity(id = 1, title = "Circular", body = "", sourceType = "Document", capturedAt = now)
        val p = point("Submit before 15 Nov 2026", "15 Nov 2026")
        val preview = ApprovalPreviewBuilder.build(p, p, source, null, ReviewApprovalPolicy.dueAt(p, ist, now), { "Sat 14 Nov" })
        assertEquals("“before 15 Nov 2026” · due the day before", preview.effects.first { it.label == "Reads as" }.value)
        val within = point("Reply within 7 days of receipt", null)
        val w = ApprovalPreviewBuilder.build(within, within, source, null, null, { "" })
        assertTrue(w.warnings.joinToString(), w.warnings.any { it.contains("Mon 23 Nov") })
    }
}
