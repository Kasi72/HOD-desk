package com.hoddesk.app.workers

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.util.Log
import com.hoddesk.app.calendar.CalendarProviderScheduler
import com.hoddesk.app.data.HodDeskDatabase
import com.hoddesk.app.observability.PerformanceMetrics
import com.hoddesk.app.privacy.PrivacyStore
import com.hoddesk.app.privacy.ProcessingPolicy
import com.hoddesk.app.privacy.WorkType
import com.hoddesk.app.repository.HodDeskRepository
import com.hoddesk.app.semantic.EmbeddingGemmaLiteRtRuntime
import com.hoddesk.app.semantic.IndexSelfCheck
import com.hoddesk.app.semantic.SourceSemanticIndexer
import com.hoddesk.app.semantic.SourceIndexPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared, resumable maintenance implementation for WorkManager and legacy callers. */
object IndexMaintenanceRunner {
    suspend fun run(context: Context): Boolean {
        val startedAt = System.currentTimeMillis()
        val progress = context.getSharedPreferences("semantic_index_progress", Context.MODE_PRIVATE)
        var runtime: EmbeddingGemmaLiteRtRuntime? = null
        var db: HodDeskDatabase? = null
        return try {
            db = HodDeskDatabase.get(context)
            val dao = db!!.dao()
            val repository = HodDeskRepository(dao)
            // Retention and calendar reconciliation do not need the AI runtime.
            // A missing model or low battery must not prevent privacy upkeep.
            repository.purgeExpiredSources(PrivacyStore(context).retentionCutoff())
            dao.deletePerformanceMetricsBefore(startedAt - 30L * 24L * 60L * 60L * 1000L)
            if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                val calendar = CalendarProviderScheduler.fromResolver(context.contentResolver) { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                repository.reconcilePendingCalendarEvents { event -> calendar.findMatchingEventId(event.title, event.startsAt, event.endsAt, event.id) }
            }
            currentCoroutineContext().ensureActive()
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val charging = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
            val percent = (if (scale > 0) level.toLong() * 100L / scale else 0L).toInt().coerceIn(0, 100)
            progress.edit().putString("state", "waiting_for_policy").putLong("started_at", startedAt).apply()
            if (!ProcessingPolicy.canRun(percent, charging, WorkType.HEAVY_INDEX)) return false
            val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo().also { memory?.getMemoryInfo(it) }
            if (memoryInfo.lowMemory || memoryInfo.availMem < 512L * 1024L * 1024L) {
                progress.edit().putString("state", "waiting_for_memory").putLong("available_memory", memoryInfo.availMem).apply()
                return false
            }
            runtime = EmbeddingGemmaLiteRtRuntime(context)
            if (!runtime!!.isInstalled()) {
                progress.edit().putString("state", "waiting_for_model").apply()
                return false
            }
            PerformanceMetrics.record(dao, "battery_percent", percent.toLong(), "charging=$charging")
            PerformanceMetrics.record(dao, "memory_available_bytes", memoryInfo.availMem, "heap_max=${Runtime.getRuntime().maxMemory()}")
            progress.edit().putString("state", "running").apply()
            val lastSelfCheck = progress.getLong("last_self_check", 0L)
            if (startedAt - lastSelfCheck >= 7L * 24L * 60L * 60L * 1000L) {
                val selfCheck = IndexSelfCheck.run(runtime!!)
                PerformanceMetrics.record(dao, "weekly_self_check", if (selfCheck.isSuccess) 1 else 0, if (selfCheck.isFailure) selfCheck.exceptionOrNull()?.javaClass?.simpleName else "passed")
                if (selfCheck.isFailure) {
                    progress.edit().putString("state", "self_check_failed").putString("last_error", selfCheck.exceptionOrNull()?.javaClass?.simpleName).apply()
                    return false
                }
                progress.edit().putLong("last_self_check", startedAt).apply()
            }
            val sources = dao.sourcesOnce().filter(SourceIndexPolicy::searchable).sortedBy { it.id }
            // Let fingerprint/chunk checks resume completed records. An ID cursor
            // alone skips a source edited while a previous run was interrupted.
            val pending = sources
            val indexer = SourceSemanticIndexer(dao, runtime!!)
            pending.forEachIndexed { index, source ->
                currentCoroutineContext().ensureActive()
                val itemStarted = System.currentTimeMillis()
                indexer.index(source)
                progress.edit().putLong("source_cursor", source.id).putInt("completed", index + 1).putInt("total", pending.size).apply()
                PerformanceMetrics.record(dao, "index_source_latency_ms", System.currentTimeMillis() - itemStarted, "source_type=${source.sourceType}")
            }
            if (pending.size == sources.size || sources.all { it.id <= progress.getLong("source_cursor", 0L) }) progress.edit().remove("source_cursor").putString("state", "complete").apply()
            PerformanceMetrics.record(dao, "index_run_latency_ms", System.currentTimeMillis() - startedAt, "sources=${pending.size};delegate=${runtime!!.delegateName()}")
            true
        } catch (cancelled: CancellationException) {
            progress.edit().putString("state", "interrupted").apply()
            throw cancelled
        } catch (t: Throwable) {
            Log.e("HOD_SEMANTIC", "scheduled_index_failed", t)
            progress.edit().putString("state", "failed").putString("last_error", t.javaClass.simpleName).apply()
            db?.let { runCatching { PerformanceMetrics.record(it.dao(), "index_run_failure", 1, t.javaClass.simpleName); PerformanceMetrics.record(it.dao(), "queue_failure", 1, "index_maintenance") } }
            false
        } finally {
            // Optional native teardown must not replace a cancellation or result.
            runCatching { runtime?.close() }.onFailure { Log.w("HOD_SEMANTIC", "runtime_close_failed", it) }
        }
    }
}
