package com.hoddesk.app.secretary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSecretaryTest {
    @Test fun multilingualWaitingCommandsShareIntent() {
        assertEquals(SecretaryIntent.Kind.SHOW_WAITING, VoiceSecretary.parse("What am I waiting for?").kind)
        assertEquals(SecretaryIntent.Kind.SHOW_WAITING, VoiceSecretary.parse("எதற்காக காத்திருக்க").kind)
    }

    @Test fun consequentialCommandsRequireConfirmation() {
        assertTrue(VoiceSecretary.parse("Create task to submit roster").requiresConfirmation)
        assertTrue(VoiceSecretary.parse("மாற்று meeting to Friday").requiresConfirmation)
    }
}
