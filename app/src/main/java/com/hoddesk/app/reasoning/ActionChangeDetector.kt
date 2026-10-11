package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity

/** Compares successive extracted actions so revised instructions become edits, not duplicates. */
object ActionChangeDetector {
    data class Change(val field: String, val previous: String?, val current: String?)

    fun compare(previous: ActionPointEntity, current: ActionPointEntity): List<Change> = buildList {
        if (previous.action.trim() != current.action.trim()) add(Change("action", previous.action, current.action))
        if (previous.dateText != current.dateText) add(Change("date", previous.dateText, current.dateText))
        if (previous.timeText != current.timeText) add(Change("time", previous.timeText, current.timeText))
        if (previous.locationText != current.locationText) add(Change("location", previous.locationText, current.locationText))
        if (previous.peopleText != current.peopleText) add(Change("people", previous.peopleText, current.peopleText))
        if (previous.category != current.category) add(Change("category", previous.category, current.category))
    }
}
