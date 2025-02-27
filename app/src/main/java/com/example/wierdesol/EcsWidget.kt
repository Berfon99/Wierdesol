package com.example.wierdesol

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
        // Schedule periodic updates
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
        triggerImmediateUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Timber.d("onEnabled called")
        // Trigger an immediate update when the first widget is added
        triggerImmediateUpdate(context)
        // Schedule periodic updates
        scheduleWidgetUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Timber.d("onDisabled called")
        // Cancel updates when the last widget is removed
        cancelWidgetUpdates(context)
    }

    private fun triggerImmediateUpdate(context: Context) {
        Timber.d("triggerImmediateUpdate called")
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        intent.action = "com.example.wierdesol.WIDGET_UPDATE"
        context.sendBroadcast(intent)
    }

// In EcsWidget.kt - Improve the widget update scheduling

    private fun scheduleWidgetUpdate(context: Context) {
        Timber.d("scheduleWidgetUpdate called in EcsWidget")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        intent.action = "com.example.wierdesol.WIDGET_UPDATE"

        // Attach context as tag for error handling
        intent.putExtra("context_package", context.packageName)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Get refresh rate from preferences
        val sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val refreshRateMinutes = sharedPreferences.getString("refresh_rate", "10")?.toLongOrNull() ?: 10
        val refreshRateMillis = refreshRateMinutes * 60 * 1000

        try {
            // For Android 12 and above (API 31+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + refreshRateMillis,
                        pendingIntent
                    )
                } else {
                    // Fall back to inexact alarm if permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + refreshRateMillis,
                        pendingIntent
                    )
                    Timber.w("Using inexact alarm - permission not granted for exact alarms")
                }
            }
            // For Android 6.0 to 11
            else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + refreshRateMillis,
                    pendingIntent
                )
            }
            // For older Android versions
            else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + refreshRateMillis,
                    pendingIntent
                )
            }

            Timber.d("Alarm scheduled for widget update in $refreshRateMinutes minutes")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException when scheduling alarm")
            // Fall back to inexact alarm
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + refreshRateMillis,
                pendingIntent
            )
            Timber.d("Falling back to inexact alarm due to security exception")
        }
    }

    private fun cancelWidgetUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        intent.action = "com.example.wierdesol.WIDGET_UPDATE"

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Timber.d("Widget updates canceled")
        }
    }
}