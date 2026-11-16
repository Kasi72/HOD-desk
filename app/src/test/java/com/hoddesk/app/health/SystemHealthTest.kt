package com.hoddesk.app.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemHealthTest {
    private val healthy = SystemHealth.Snapshot(
        databaseOk = true, calendarChosen = true, calendarPermission = true, remindersOn = true, notificationPermission = true,
        autoLockOn = true, deviceSecure = true, freeBytes = 10L * 1024 * 1024 * 1024, reasoningModelNeedsDownload = false,
    )

    private fun ids(s: SystemHealth.Snapshot) = SystemHealth.check(s).map { it.id }

    @Test fun healthyPhoneHasNoBanner() {
        assertTrue(SystemHealth.check(healthy).isEmpty())
        // The database check still running is not a problem.
        assertTrue(SystemHealth.check(healthy.copy(databaseOk = null)).isEmpty())
    }

    @Test fun databaseFailureIsBlockingAndFirst() {
        val issues = SystemHealth.check(healthy.copy(databaseOk = false, freeBytes = 1))
        assertEquals("database", issues.first().id)
        assertEquals(SystemHealth.Severity.BLOCKED, issues.first().severity)
    }

    @Test fun permissionsOnlyMatterWhenTheFeatureIsUsed() {
        assertEquals(listOf("calendar"), ids(healthy.copy(calendarPermission = false)))
        assertTrue(ids(healthy.copy(calendarPermission = false, calendarChosen = false)).isEmpty())
        assertEquals(listOf("notifications"), ids(healthy.copy(notificationPermission = false)))
        assertTrue(ids(healthy.copy(notificationPermission = false, remindersOn = false)).isEmpty())
        assertEquals(listOf("autolock"), ids(healthy.copy(deviceSecure = false)))
        assertTrue(ids(healthy.copy(deviceSecure = false, autoLockOn = false)).isEmpty())
    }

    @Test fun lowStorageAndModelDownload() {
        assertEquals(listOf("storage"), ids(healthy.copy(freeBytes = SystemHealth.LOW_STORAGE_BYTES - 1)))
        assertTrue(ids(healthy.copy(freeBytes = -1)).isEmpty()) // unknown free space: say nothing
        assertEquals(listOf("reasoning_model"), ids(healthy.copy(reasoningModelNeedsDownload = true)))
    }
}
