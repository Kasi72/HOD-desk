package com.hoddesk.app.semantic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexSnapshotManagerTest {
    @Test fun snapshotValidatesExactIndexIdentity() {
        val snapshot = IndexSnapshotManager.create("model-v1", 3, listOf(2, 1), 8)
        assertTrue(IndexSnapshotManager.validate(snapshot, "model-v1", 3, listOf(1, 2), 8))
        assertFalse(IndexSnapshotManager.validate(snapshot, "model-v2", 3, listOf(1, 2), 8))
        assertFalse(IndexSnapshotManager.validate(snapshot, "model-v1", 3, listOf(1, 2), 9))
    }
}
