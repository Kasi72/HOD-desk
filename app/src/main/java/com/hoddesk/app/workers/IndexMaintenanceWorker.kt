package com.hoddesk.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class IndexMaintenanceWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = if (IndexMaintenanceRunner.run(applicationContext)) Result.success() else Result.retry()
}
