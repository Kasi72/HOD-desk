package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity
import java.security.MessageDigest

/** Stable local identity for re-import protection; it never leaves the device. */
object SourceFingerprint {
    const val EXTRACTOR_VERSION = 2

    /** Includes the source location and extractor contract, so replacing a
     * file at the same URI or changing extraction rules invalidates vectors. */
    fun of(source: SourceEntity): String = of(source.sourceType, source.body, source.sourceUri, EXTRACTOR_VERSION)

    fun of(sourceType: String, body: String, sourceUri: String? = null, extractorVersion: Int = EXTRACTOR_VERSION): String {
        val canonical = "extractor=$extractorVersion|type=${sourceType.trim().lowercase()}|uri=${sourceUri?.trim()?.lowercase().orEmpty()}|body=" + body
            .lowercase().replace("\r\n", "\n")
            .replace(Regex("\\s+"), " ")
            .trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

