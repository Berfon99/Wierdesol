package com.example.wierdesol

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import timber.log.Timber

class EcsWidget : AppWidgetProvider() {

    companion object {
        const val PREF_ECS_TEMPERATURE = "pref_ecs_temperature"
        const val PREF_CAPTEURS_TEMPERATURE = "pref_capteurs_temperature"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("onUpdate called")
        // Trigger an immediate update for each widget instance
        for (appWidgetId in appWidgetIds) {
            triggerImmediateUpdate(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Timber.d("onAppWidgetOptionsChanged called")
        // No need to update the widget here anymore
        triggerImmediateUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Timber.d("onEnabled called")
        // Trigger an immediate update when the first widget is added
        triggerImmediateUpdate(context)
    }

    private fun triggerImmediateUpdate(context: Context) {
        Timber.d("triggerImmediateUpdate called")
        val updateWorkRequest = OneTimeWorkRequest.Builder(WidgetUpdateWorker::class.java).build()
        WorkManager.getInstance(context).enqueue(updateWorkRequest)
    }
}