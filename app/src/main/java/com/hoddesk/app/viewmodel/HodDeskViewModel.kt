package com.hoddesk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.ActionPointEntity
import com.hoddesk.app.repository.HodDeskRepository
import com.hoddesk.app.repository.ActionPointDraft
import com.hoddesk.app.reasoning.ActionReasoningOutput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Log

class HodDeskViewModel(private val repository: HodDeskRepository) : ViewModel() {
    /** Database/provider failures must be surfaced as a logged operation
     * failure, never as an uncaught exception that takes down the activity. */
    private fun launchSafe(operation: String, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Log.e("HOD_DB", operation, failure)
        }
    }
    val sources = repository.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ingest(source: SourceEntity) = launchSafe("ingest_failed") { repository.ingest(source) }
    fun ingestWithActions(source: SourceEntity, actions: List<ActionPointDraft>) = launchSafe("ingest_with_actions_failed") { repository.ingestWithActions(source, actions) }
    fun approve(sourceId: Long, project: String, dueAt: Long? = null) = launchSafe("approve_source_failed") { repository.approveSource(sourceId, project, dueAt) }
    fun approveAction(sourceId: Long, action: String, project: String, dueAt: Long? = null, assignee: String? = null) = launchSafe("approve_action_failed") { repository.approveAction(sourceId, action, project, dueAt, assignee) }
    fun dismissSource(sourceId: Long, reason: String) = launchSafe("dismiss_source_failed") { repository.dismissSource(sourceId, reason) }
    fun returnToInbox(sourceId: Long) = launchSafe("return_to_inbox_failed") { repository.returnSourceToInbox(sourceId) }
    fun setTaskDue(taskId: Long, dueAt: Long?) = launchSafe("set_due_failed") { repository.setTaskDue(taskId, dueAt) }
    fun setAssignee(taskId: Long, assignee: String?) = launchSafe("set_assignee_failed") { repository.setAssignee(taskId, assignee) }
    fun deleteAllWorkData() = launchSafe("delete_all_failed") { repository.deleteAllWorkData() }
    val auditLog =repository.observeAllAudit().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    suspend fun proposeSchedule(title: String, startsAt: Long, endsAt: Long, taskId: Long? = null) = repository.proposeSchedule(title, startsAt, endsAt, taskId)
    fun confirmSchedule(proposal: com.hoddesk.app.repository.ScheduleProposal) = launchSafe("confirm_schedule_failed") { repository.confirmSchedule(proposal) }
    fun setActionPointStatus(sourceId: Long, action: String, status: String) = launchSafe("action_status_failed") { repository.setActionPointStatus(sourceId, action, status) }
    fun updateActionPoint(point: ActionPointEntity) = launchSafe("update_action_point_failed") { repository.updateActionPoint(point) }
    fun approveEditedAction(point: ActionPointEntity?, sourceId: Long, project: String, action: String, assignee: String? = null) = launchSafe("approve_edited_action_failed") {
        point?.let { repository.updateActionPoint(it) }
        repository.approveAction(sourceId, action, project, assignee = assignee)
    }
    fun complete(taskId: Long) = launchSafe("complete_task_failed") { repository.completeTask(taskId) }
    fun setDone(taskId: Long, done: Boolean) = launchSafe("set_done_failed") { repository.setTaskDone(taskId, done) }
    fun setBlocker(taskId: Long, blocker: String?) = launchSafe("set_blocker_failed") { repository.setBlocker(taskId, blocker) }
    fun clarify(taskId: Long, detail: String) = launchSafe("clarification_failed") { repository.requestClarification(taskId, detail) }
    fun addEvidence(taskId: Long, uri: String, type: String, note: String? = null) = launchSafe("add_evidence_failed") { repository.addEvidence(taskId, uri, type, note) }
    fun saveReasoning(sourceId: Long, output: ActionReasoningOutput) = launchSafe("save_reasoning_failed") { repository.saveReasoningRun(sourceId, output) }
    fun deleteSource(id: Long) = launchSafe("delete_source_failed") { repository.deleteSource(id) }
    fun deleteTask(id: Long) = launchSafe("delete_task_failed") { repository.deleteTask(id) }
    fun deleteEvent(id: Long) = launchSafe("delete_event_failed") { repository.deleteEvent(id) }
    fun purgeAuditBefore(before: Long) = launchSafe("purge_audit_failed") { repository.purgeAuditBefore(before) }
    fun purgeExpiredSources(before: Long) = launchSafe("purge_sources_failed") { repository.purgeExpiredSources(before) }
    fun schedulePlannedTask(taskId: Long, title: String, startsAt: Long, endsAt: Long) = launchSafe("schedule_task_failed") { repository.schedulePlannedTask(taskId, title, startsAt, endsAt) }
}

class HodDeskViewModelFactory(private val repository: HodDeskRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HodDeskViewModel(repository) as T
}
