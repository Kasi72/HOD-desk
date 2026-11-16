package com.hoddesk.app.reasoning

import com.hoddesk.app.data.ActionPointEntity
import org.junit.Assert.*
import org.junit.Test

class ReviewApprovalPolicyTest {
    private val point = ActionPointEntity(id = 1, sourceId = 1, action = "Attend meeting", dateText = "13.10.2026", timeText = "3 PM", evidence = "Attend at 3 PM; invitation says 3:30 PM", confidence = .8f, ambiguity = "Conflicting time needs confirmation")

    @Test fun unresolved_conflict_never_schedules() {
        assertNull(ReviewApprovalPolicy.dueAt(point))
    }
    @Test fun explicitly_confirmed_correction_can_schedule() {
        val edited = ReviewApprovalPolicy.edit(point, "Attend meeting", "13.10.2026", "3:30 PM", "", "", "", "", true)
        assertNull(edited.ambiguity)
        assertNotNull(ReviewApprovalPolicy.dueAt(edited))
    }
    @Test fun saving_without_confirmation_preserves_warning() {
        val edited = ReviewApprovalPolicy.edit(point, "Attend meeting", "13.10.2026", "3:30 PM", "", "", "", "", false)
        assertEquals(point.ambiguity, edited.ambiguity)
        assertNull(ReviewApprovalPolicy.dueAt(edited))
    }
    @Test fun invalid_explicit_time_cannot_be_confirmed() {
        assertThrows(IllegalArgumentException::class.java) {
            ReviewApprovalPolicy.edit(point, "Attend meeting", "13.10.2026", "25 PM", "", "", "", "", true)
        }
    }
}
