package com.hoddesk.app.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object IndexMaintenanceScheduler {
    private const val WORK_NAME = "hoddesk-index-maintenance"
    private const val STARTUP_WORK_NAME = "hoddesk-startup-integrity"
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiresCharging(true).setRequiredNetworkType(NetworkType.NOT_REQUIRED).build()
        val startup = OneTimeWorkRequestBuilder<IndexMaintenanceWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(STARTUP_WORK_NAME, ExistingWorkPolicy.KEEP, startup)
        val request = PeriodicWorkRequestBuilder<IndexMaintenanceWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
