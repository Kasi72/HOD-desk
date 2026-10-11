package com.hoddesk.app.observability

import com.hoddesk.app.data.HodDeskDao
import com.hoddesk.app.data.PerformanceMetricEntity

/** Small, privacy-preserving recorder for local performance and queue health. */
object PerformanceMetrics {
    suspend fun record(dao: HodDeskDao, kind: String, value: Long, detail: String? = null) {
        dao.insertPerformanceMetric(PerformanceMetricEntity(kind = kind, value = value, detail = detail?.take(240)))
    }
}
