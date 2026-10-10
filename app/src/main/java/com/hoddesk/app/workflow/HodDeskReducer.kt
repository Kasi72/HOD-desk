package com.hoddesk.app.workflow

data class WorkflowTask(val id: Long, val title: String, val status: Status = Status.OPEN, val eventId: Long? = null, val evidenceCount: Int = 0)
enum class Status { OPEN, REVIEW, SCHEDULED, COMPLETED }
data class WorkflowState(val sources: Set<Long> = emptySet(), val tasks: List<WorkflowTask> = emptyList(), val nextId: Long = 1)

sealed interface WorkflowAction {
    data class Ingest(val sourceId: Long) : WorkflowAction
    data class Review(val sourceId: Long) : WorkflowAction
    data class Approve(val title: String) : WorkflowAction
    data class Schedule(val taskId: Long, val eventId: Long) : WorkflowAction
    data class Complete(val taskId: Long, val evidenceCount: Int = 0) : WorkflowAction
}

fun reduce(state: WorkflowState, action: WorkflowAction): WorkflowState = when (action) {
    is WorkflowAction.Ingest -> state.copy(sources = state.sources + action.sourceId)
    is WorkflowAction.Review -> state.copy(tasks = state.tasks.map { if (it.id == action.sourceId) it.copy(status = Status.REVIEW) else it })
    is WorkflowAction.Approve -> state.copy(tasks = state.tasks + WorkflowTask(state.nextId, action.title), nextId = state.nextId + 1)
    is WorkflowAction.Schedule -> state.copy(tasks = state.tasks.map { if (it.id == action.taskId) it.copy(status = Status.SCHEDULED, eventId = action.eventId) else it })
    is WorkflowAction.Complete -> state.copy(tasks = state.tasks.map { if (it.id == action.taskId) it.copy(status = Status.COMPLETED, evidenceCount = action.evidenceCount) else it })
}
