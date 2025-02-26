package com.example.wierdesol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed received")

            // Check if any widgets are active
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, EcsWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isNotEmpty()) {
                Timber.d("Widgets found after reboot, restarting updates")
                // There are widgets, restart the update schedule
                val updateIntent = Intent(context, EcsWidget::class.java)
                updateIntent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                context.sendBroadcast(updateIntent)
            }
        }
    }
}