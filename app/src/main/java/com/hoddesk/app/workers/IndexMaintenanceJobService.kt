package com.hoddesk.app.workers

import android.app.job.JobParameters
import android.app.job.JobService
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import com.hoddesk.app.data.HodDeskDatabase
import com.hoddesk.app.privacy.PrivacyStore
import com.hoddesk.app.repository.HodDeskRepository
import com.hoddesk.app.calendar.CalendarProviderScheduler
import com.hoddesk.app.privacy.ProcessingPolicy
import com.hoddesk.app.privacy.WorkType
import com.hoddesk.app.semantic.EmbeddingGemmaLiteRtRuntime
import com.hoddesk.app.semantic.SourceSemanticIndexer
import com.hoddesk.app.semantic.IndexSelfCheck
import com.hoddesk.app.observability.PerformanceMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IndexMaintenanceJobService : JobService() {
    private var job: kotlinx.coroutines.Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var runtime: EmbeddingGemmaLiteRtRuntime? = null
            var db: HodDeskDatabase? = null
            var succeeded = false
            val startedAt = System.currentTimeMillis()
            val progress = getSharedPreferences("semantic_index_progress", MODE_PRIVATE)
            try {
                val battery = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
                val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
                val charging = battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) in setOf(android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_STATUS_FULL)
                val percent = if (scale > 0) level * 100 / scale else 0
                progress.edit().putString("state", "waiting_for_policy").putLong("started_at", startedAt).apply()
                if (ProcessingPolicy.canRun(percent, charging, WorkType.HEAVY_INDEX)) {
                    // Do not initialize optional native inference until policy permits
                    // heavy work. A missing/incompatible delegate must never prevent
                    // JobService from finishing or affect normal app startup.
                    runtime = EmbeddingGemmaLiteRtRuntime(this@IndexMaintenanceJobService)
                    db = HodDeskDatabase.get(this@IndexMaintenanceJobService)
                    val repository = HodDeskRepository(db!!.dao())
                    PerformanceMetrics.record(db!!.dao(), "battery_percent", percent.toLong(), "charging=$charging")
                    PerformanceMetrics.record(db!!.dao(), "memory_available_bytes", Runtime.getRuntime().freeMemory(), "heap_max=${Runtime.getRuntime().maxMemory()}")
                    progress.edit().putString("state", "running").apply()
                    val lastSelfCheck = progress.getLong("last_self_check", 0L)
                    if (startedAt - lastSelfCheck >= 7L * 24L * 60L * 60L * 1000L) {
                        val selfCheck = IndexSelfCheck.run(runtime!!)
                        progress.edit().putLong("last_self_check", startedAt).apply()
                        PerformanceMetrics.record(db!!.dao(), "weekly_self_check", if (selfCheck.isSuccess) 1 else 0, if (selfCheck.isFailure) selfCheck.exceptionOrNull()?.javaClass?.simpleName else "passed")
                    }
                    repository.purgeExpiredSources(PrivacyStore(this@IndexMaintenanceJobService).retentionCutoff())
                    if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                        val scheduler = CalendarProviderScheduler.fromResolver(contentResolver) { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                        repository.reconcilePendingCalendarEvents { event -> scheduler.findMatchingEventId(event.title, event.startsAt, event.endsAt) }
                    }
                    val sources = db.dao().sourcesOnce().filter { it.sensitivity != "T3" && it.sensitivity != "T4" }.sortedBy { it.id }
                    val cursor = progress.getLong("source_cursor", 0L)
                    val pending = if (cursor == 0L) sources else sources.filter { it.id > cursor }
                    val indexer = SourceSemanticIndexer(db!!.dao(), runtime!!)
                    pending.forEachIndexed { index, source ->
                        val itemStarted = System.currentTimeMillis()
                        indexer.index(source)
                        progress.edit().putLong("source_cursor", source.id).putInt("completed", index + 1).putInt("total", pending.size).apply()
                        PerformanceMetrics.record(db!!.dao(), "index_source_latency_ms", System.currentTimeMillis() - itemStarted, "source_type=${source.sourceType}")
                    }
                    if (pending.size == sources.size || sources.all { it.id <= progress.getLong("source_cursor", 0L) }) progress.edit().remove("source_cursor").putString("state", "complete").apply()
                    PerformanceMetrics.record(db!!.dao(), "index_run_latency_ms", System.currentTimeMillis() - startedAt, "sources=${pending.size};delegate=${runtime!!.delegateName()}")
                }
                succeeded = true
            } catch (t: Throwable) {
                Log.e("HOD_SEMANTIC", "scheduled_index_failed", t)
                progress.edit().putString("state", "failed").putString("last_error", t.javaClass.simpleName).apply()
                db?.let { runCatching { PerformanceMetrics.record(it.dao(), "index_run_failure", 1, t.javaClass.simpleName); PerformanceMetrics.record(it.dao(), "queue_failure", 1, "index_maintenance") } }
            } finally {
                runtime?.close()
                jobFinished(params, !succeeded)
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); job = null; return true }
}
