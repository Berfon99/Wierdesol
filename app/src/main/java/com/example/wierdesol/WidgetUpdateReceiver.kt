package com.example.wierdesol

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.lang.reflect.Method
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequest
import java.util.concurrent.TimeUnit
import android.app.AlarmManager


class WidgetUpdateReceiver : BroadcastReceiver() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("WidgetUpdateReceiver onReceive called with action: ${intent.action}")

        // Initialize SharedPreferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        when (intent.action) {
            "com.example.wierdesol.PREFERENCE_CHANGED" -> {
                Timber.d("Preferences changed, rescheduling updates")
                // Cancel existing alarms and reschedule with new settings
                cancelWidgetUpdates(context)
                scheduleWidgetUpdate(context)
                // Also update widget immediately with current data
                updateAppWidget(context)
            }
            "com.example.wierdesol.WIDGET_UPDATE" -> {
                Timber.d("Widget update requested")
                // Update widget with current data
                updateAppWidget(context)
                // Reschedule for next update to maintain the cycle
                scheduleWidgetUpdate(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Timber.d("Boot completed, checking for widgets")
                // Check if any widgets are active and restart updates if needed
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, EcsWidget::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isNotEmpty()) {
                    Timber.d("Widgets found after reboot, restarting updates")
                    updateAppWidget(context)
                    scheduleWidgetUpdate(context)
                }
            }
        }
    }

    private fun scheduleWidgetUpdate(context: Context) {
        Timber.d("scheduleWidgetUpdate called")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        intent.action = "com.example.wierdesol.WIDGET_UPDATE"

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cancel any existing alarms
        alarmManager.cancel(pendingIntent)

        // Get refresh rate from preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val refreshRateMinutes = sharedPreferences.getString("refresh_rate", "10")?.toLongOrNull() ?: 10
        val refreshRateMillis = refreshRateMinutes * 60 * 1000

        // Schedule new alarm
        val triggerTime = System.currentTimeMillis() + refreshRateMillis

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12 (API 31) and higher
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fall back to inexact alarm if permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Timber.w("Cannot schedule exact alarms - permission not granted. Using inexact alarm instead.")
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0 to 11
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // Pre-Android 6.0
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Timber.d("Alarm scheduled for widget update in $refreshRateMinutes minutes")
        } catch (e: SecurityException) {
            // Handle case where we don't have permission
            Timber.e(e, "SecurityException when scheduling alarm - permission may be needed")
            // Fall back to inexact alarm
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Timber.d("Falling back to inexact alarm for widget update")
        }
    }
    private fun updateAppWidget(context: Context) {
        Timber.d("updateAppWidget called")
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, EcsWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            Timber.d("updateAppWidget called for appWidgetId: $appWidgetId")

            val layoutId = getLayoutIdForWidget(context, appWidgetManager, appWidgetId)
            val views = RemoteViews(context.packageName, layoutId)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            views.setOnClickPendingIntent(views.getLayoutId(), pendingIntent)
            Timber.d("PendingIntent set on widget_layout")

            val lastEcsTemperature = sharedPreferences.getString(EcsWidget.PREF_ECS_TEMPERATURE, "N/A") ?: "N/A"
            val lastCapteursTemperature = sharedPreferences.getString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, "N/A") ?: "N/A"
            views.setTextViewText(R.id.widget_ecs_value, lastEcsTemperature)
            views.setTextViewText(R.id.widget_capteurs_value, lastCapteursTemperature)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Timber.d("appWidgetManager.updateAppWidget called")

            fetchDataAndUpdateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun fetchDataAndUpdateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Timber.d("fetchDataAndUpdateWidget called")
        CoroutineScope(Dispatchers.Main).launch {
            fetchData { data ->
                if (data != null) {
                    val sensorValues = extractSensorValues(data)
                    updateWidgetWithValues(context, appWidgetManager, appWidgetId, sensorValues)
                } else {
                    updateWidgetWithError(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    private fun fetchData(callback: (ResolResponse?) -> Unit) {
        Timber.d("fetchData called in WidgetUpdateReceiver")

        RetrofitClient.instance.getLiveData().enqueue(object : Callback<ResolResponse> {
            override fun onResponse(call: Call<ResolResponse>, response: Response<ResolResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()
                    if (data != null && data.headersets.isNotEmpty() &&
                        data.headersets[0].packets.size > 1) {
                        Timber.d("Valid data received in WidgetUpdateReceiver")
                        callback(data)
                    } else {
                        Timber.e("Received empty or incomplete data in WidgetUpdateReceiver")
                        callback(null)
                    }
                } else {
                    Timber.e("Error in WidgetUpdateReceiver: ${response.code()}")
                    callback(null)
                }
            }

            override fun onFailure(call: Call<ResolResponse>, t: Throwable) {
                Timber.e(t, "Connection failed in WidgetUpdateReceiver")
                callback(null)
            }
        })
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
            Timber.d("Existing widget updates canceled")
        }
    }


    private fun extractSensorValues(data: ResolResponse): Map<String, Pair<String, Double>> {
        Timber.d("extractSensorValues called")
        val sensorValues = mutableMapOf<String, Pair<String, Double>>()

        val correctPacket = data.headersets[0].packets.getOrNull(1)
        Timber.d("correctPacket: $correctPacket")
        if (correctPacket != null) {
            val fieldValues = correctPacket.fieldValues.associateBy { it.fieldIndex }
            Timber.d("fieldValues: $fieldValues")

            val ecsValue = fieldValues[4]?.value ?: "N/A"
            val ecsTemperature = ecsValue.toDoubleOrNull() ?: 0.0
            sensorValues["ecs"] = Pair("$ecsValue°C", ecsTemperature)

            val capteursValue = fieldValues[0]?.value ?: "N/A"
            val capteursTemperature = capteursValue.toDoubleOrNull() ?: 0.0
            sensorValues["capteurs"] = Pair("$capteursValue°C", capteursTemperature)
        }

        return sensorValues
    }

    private fun getEcsBackgroundColor(temperature: Double, context: Context): Int {
        return when {
            temperature > 41 -> ContextCompat.getColor(context, R.color.green)
            temperature >= 37 -> ContextCompat.getColor(context, R.color.orange)
            else -> ContextCompat.getColor(context, R.color.black)
        }
    }

    private fun getCapteursBackgroundColor(temperature: Double, context: Context): Int {
        return when {
            temperature < 100 -> ContextCompat.getColor(context, R.color.green)
            else -> ContextCompat.getColor(context, R.color.black)
        }
    }

    private fun updateWidgetWithValues(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        sensorValues: Map<String, Pair<String, Double>>
    ) {
        Timber.d("updateWidgetWithValues called")
        Timber.d("sensorValues: $sensorValues")
        val views = RemoteViews(context.packageName, getLayoutIdForWidget(context, appWidgetManager, appWidgetId))

        val ecsTemperature = sensorValues["ecs"]?.first ?: "N/A"
        val ecsTemperatureValue = sensorValues["ecs"]?.second ?: 0.0
        views.setTextViewText(R.id.widget_ecs_value, ecsTemperature)

        val capteursTemperature = sensorValues["capteurs"]?.first ?: "N/A"
        val capteursTemperatureValue = sensorValues["capteurs"]?.second ?: 0.0
        views.setTextViewText(R.id.widget_capteurs_value, capteursTemperature)

        Timber.d("Saving to SharedPreferences: ECS=$ecsTemperature, Capteurs=$capteursTemperature")
        sharedPreferences.edit()
            .putString(EcsWidget.PREF_ECS_TEMPERATURE, ecsTemperature)
            .putString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, capteursTemperature)
            .apply()

        val ecsBackgroundColor = getEcsBackgroundColor(ecsTemperatureValue, context)
        views.setInt(R.id.ecs_section, "setBackgroundColor", ecsBackgroundColor)

        val capteursBackgroundColor = getCapteursBackgroundColor(capteursTemperatureValue, context)
        views.setInt(R.id.capteurs_section, "setBackgroundColor", capteursBackgroundColor)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateWidgetWithError(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, getLayoutIdForWidget(context, appWidgetManager, appWidgetId))

        // Use cached values instead of showing error
        val lastEcsTemperature = sharedPreferences.getString(EcsWidget.PREF_ECS_TEMPERATURE, "N/A") ?: "N/A"
        val lastCapteursTemperature = sharedPreferences.getString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, "N/A") ?: "N/A"

        views.setTextViewText(R.id.widget_ecs_value, lastEcsTemperature)
        views.setTextViewText(R.id.widget_capteurs_value, lastCapteursTemperature)

        // Optional: Add some indicator that data might be stale
        // views.setTextViewText(R.id.update_time, "Last update: ${getTimeAgo()}")

        // Keep the last colors or use neutral colors instead of error red
        val ecsTemp = lastEcsTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0
        val capteursTemp = lastCapteursTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0

        views.setInt(R.id.ecs_section, "setBackgroundColor", getEcsBackgroundColor(ecsTemp, context))
        views.setInt(R.id.capteurs_section, "setBackgroundColor", getCapteursBackgroundColor(capteursTemp, context))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getLayoutIdForWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ): Int {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        Timber.d("minWidth: $minWidth, minHeight: $minHeight")

        // Convert dp to pixels for comparison
        val thresholdDp = 120
        val thresholdPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            thresholdDp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
        Timber.d("thresholdPx: $thresholdPx")

        return if (minWidth > minHeight) {
            Timber.d("Using horizontal layout")
            R.layout.widget_layout_horizontal
        } else {
            Timber.d("Using vertical layout")
            R.layout.widget_layout_vertical
        }
    }

    fun RemoteViews.getLayoutId(): Int {
        val clazz = RemoteViews::class.java
        val method: Method = clazz.getDeclaredMethod("getLayoutId")
        method.isAccessible = true
        return method.invoke(this) as Int
    }
}
