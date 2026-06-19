package com.cybersecdaily.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val report = ReportFetcher.fetchLatest()
        CyberSecDailyWidgetReceiver.updateWidgets(applicationContext, report)
        return if (report.error == null) {
            Result.success()
        } else if (runAttemptCount < 3) {
            Result.retry()
        } else {
            Result.failure()
        }
    }
}
