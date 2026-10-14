package com.hoddesk.app.calendar

/** Stable identity persisted in both the pending Room row and provider description. */
object CalendarWriteIdentity {
    fun marker(localEventId: Long): String {
        require(localEventId > 0) { "A calendar write requires a durable local event" }
        return "HODDESK_EVENT:$localEventId"
    }

    fun matches(description: String?, localEventId: Long): Boolean =
        description?.lineSequence()?.any { it.trim() == marker(localEventId) } == true
}
