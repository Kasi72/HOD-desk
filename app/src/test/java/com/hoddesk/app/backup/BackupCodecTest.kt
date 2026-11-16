package com.hoddesk.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupCodecTest {
    private val iterations = 100_000 // fast for tests; the app uses the 600k default
    private val payload = "{\"format\":\"hoddesk-backup\",\"text\":\"மின் அதிர்வு சிகிச்சை · ECT report\"}".toByteArray()

    @Test fun roundTrip() {
        val encrypted = BackupCodec.encrypt(payload, "correct horse".toCharArray(), iterations)
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("ECT report"))
        assertArrayEquals(payload, BackupCodec.decrypt(encrypted, "correct horse".toCharArray()))
    }

    @Test(expected = WrongPasswordException::class) fun wrongPasswordIsRejected() {
        val encrypted = BackupCodec.encrypt(payload, "correct horse".toCharArray(), iterations)
        BackupCodec.decrypt(encrypted, "wrong horse!".toCharArray())
    }

    @Test(expected = WrongPasswordException::class) fun tamperedFileIsRejected() {
        val encrypted = BackupCodec.encrypt(payload, "correct horse".toCharArray(), iterations)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 1).toByte()
        BackupCodec.decrypt(encrypted, "correct horse".toCharArray())
    }

    @Test(expected = WrongPasswordException::class) fun tamperedHeaderIsRejected() {
        val encrypted = BackupCodec.encrypt(payload, "correct horse".toCharArray(), iterations)
        encrypted[14] = (encrypted[14].toInt() xor 1).toByte() // inside the salt
        BackupCodec.decrypt(encrypted, "correct horse".toCharArray())
    }

    @Test(expected = NotABackupException::class) fun otherFilesAreRejected() {
        BackupCodec.decrypt("just some text file content".toByteArray(), "correct horse".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class) fun shortPasswordIsRefused() {
        BackupCodec.encrypt(payload, "short".toCharArray(), iterations)
    }
}
