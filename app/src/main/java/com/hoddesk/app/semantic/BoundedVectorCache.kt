package com.hoddesk.app.semantic

/** Small process-local LRU for deterministic vectors. Values are copied on
 * ingress/egress so callers cannot mutate a cached embedding while another
 * search is using it. The byte budget keeps media-heavy screens bounded. */
class BoundedVectorCache(
    private val maxEntries: Int = 96,
    private val maxBytes: Long = 2L * 1024L * 1024L,
) {
    private val values = LinkedHashMap<String, FloatArray>(maxEntries, .75f, true)
    private var bytes = 0L

    @Synchronized fun get(key: String): FloatArray? = values[key]?.copyOf()

    @Synchronized fun put(key: String, vector: FloatArray) {
        values.remove(key)?.let { bytes -= it.size * 4L }
        val copy = vector.copyOf()
        values[key] = copy
        bytes += copy.size * 4L
        while (values.size > maxEntries || bytes > maxBytes) {
            val eldest = values.entries.firstOrNull() ?: break
            bytes -= eldest.value.size * 4L
            values.remove(eldest.key)
        }
    }

    @Synchronized fun clear() {
        values.clear()
        bytes = 0L
    }
}
