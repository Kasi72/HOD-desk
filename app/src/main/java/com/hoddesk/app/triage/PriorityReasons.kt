package com.hoddesk.app.triage

import java.util.concurrent.TimeUnit

/**
 * Turns stored triage reasons into short, plain phrases a reviewer can scan, and adds the reasons that
 * depend on time or state (deadline proximity, conflicts) which cannot be stored at import.
 */
object PriorityReasons {
    private val plain = mapOf(
        "high-authority sender or office" to "Senior office",
        "addressed to hod" to "Addressed to you",
        "escalation wording" to "Marked urgent",
        "action language" to "Asks for action",
        "date or time cue" to "Mentions a date",
        "learned local preference" to "Like items you act on",
    )

    fun humanize(stored: String?): List<String> =
        stored.orEmpty().split("·").map(String::trim).filter(String::isNotBlank)
            .map { reason -> plain[reason.lowercase()] ?: reason.replaceFirstChar(Char::uppercase) }
            .distinct()

    /** Most decisive first: overdue/deadline and conflicts outrank wording cues. */
    fun forItem(stored: String?, dueAt: Long? = null, hasConflict: Boolean = false, now: Long = System.currentTimeMillis(), limit: Int = 3): List<String> {
        val timed = buildList {
            if (dueAt != null) {
                val left = dueAt - now
                when {
                    left < 0 -> add("Overdue")
                    left < TimeUnit.HOURS.toMillis(24) -> add("Due within a day")
                    left < TimeUnit.DAYS.toMillis(3) -> add("Due in ${TimeUnit.MILLISECONDS.toDays(left) + 1} days")
                }
            }
            if (hasConflict) add("Conflicting details")
        }
        val wording = humanize(stored).filterNot { it == "Mentions a date" && timed.any { t -> t.startsWith("Due") || t == "Overdue" } }
        return (timed + wording).distinct().take(limit)
    }
}
