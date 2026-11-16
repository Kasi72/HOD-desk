package com.hoddesk.app.semantic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedVectorCacheTest {
    @Test fun evictsLeastRecentlyUsedEntryWithinEntryBudget() {
        val cache = BoundedVectorCache(maxEntries = 2, maxBytes = 1024)
        cache.put("a", floatArrayOf(1f))
        cache.put("b", floatArrayOf(2f))
        assertArrayEquals(floatArrayOf(1f), cache.get("a"), 0f)
        cache.put("c", floatArrayOf(3f))
        assertNull(cache.get("b"))
        assertArrayEquals(floatArrayOf(3f), cache.get("c"), 0f)
    }

    @Test fun callersCannotMutateCachedValue() {
        val cache = BoundedVectorCache(maxEntries = 2, maxBytes = 1024)
        val original = floatArrayOf(1f, 2f)
        cache.put("x", original)
        original[0] = 99f
        val returned = cache.get("x")!!
        returned[1] = 88f
        assertArrayEquals(floatArrayOf(1f, 2f), cache.get("x"), 0f)
    }
}
