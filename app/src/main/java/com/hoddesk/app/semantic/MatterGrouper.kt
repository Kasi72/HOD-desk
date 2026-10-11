package com.hoddesk.app.semantic

import com.hoddesk.app.data.SourceEntity
import com.hoddesk.app.data.SourceRelationEntity

/** Builds deterministic matter files from the relation graph already produced by EmbeddingGemma. */
object MatterGrouper {
    data class Matter(val id: Long, val sources: List<SourceEntity>, val supersededSourceIds: Set<Long>) {
        val latest: SourceEntity get() = sources.maxBy { it.capturedAt }
        val hasConflict: Boolean get() = supersededSourceIds.isNotEmpty()
    }

    fun group(sources: List<SourceEntity>, relations: List<SourceRelationEntity>): List<Matter> {
        if (sources.isEmpty()) return emptyList()
        val byId = sources.associateBy { it.id }
        val parent = sources.associate { it.id to it.id }.toMutableMap()
        fun find(id: Long): Long {
            var root = parent[id] ?: id
            while (parent[root] != root) root = parent[root] ?: root
            var current = id
            while (parent[current] != current) {
                val next = parent[current] ?: current
                parent[current] = root
                current = next
            }
            return root
        }
        fun union(a: Long, b: Long) {
            val left = find(a); val right = find(b)
            if (left != right) parent[right] = left
        }
        relations.filter { it.relationType == "related" || it.relationType == "duplicate" || it.relationType == "supersedes" }
            .forEach { if (byId.containsKey(it.sourceId) && byId.containsKey(it.relatedSourceId)) union(it.sourceId, it.relatedSourceId) }
        val superseded = relations.filter { it.relationType == "supersedes" }.groupBy { find(it.sourceId) }
            .mapValues { (_, edges) -> edges.map { it.relatedSourceId }.toSet() }
        return sources.groupBy { find(it.id) }.map { (root, items) ->
            Matter(root, items.sortedByDescending { it.capturedAt }, superseded[root].orEmpty())
        }.sortedByDescending { it.latest.capturedAt }
    }
}
