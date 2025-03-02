package com.example.wierdesol

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import timber.log.Timber

class EcsWidget : AppWidgetProvider() {

    companion object {
        const val PREF_ECS_TEMPERATURE = "pref_ecs_temperature"
        const val PREF_CAPTEURS_TEMPERATURE = "pref_capteurs_temperature"
        const val PREF_PISCINE_TEMPERATURE = "pref_piscine_temperature"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("onUpdate called")
        // Trigger an immediate update for each widget instance
        for (appWidgetId in appWidgetIds) {
            WidgetScheduler.triggerImmediateUpdate(context)
        }
        // Schedule periodic updates
        WidgetScheduler.scheduleUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Timber.d("onAppWidgetOptionsChanged called")
        WidgetScheduler.triggerImmediateUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Timber.d("onEnabled called")
        // Trigger an immediate update when the first widget is added
        WidgetScheduler.triggerImmediateUpdate(context)
        // Schedule periodic updates
        WidgetScheduler.scheduleUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Timber.d("onDisabled called")
        // Cancel updates when the last widget is removed
        WidgetScheduler.cancelUpdates(context)
    }
}