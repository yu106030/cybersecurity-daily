package com.cybersecdaily.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://unclecheng-li.github.io/cybersecurity-daily"

class CyberSecDailyWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        cancelLegacyGlanceWork(context)
        updateLoading(context, appWidgetManager, appWidgetIds)
        requestImmediateRefresh(context)
        schedulePeriodicRefresh(context)
    }

    override fun onEnabled(context: Context) {
        cancelLegacyGlanceWork(context)
        requestImmediateRefresh(context)
        schedulePeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        workManager.cancelUniqueWork(LEGACY_REFRESH_WORK_NAME)
        workManager.cancelAllWorkByTag(LEGACY_GLANCE_SESSION_TAG)
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "cybersec_daily_periodic_refresh"
        private const val IMMEDIATE_WORK_NAME = "cybersec_daily_immediate_refresh"
        private const val LEGACY_REFRESH_WORK_NAME = "cybersec_daily_refresh"
        private const val LEGACY_GLANCE_SESSION_TAG = "androidx.glance.session.SessionWorker"

        fun updateWidgets(context: Context, report: DailyReport) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, CyberSecDailyWidgetReceiver::class.java)
            )
            if (ids.isEmpty()) return

            val views = createViews(context, report)
            for (id in ids) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        private fun updateLoading(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val loading = DailyReport(
                date = "Loading",
                keywords = "Fetching latest security news...",
                headlines = listOf("Fetching latest report...")
            )
            val views = createViews(context, loading)
            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        private fun requestImmediateRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun cancelLegacyGlanceWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(LEGACY_REFRESH_WORK_NAME)
            workManager.cancelAllWorkByTag(LEGACY_GLANCE_SESSION_TAG)
        }

        private fun schedulePeriodicRefresh(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                3,
                TimeUnit.HOURS,
                30,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun createViews(context: Context, report: DailyReport): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val hasError = report.error != null
            val headlines = report.headlines.filter { it.isNotBlank() }

            views.setTextViewText(R.id.widget_date, report.date.ifBlank { "Latest" })
            views.setTextViewText(
                R.id.widget_keywords,
                if (hasError) "Update failed" else report.keywords.ifBlank { "Daily security highlights" }
            )
            views.setTextViewText(
                R.id.widget_headline_1,
                if (hasError) report.error ?: "Update failed" else formatHeadline(headlines.getOrNull(0))
            )
            views.setTextViewText(R.id.widget_headline_2, formatHeadline(headlines.getOrNull(1)))
            views.setTextViewText(R.id.widget_headline_3, formatHeadline(headlines.getOrNull(2)))
            views.setTextViewText(
                R.id.widget_footer,
                if (hasError) "Retrying later" else "Tap to read full report"
            )

            val url = if (!hasError && report.date.isNotBlank()) {
                "$BASE_URL/daily/${report.date}.html"
            } else {
                BASE_URL
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val pendingIntent = PendingIntent.getActivity(
                context,
                url.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            return views
        }

        private fun formatHeadline(text: String?): String {
            if (text.isNullOrBlank()) return ""
            return "> $text"
        }
    }
}
