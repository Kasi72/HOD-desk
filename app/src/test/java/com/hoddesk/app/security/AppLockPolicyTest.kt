package com.hoddesk.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L

    @Test fun locksAfterTimeout() {
        assertTrue(AppLockPolicy.shouldLock(true, true, t0, t0 + 5 * min, 5))
        assertTrue(AppLockPolicy.shouldLock(true, true, t0, t0 + 60 * min, 5))
    }

    @Test fun staysOpenWithinTimeout() {
        assertFalse(AppLockPolicy.shouldLock(true, true, t0, t0 + 5 * min - 1, 5))
    }

    @Test fun immediateLocksOnAnyReturn() {
        assertTrue(AppLockPolicy.shouldLock(true, true, t0, t0, 0))
    }

    @Test fun neverLocksWhenOffOrNoScreenLockOrFirstLaunch() {
        assertFalse(AppLockPolicy.shouldLock(false, true, t0, t0 + 99 * min, 5))
        assertFalse(AppLockPolicy.shouldLock(true, false, t0, t0 + 99 * min, 5))
        assertFalse(AppLockPolicy.shouldLock(true, true, null, t0, 5))
    }

    @Test fun clockMovedBackwardsLocks() {
        assertTrue(AppLockPolicy.shouldLock(true, true, t0, t0 - min, 30))
    }
}
