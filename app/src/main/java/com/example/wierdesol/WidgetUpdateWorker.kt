package com.example.wierdesol

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import timber.log.Timber

class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Timber.d("WidgetUpdateWorker doWork called")
        // Call the function to update the widget
        updateWidget(applicationContext)
        return Result.success()
    }
    private fun updateWidget(context: Context) {
        Timber.d("updateWidget called")
        val intent = android.content.Intent(context, WidgetUpdateReceiver::class.java)
        intent.action = "com.example.wierdesol.WIDGET_UPDATE"
        context.sendBroadcast(intent)
    }
}