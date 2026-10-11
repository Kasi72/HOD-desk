package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity
import java.security.MessageDigest

/** Stable local identity for re-import protection; it never leaves the device. */
object SourceFingerprint {
    fun of(source: SourceEntity): String = of(source.sourceType, source.body)

    fun of(sourceType: String, body: String): String {
        val canonical = sourceType.trim().lowercase() + "|" + body
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
