package com.example.wierdesol

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.preference.PreferenceManager
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
        // No need to update the widget here anymore
        scheduleWidgetUpdate(context)
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
    }

    private fun scheduleWidgetUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        intent.action = "com.example.wierdesol.WIDGET_UPDATE" // Use the new action here
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cancel any existing alarms
        alarmManager.cancel(pendingIntent)

        val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val refreshRateMinutes = sharedPreferences.getString("refresh_rate", "10")?.toLongOrNull() ?: 10
        val refreshRateMillis = refreshRateMinutes * 60 * 1000

        Timber.d("Scheduling widget update every $refreshRateMinutes minutes")

        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + refreshRateMillis,
            refreshRateMillis,
            pendingIntent
        )
    }
}