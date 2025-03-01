package com.example.wierdesol

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import timber.log.Timber

/**
 * Utility class to handle widget update scheduling in a centralized way.
 * This eliminates code duplication between EcsWidget and WidgetUpdateReceiver.
 */
object WidgetScheduler {

    private const val WIDGET_UPDATE_ACTION = "com.example.wierdesol.WIDGET_UPDATE"

    /**
     * Schedule the next widget update based on user preferences
     */
    fun scheduleUpdate(context: Context) {
        Timber.d("scheduleUpdate called")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WIDGET_UPDATE_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Get refresh rate from preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val refreshRateMinutes = sharedPreferences.getString("refresh_rate", "10")?.toLongOrNull() ?: 10
        val refreshRateMillis = refreshRateMinutes * 60 * 1000

        // Schedule new alarm
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12 (API 31) and higher
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
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0 to 11
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + refreshRateMillis,
                    pendingIntent
                )
            } else {
                // Pre-Android 6.0
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

    /**
     * Cancel any previously scheduled updates
     */
    fun cancelUpdates(context: Context) {
        Timber.d("cancelUpdates called")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WIDGET_UPDATE_ACTION
        }

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

    /**
     * Trigger an immediate widget update via broadcast
     */
    fun triggerImmediateUpdate(context: Context) {
        Timber.d("triggerImmediateUpdate called")
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WIDGET_UPDATE_ACTION
        }
        context.sendBroadcast(intent)
    }
}