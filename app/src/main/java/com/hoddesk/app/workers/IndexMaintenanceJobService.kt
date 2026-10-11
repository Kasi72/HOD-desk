package com.hoddesk.app.workers

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IndexMaintenanceJobService : JobService() {
    private var job: kotlinx.coroutines.Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            jobFinished(params, !IndexMaintenanceRunner.run(this@IndexMaintenanceJobService))
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); job = null; return true }
}
