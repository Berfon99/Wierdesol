package com.example.wierdesol

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.d("WidgetUpdateWorker doWork called")
        // Call the function to update the widget
        updateWidget(applicationContext)
        return@withContext Result.success()
    }

    private fun updateWidget(context: Context) {
        Timber.d("updateWidget called")
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        intent.action = "com.example.wierdesol.WIDGET_UPDATE"
        context.sendBroadcast(intent)
    }
}