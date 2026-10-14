package com.hoddesk.app.repository

import com.hoddesk.app.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Exercises the same default DAO transitions Room wraps in a transaction. */
class ApprovalIntegrityTest {
    @Test fun approving_one_candidate_keeps_other_candidates_in_review() = runTest {
        val state = DaoState()
        state.sources[1] = SourceEntity(id = 1, title = "Notice", body = "Two obligations", sourceType = "Document")
        state.points[1] = point(1, "Prepare report")
        state.points[2] = point(2, "Submit report")

        state.dao.approveActionAtomic(1, "Prepare report", "Office", null, null)

        assertFalse(state.sources.getValue(1).reviewed)
        assertEquals("approved", state.points.getValue(1).status)
        assertEquals("needs_review", state.points.getValue(2).status)
        state.dao.approveActionAtomic(1, "Submit report", "Office", null, null)
        assertTrue(state.sources.getValue(1).reviewed)
    }

    @Test fun repeated_approval_creates_one_task_and_one_commitment() = runTest {
        val state = DaoState()
        state.sources[1] = SourceEntity(id = 1, title = "Notice", body = "Submit report", sourceType = "Document")
        state.points[1] = point(1, "Submit report")

        val first = state.dao.approveActionAtomic(1, "Submit report", "Office", 100_000L, "Secretary")
        val repeated = state.dao.approveActionAtomic(1, "Submit report", "Office", 100_000L, "Secretary")

        assertEquals(first, repeated)
        assertEquals(1, state.tasks.size)
        assertEquals(1, state.events.size)
        assertEquals(1, state.commitments.size)
        assertEquals(HodDeskRepository.OWED_TO_ME, state.commitments.values.single().direction)
        assertEquals(1, state.corrections.size)
    }

    @Test fun due_backfill_preserves_existing_provider_mapping_and_duration() = runTest {
        val state = DaoState()
        state.tasks[1] = TaskEntity(id = 1, title = "Meeting", project = "Office", dueAt = 200_000L)
        state.events[7] = EventEntity(id = 7, taskId = 1, title = "Meeting", startsAt = 100_000L, endsAt = 130_000L, confirmed = true, calendarEventId = 91)

        HodDeskRepository(state.dao).backfillTaskEvents()

        assertEquals(1, state.events.size)
        assertEquals(91L, state.events.getValue(7).calendarEventId)
        assertEquals(100_000L, state.events.getValue(7).startsAt)
        assertEquals(130_000L, state.events.getValue(7).endsAt)
    }

    @Test fun stale_schedule_proposal_is_rejected_before_insert() = runTest {
        val state = DaoState()
        state.events[1] = EventEntity(id = 1, title = "Ward round", startsAt = 100_000L, endsAt = 300_000L)
        val stale = ScheduleProposal(EventEntity(title = "Report", startsAt = 200_000L, endsAt = 400_000L), emptyList())

        try {
            HodDeskRepository(state.dao).confirmSchedule(stale)
            fail("A conflict added after proposal generation must block confirmation")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, state.events.size)
    }

    private fun point(id: Long, action: String) = ActionPointEntity(id = id, sourceId = 1, action = action, evidence = action, confidence = .8f)

    private class DaoState {
        val sources = linkedMapOf<Long, SourceEntity>()
        val tasks = linkedMapOf<Long, TaskEntity>()
        val events = linkedMapOf<Long, EventEntity>()
        val points = linkedMapOf<Long, ActionPointEntity>()
        val commitments = linkedMapOf<Long, CommitmentEntity>()
        val corrections = mutableListOf<LearningCorrectionEntity>()
        val audits = mutableListOf<AuditEntryEntity>()
        private var nextId = 100L
        val dao: HodDeskDao = Proxy.newProxyInstance(HodDeskDao::class.java.classLoader, arrayOf(HodDeskDao::class.java)) { proxy, method, supplied ->
            val a = supplied.orEmpty()
            when (method.name) {
                "observeSources", "observeTasks" -> kotlinx.coroutines.flow.flowOf(emptyList<Any>())
                "source" -> sources[a[0]]
                "task" -> tasks[a[0]]
                "event" -> events[a[0]]
                "approvedTask" -> tasks.values.firstOrNull { it.sourceId == a[0] && it.title == a[1] && it.status != "deleted" }
                "actionPoints" -> points.values.filter { it.sourceId == a[0] }
                "actionPoint" -> points[a[0]]
                "actionPointBySourceAndAction" -> points.values.firstOrNull { it.sourceId == a[0] && it.action == a[1] }
                "eventsForTask" -> events.values.filter { it.taskId == a[0] }
                "overlappingEvents" -> events.values.filter { it.startsAt < a[1] as Long && it.endsAt > a[0] as Long }
                "tasksWithDueTimes" -> tasks.values.filter { it.dueAt != null && it.status !in setOf("deleted", "archived", "completed") }
                "commitmentForTask" -> commitments.values.firstOrNull { it.taskId == a[0] }
                "insertTask" -> (a[0] as TaskEntity).let { item -> val id = item.id.takeIf { it != 0L } ?: nextId++; tasks[id] = item.copy(id = id); id }
                "insertEvent" -> (a[0] as EventEntity).let { item -> val id = item.id.takeIf { it != 0L } ?: nextId++; events[id] = item.copy(id = id); id }
                "insertCommitment" -> (a[0] as CommitmentEntity).let { item -> val id = item.id.takeIf { it != 0L } ?: nextId++; commitments[id] = item.copy(id = id); id }
                "insertAudit" -> { audits += a[0] as AuditEntryEntity; nextId++ }
                "insertLearningCorrection" -> { corrections += a[0] as LearningCorrectionEntity; nextId++ }
                "updateSource" -> { val item = a[0] as SourceEntity; sources[item.id] = item; Unit }
                "updateTask" -> { val item = a[0] as TaskEntity; tasks[item.id] = item; Unit }
                "updateEvent" -> { val item = a[0] as EventEntity; events[item.id] = item; Unit }
                "updateActionPoint" -> { val item = a[0] as ActionPointEntity; points[item.id] = item; Unit }
                "setActionPointStatus" -> { points.replaceAll { _, point -> if (point.sourceId == a[0] && point.action == a[1]) point.copy(status = a[2] as String) else point }; Unit }
                "deleteEventsForTask" -> { events.entries.removeAll { it.value.taskId == a[0] }; Unit }
                else -> {
                    val defaults = Class.forName("com.hoddesk.app.data.HodDeskDao\$DefaultImpls")
                    val implementation = defaults.methods.firstOrNull { it.name == method.name && it.parameterCount == a.size + 1 }
                        ?: error("Unexpected DAO method ${method.name}")
                    try { implementation.invoke(null, proxy, *a) }
                    catch (failure: java.lang.reflect.InvocationTargetException) { throw failure.targetException }
                }
            }
        } as HodDeskDao
    }
}
