package com.hoddesk.app.privacy

import android.content.Context
import android.content.SharedPreferences

/** Small, synchronous policy store used before any sensitive source is ingested. */
class PrivacyStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hod_desk_privacy", Context.MODE_PRIVATE)

    var consentGranted: Boolean
        get() = prefs.getBoolean("consent_granted", false)
        set(value) = prefs.edit().putBoolean("consent_granted", value).apply()

    var retentionDays: Int
        get() = prefs.getInt("retention_days", 90)
        set(value) = prefs.edit().putInt("retention_days", value.coerceIn(1, 3650)).apply()

    var redactIdentifiers: Boolean
        get() = prefs.getBoolean("redact_identifiers", true)
        set(value) = prefs.edit().putBoolean("redact_identifiers", value).apply()

    fun canImportSensitive(): Boolean = consentGranted

    fun scopeGranted(scope: ConsentScope): Boolean = prefs.getBoolean("scope_${scope.name}", consentGranted)
    fun setScope(scope: ConsentScope, granted: Boolean) = prefs.edit().putBoolean("scope_${scope.name}", granted).apply()

    fun redact(text: String): String = if (!redactIdentifiers) text else text
        .replace(Regex("\\b[0-9]{10}\\b"), "[PHONE]")
        .replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[EMAIL]")

    /** Cutoff used by the explicit retention action; no data is removed implicitly. */
    fun retentionCutoff(now: Long = System.currentTimeMillis()): Long = now - retentionDays * 24L * 60L * 60L * 1000L
}
