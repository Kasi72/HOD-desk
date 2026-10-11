package com.hoddesk.app.workers

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import com.hoddesk.app.data.HodDeskDatabase
import com.hoddesk.app.privacy.ProcessingPolicy
import com.hoddesk.app.privacy.WorkType
import com.hoddesk.app.semantic.EmbeddingGemmaLiteRtRuntime
import com.hoddesk.app.semantic.SourceSemanticIndexer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IndexMaintenanceJobService : JobService() {
    private var job: kotlinx.coroutines.Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val runtime = EmbeddingGemmaLiteRtRuntime(this@IndexMaintenanceJobService)
            try {
                val battery = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
                val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
                val charging = battery?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) in setOf(android.os.BatteryManager.BATTERY_STATUS_CHARGING, android.os.BatteryManager.BATTERY_STATUS_FULL)
                val percent = if (scale > 0) level * 100 / scale else 0
                if (ProcessingPolicy.canRun(percent, charging, WorkType.HEAVY_INDEX)) {
                    val db = HodDeskDatabase.get(this@IndexMaintenanceJobService)
                    val sources = db.dao().sourcesOnce().filter { it.sensitivity != "T3" && it.sensitivity != "T4" }
                    SourceSemanticIndexer(db.dao(), runtime).indexAll(sources)
                }
            } catch (t: Throwable) {
                Log.e("HOD_SEMANTIC", "scheduled_index_failed", t)
            } finally {
                runtime.close()
                jobFinished(params, false)
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); job = null; return true }
}
