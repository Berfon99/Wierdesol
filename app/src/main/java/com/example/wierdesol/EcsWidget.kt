package com.example.wierdesol

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.preference.PreferenceManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class EcsWidget : AppWidgetProvider() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var sharedPreferences: SharedPreferences

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleWidgetUpdate(context)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Timber.d("updateAppWidget called for appWidgetId: $appWidgetId")

        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Create an Intent to launch the app
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Set the PendingIntent on the Button
        views.setOnClickPendingIntent(R.id.widget_click_button, pendingIntent)
        Timber.d("PendingIntent set on widget_click_button")

        // Set the loading text
        views.setTextViewText(R.id.widget_ecs_value, context.getString(R.string.loading))

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Timber.d("appWidgetManager.updateAppWidget called")

        // Fetch the data and update the widget
        fetchDataAndUpdateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun fetchDataAndUpdateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        coroutineScope.launch {
            fetchData { data ->
                if (data != null) {
                    val (ecsValue, temperature) = getEcsValue(data)
                    updateWidgetWithEcsValue(context, appWidgetManager, appWidgetId, ecsValue, temperature)
                } else {
                    updateWidgetWithError(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    private fun fetchData(callback: (ResolResponse?) -> Unit) {
        RetrofitClient.instance.getLiveData().enqueue(object : Callback<ResolResponse> {
            override fun onResponse(call: Call<ResolResponse>, response: Response<ResolResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()
                    Timber.d("Data received: $data")
                    callback(data)
                } else {
                    Timber.e("Error: ${response.code()}")
                    callback(null)
                }
            }

            override fun onFailure(call: Call<ResolResponse>, t: Throwable) {
                Timber.e(t, "Connection failed")
                callback(null)
            }
        })
    }

    private fun getEcsValue(data: ResolResponse): Pair<String, Double> {
        val correctPacket = data.headersets[0].packets.getOrNull(1)
        if (correctPacket != null) {
            val sensorValues = correctPacket.fieldValues.associateBy { it.fieldIndex }
            val ecsValue = sensorValues[4]?.value ?: "N/A"
            val temperature = ecsValue.toDoubleOrNull() ?: 0.0
            return Pair("$ecsValue°C", temperature)
        }
        return Pair("N/A", 0.0)
    }

    private fun getBackgroundColor(temperature: Double, context: Context): Int {
        return when {
            temperature > 41 -> ContextCompat.getColor(context, R.color.green)
            temperature >= 37 -> ContextCompat.getColor(context, R.color.orange)
            else -> ContextCompat.getColor(context, R.color.black)
        }
    }

    private fun updateWidgetWithEcsValue(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        ecsValue: String,
        temperature: Double
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.widget_ecs_value, ecsValue)
        val backgroundColor = getBackgroundColor(temperature, context)
        views.setInt(R.id.widget_layout, "setBackgroundColor", backgroundColor)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateWidgetWithError(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.widget_ecs_value, context.getString(R.string.error_retrieving_data))
        val backgroundColor = ContextCompat.getColor(context, R.color.black)
        views.setInt(R.id.widget_layout, "setBackgroundColor", backgroundColor)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        scheduleWidgetUpdate(context)
    }

    private fun scheduleWidgetUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cancel any existing alarms
        alarmManager.cancel(pendingIntent)

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
    companion object {
        fun updateWidget(context: Context) {
            val intent = Intent(context, EcsWidget::class.java)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, EcsWidget::class.java))
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            for (appWidgetId in ids) {
                val widget = EcsWidget()
                widget.updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}