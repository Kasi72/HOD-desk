package com.hoddesk.app.imports

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionPointExtractorTest {
    private val notice = """
        10/12/26, 6:36 PM
        Tamil Nadu Government Email Services :: e Nalam - Demo Meeting on Drug Condemnation and Drug Return – 13.10.2026 at 03:00 PM- reg
        e Nalam - Demo Meeting on Drug Condemnation and Drug Return – 13.10.2026 at 03:00 PM- reg
        From HMIS NHM <hmis.nhm@gmail.com>
        To Dr. Unrelated Recipient <recipient@example.org>
        Cc Dr. Copied Recipient <copied@example.org>
        Date 2026-10-12 15:22
        Respected Sir / Madam,
        It is hereby informed that a demo meeting has been scheduled on 13.10.2026 at 03:00 PM to discuss the Drug Condemnation and Drug Return processes in HMIS 3.0.
        Hence, you are requested to kindly instruct the following officials to attend the demo meeting without fail:
        1. HMIS Nodal Officer from the Directorate
        2. HMIS Nodal Officer from the pilot location
        3. IT Coordinators from the Directorate
        e Nalam - Demo- reg
        Tuesday, October 13 · 3:30 – 4:30pm
        Time zone: Asia/Kolkata
        Google Meet joining info
        Video call link: https://meet.google.com/yvn-zjmb-cpo
        Thanking you,
        HMIS Help Desk,
        National Health Mission,
        Chennai - 600 006.
    """.trimIndent()

    @Test fun government_notice_extracts_only_the_recipients_actual_obligation() {
        val drafts = ActionPointExtractor().extract(notice)
        assertEquals(1, drafts.size)
        val instruction = drafts.single()
        assertTrue(instruction.action.startsWith("Instruct"))
        assertTrue(instruction.action.contains("Drug Condemnation and Drug Return"))
        assertEquals("13.10.2026", instruction.dateText)
        assertEquals("Google Meet", instruction.locationText)
        assertEquals(3, instruction.peopleText.orEmpty().split(";").size)
        assertTrue(instruction.peopleText.orEmpty().contains("IT Coordinators from the Directorate"))
        assertTrue(!instruction.peopleText.orEmpty().contains("Recipient"))
        assertNull(instruction.referenceNumber)
    }

    @Test fun conflicting_body_and_invite_times_remain_in_the_cited_evidence() {
        val instruction = ActionPointExtractor().extract(notice).single()
        assertTrue(instruction.ambiguity.orEmpty().contains("conflict", true))
        assertTrue(instruction.timeText.orEmpty().contains("03:00 PM"))
        assertTrue(instruction.timeText.orEmpty().contains("3:30"))
        assertTrue(instruction.evidence.contains("13.10.2026"))
        assertTrue(instruction.evidence.contains("3:30 – 4:30pm"))
        assertTrue(instruction.evidence.contains("Google Meet"))
    }

    @Test fun an_informational_schedule_does_not_invent_hod_attendance() {
        assertTrue(ActionPointExtractor().extract("A demo meeting has been scheduled on 13.10.2026 at 03:00 PM to discuss Drug Return.").isEmpty())
    }

    @Test fun repeated_equal_dates_and_equivalent_times_are_not_conflicts() {
        val drafts = ActionPointExtractor().extract("Attend the DME meeting on 13.10.2026 at 03:00 PM (13.10.2026, 3 PM).")
        assertEquals(1, drafts.size)
        assertEquals("13.10.2026", drafts.single().dateText)
        assertTrue(!drafts.single().ambiguity.orEmpty().contains("Multiple date/time"))
        assertTrue(!drafts.single().ambiguity.orEmpty().contains("conflict", true))
    }

    @Test fun first_meeting_context_cannot_leak_into_an_unrelated_later_obligation() {
        val drafts = ActionPointExtractor().extract("""
            A DME meeting has been scheduled on 13.10.2026 at 3 PM.
            Please send the purchase report by Friday.
            Please join another meeting with the supplier.
        """.trimIndent())
        val supplier = drafts.first { it.action.contains("supplier") }
        assertNull(supplier.dateText)
        assertNull(supplier.timeText)
        assertNull(supplier.referenceNumber)
    }

    @Test fun numbered_non_people_are_never_added_to_the_people_field() {
        val draft = ActionPointExtractor().extract("Please submit the following items:\n1. Purchase report\n2. Drug return summary").single()
        assertNull(draft.peopleText)
    }

    @Test fun nearby_dates_are_not_treated_as_document_reference_numbers() {
        val draft = ActionPointExtractor().extract("Please submit the report on 13.10.2026 at 3 PM.").single()
        assertNull(draft.referenceNumber)
    }
    @Test
    fun extractsMeetingDateTimeAndLocationCues() {
        val body = "Attend online Zoom meeting with DME at 2 PM at Erode Collectorate on 19th September 2026."
        val result = ActionPointExtractor().annotate(body)
        assertTrue(result.contains("Action candidate: Attend online Zoom meeting"))
        assertTrue(result.contains("19th September 2026"))
        assertTrue(result.contains("2 PM"))
        assertTrue(result.contains("Erode Collectorate"))
    }

    @Test
    fun extractsContextualObligationAndScheduleWithoutActionVerb() {
        val body = "All HODs are requested to attend the online DME review. Zoom meeting at 2 PM on 19th September 2026. Resident duty roster due by Friday."
        val result = ActionPointExtractor().annotate(body)
        assertTrue(result.contains("Action candidate:"))
        assertTrue(result.contains("Zoom meeting at 2 PM"))
        assertTrue(result.contains("duty roster due by Friday"))
    }

    @Test fun fields_are_sentence_local_and_extended_fields_are_captured() {
        val drafts = ActionPointExtractor().extract("Attend the DME review at 2 PM in Erode Collectorate. Submit GO No. 214/2026 weekly; owner: Dr Raman.")
        assertEquals(2, drafts.size)
        assertTrue(drafts[0].timeText == "2 PM")
        assertTrue(drafts[0].locationText?.contains("Erode Collectorate") == true)
        assertTrue(drafts.any { it.referenceNumber?.contains("214/2026") == true })
        assertTrue(drafts.any { it.recurrenceRule?.contains("weekly", ignoreCase = true) == true })
    }

    @Test fun processing_error_text_never_becomes_an_action() {
        assertTrue(ActionPointExtractor().extract("OCR could not read this image; manual review required.").isEmpty())
        assertTrue(ActionPointExtractor().extract("Audio is queued for the approved offline transcription runtime.").isEmpty())
    }

    @Test fun independent_actions_split_on_explicit_clause_boundary() {
        val drafts = ActionPointExtractor().extract("Attend the review and then submit the report by Friday.")
        assertEquals(2, drafts.size)
    }

    @Test fun government_notice_keeps_dotted_date_time_and_numbered_officials() {
        val body = "A demo meeting has been scheduled on 13.10.2026 at 03:00 PM to discuss Drug Return. " +
            "Hence, kindly instruct the following officials to attend:\n" +
            "1. HMIS Nodal Officer from the Directorate\n" +
            "2. HMIS Nodal Officer from the pilot location\n" +
            "3. IT Coordinators from the Directorate\n" +
            "Google Meet joining info."
        val drafts = ActionPointExtractor().extract(body)
        assertTrue(drafts.isNotEmpty())
        assertTrue(drafts.any { it.dateText?.contains("13.10.2026") == true })
        assertTrue(drafts.any { it.timeText?.contains("03:00 PM") == true })
        assertTrue(drafts.any { it.peopleText?.contains("HMIS Nodal Officer") == true })
        assertTrue(drafts.any { it.locationText?.contains("Google Meet") == true })
        val instruction = drafts.first { it.action.startsWith("Instruct the listed officials") }
        assertTrue(instruction.dateText?.contains("13.10.2026") == true)
        assertTrue(instruction.timeText?.contains("03:00 PM") == true)
        assertTrue(instruction.peopleText?.contains("IT Coordinators") == true)
        assertTrue(drafts.none { it.action.contains("joining info", ignoreCase = true) })
    }
}
