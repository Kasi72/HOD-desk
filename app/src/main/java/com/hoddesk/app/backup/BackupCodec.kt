package com.hoddesk.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Wrong password, or the backup file is damaged")
class NotABackupException : Exception("This file is not a HOD Desk backup")

/**
 * Password-encrypted container for backups. Layout:
 * `HODBK001` | iterations (int) | salt (16) | iv (12) | AES-256-GCM(gzip(payload)).
 * The header is bound as additional authenticated data, so any tampering fails decryption.
 * The key is derived with PBKDF2-HMAC-SHA256; the password itself is never stored.
 */
object BackupCodec {
    private val MAGIC = "HODBK001".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 5_000_000
    const val MIN_PASSWORD_LENGTH = 8

    fun encrypt(payload: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 4 + SALT_BYTES + IV_BYTES).put(MAGIC).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(gzip(payload))
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val headerSize = MAGIC.size + 4 + SALT_BYTES + IV_BYTES
        if (file.size <= headerSize || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackupException()
        val buffer = ByteBuffer.wrap(file)
        buffer.position(MAGIC.size)
        val iterations = buffer.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw NotABackupException()
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(file.copyOfRange(0, headerSize))
        val plain = try {
            cipher.doFinal(file, headerSize, file.size - headerSize)
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return gunzip(plain)
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(data: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()
    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
