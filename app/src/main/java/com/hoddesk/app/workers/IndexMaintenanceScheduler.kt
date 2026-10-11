package com.hoddesk.app.workers

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object IndexMaintenanceScheduler {
    private const val JOB_ID = 24017
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, IndexMaintenanceJobService::class.java))
            .setRequiresCharging(true)
            .setPersisted(true)
            .setPeriodic(24 * 60 * 60 * 1000L)
            .build())
    }
}
