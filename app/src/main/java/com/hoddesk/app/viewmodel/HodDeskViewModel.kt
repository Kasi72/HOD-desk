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

class HodDeskViewModel(private val repository: HodDeskRepository) : ViewModel() {
    val sources = repository.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ingest(source: SourceEntity) = viewModelScope.launch { repository.ingest(source) }
    fun ingestWithActions(source: SourceEntity, actions: List<ActionPointDraft>) = viewModelScope.launch { repository.ingestWithActions(source, actions) }
    fun approve(sourceId: Long, project: String, dueAt: Long? = null) = viewModelScope.launch { repository.approveSource(sourceId, project, dueAt) }
    fun approveAction(sourceId: Long, action: String, project: String, dueAt: Long? = null, assignee: String? = null) = viewModelScope.launch { repository.approveAction(sourceId, action, project, dueAt, assignee) }
    fun dismissSource(sourceId: Long, reason: String) = viewModelScope.launch { repository.dismissSource(sourceId, reason) }
    fun returnToInbox(sourceId: Long) = viewModelScope.launch { repository.returnSourceToInbox(sourceId) }
    fun setTaskDue(taskId: Long, dueAt: Long?) = viewModelScope.launch { repository.setTaskDue(taskId, dueAt) }
    fun setAssignee(taskId: Long, assignee: String?) = viewModelScope.launch { repository.setAssignee(taskId, assignee) }
    fun deleteAllWorkData() = viewModelScope.launch { repository.deleteAllWorkData() }
    val auditLog =repository.observeAllAudit().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    suspend fun proposeSchedule(title: String, startsAt: Long, endsAt: Long, taskId: Long? = null) = repository.proposeSchedule(title, startsAt, endsAt, taskId)
    fun confirmSchedule(proposal: com.hoddesk.app.repository.ScheduleProposal) = viewModelScope.launch { repository.confirmSchedule(proposal) }
    fun setActionPointStatus(sourceId: Long, action: String, status: String) = viewModelScope.launch { repository.setActionPointStatus(sourceId, action, status) }
    fun updateActionPoint(point: ActionPointEntity) = viewModelScope.launch { repository.updateActionPoint(point) }
    fun approveEditedAction(point: ActionPointEntity?, sourceId: Long, project: String, action: String, assignee: String? = null) = viewModelScope.launch {
        point?.let { repository.updateActionPoint(it) }
        repository.approveAction(sourceId, action, project, assignee = assignee)
    }
    fun complete(taskId: Long) = viewModelScope.launch { repository.completeTask(taskId) }
    fun setDone(taskId: Long, done: Boolean) = viewModelScope.launch { repository.setTaskDone(taskId, done) }
    fun setBlocker(taskId: Long, blocker: String?) = viewModelScope.launch { repository.setBlocker(taskId, blocker) }
    fun clarify(taskId: Long, detail: String) = viewModelScope.launch { repository.requestClarification(taskId, detail) }
    fun addEvidence(taskId: Long, uri: String, type: String, note: String? = null) = viewModelScope.launch { repository.addEvidence(taskId, uri, type, note) }
    fun saveReasoning(sourceId: Long, output: ActionReasoningOutput) = viewModelScope.launch { repository.saveReasoningRun(sourceId, output) }
    fun deleteSource(id: Long) = viewModelScope.launch { repository.deleteSource(id) }
    fun deleteTask(id: Long) = viewModelScope.launch { repository.deleteTask(id) }
    fun deleteEvent(id: Long) = viewModelScope.launch { repository.deleteEvent(id) }
    fun purgeAuditBefore(before: Long) = viewModelScope.launch { repository.purgeAuditBefore(before) }
    fun purgeExpiredSources(before: Long) = viewModelScope.launch { repository.purgeExpiredSources(before) }
    fun schedulePlannedTask(taskId: Long, title: String, startsAt: Long, endsAt: Long) = viewModelScope.launch { repository.schedulePlannedTask(taskId, title, startsAt, endsAt) }
}

class HodDeskViewModelFactory(private val repository: HodDeskRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HodDeskViewModel(repository) as T
}
