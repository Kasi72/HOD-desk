package com.hoddesk.app.data

/** Persisted source lifecycle. Transitions are explicit and reject regressions. */
enum class SourceLifecycle(val wire: String) {
    IMPORTED("imported"), PROCESSING("processing"), REVIEW("review"), APPROVED("approved"), TASK_EVENT("task_event"), COMPLETED("completed"), DELETED("deleted");

    fun canTransitionTo(next: SourceLifecycle): Boolean = when (this) {
        // COMPLETED from IMPORTED/REVIEW is the reviewer closing an item without a task
        // ("Done with This Item", "Not mine", "Not an action anymore").
        IMPORTED -> next in setOf(PROCESSING, REVIEW, APPROVED, COMPLETED, DELETED)
        PROCESSING -> next in setOf(REVIEW, IMPORTED, DELETED)
        REVIEW -> next in setOf(APPROVED, IMPORTED, COMPLETED, DELETED)
        APPROVED -> next in setOf(TASK_EVENT, COMPLETED, DELETED)
        TASK_EVENT -> next in setOf(COMPLETED, DELETED)
        COMPLETED -> next in setOf(REVIEW, COMPLETED, DELETED)
        DELETED -> next == this
    }

    companion object {
        fun parse(value: String): SourceLifecycle = entries.firstOrNull { it.wire == value } ?: IMPORTED
    }
}

fun SourceEntity.transitionTo(next: SourceLifecycle): SourceEntity {
    val current = SourceLifecycle.parse(lifecycleState)
    require(current.canTransitionTo(next) || current == next) { "Invalid source lifecycle transition: ${current.wire} -> ${next.wire}" }
    return copy(lifecycleState = next.wire, processingStatus = when (next) {
        SourceLifecycle.DELETED -> "deleted"
        SourceLifecycle.COMPLETED -> "completed"
        SourceLifecycle.REVIEW -> "needs_review"
        SourceLifecycle.PROCESSING -> "processing"
        else -> processingStatus
    })
}
