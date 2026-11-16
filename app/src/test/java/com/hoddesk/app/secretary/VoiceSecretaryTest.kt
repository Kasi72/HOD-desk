package com.hoddesk.app.secretary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSecretaryTest {
    @Test fun readOnlyMeetingQueryNeverRequiresConfirmationAndRecognisesTomorrow() {
        val intent = VoiceSecretary.parse("Show my meetings tomorrow")
        assertEquals(SecretaryIntent.Kind.SHOW_MEETINGS, intent.kind)
        assertTrue(!intent.requiresConfirmation)
    }

    @Test fun moveMeetingProducesConstrainedConfirmationParameters() {
        val intent = VoiceSecretary.parse("Move the DME meeting to 3 PM")
        assertEquals(SecretaryIntent.Kind.MOVE_TASKS, intent.kind)
        assertTrue(intent.requiresConfirmation)
        assertEquals("15:00", intent.parameters["time"])
        assertTrue(intent.parameters["target"].orEmpty().contains("DME"))
    }

    @Test fun timeParserHandlesTamilAndEnglishSafeFormats() {
        assertEquals(15 to 0, VoiceSecretary.time("at 3 PM"))
        assertEquals(9 to 30, VoiceSecretary.time("9:30 am"))
        assertEquals(null, VoiceSecretary.time("sometime later"))
    }

    @Test fun mutatingDelegateAndScheduleCommandsRequireConfirmation() {
        val delegate = VoiceSecretary.parse("Delegate the report to Dr Rao")
        assertTrue(delegate.requiresConfirmation)
        assertEquals(SecretaryIntent.Kind.DELEGATE_TASK, delegate.kind)
        assertEquals(SecretaryIntent.Kind.SCHEDULE_TASK, VoiceSecretary.parse("Schedule the DME meeting at 3 PM").kind)
    }
}
