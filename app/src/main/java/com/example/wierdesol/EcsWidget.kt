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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import java.lang.reflect.Method

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
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Create an Intent to launch MainActivity when the widget is clicked
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(views.getLayoutId(), pendingIntent)

        // Set the loading text
        views.setTextViewText(R.id.widget_ecs_value, context.getString(R.string.loading))

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)

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
                    val ecsValue = getEcsValue(data)
                    updateWidgetWithEcsValue(context, appWidgetManager, appWidgetId, ecsValue)
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

    private fun getEcsValue(data: ResolResponse): String {
        val correctPacket = data.headersets[0].packets.getOrNull(1)
        if (correctPacket != null) {
            val sensorValues = correctPacket.fieldValues.associateBy { it.fieldIndex }
            val ecsValue = sensorValues[4]?.value ?: "N/A"
            return "$ecsValue°C"
        }
        return "N/A"
    }

    private fun updateWidgetWithEcsValue(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        ecsValue: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.widget_ecs_value, ecsValue)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateWidgetWithError(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.widget_ecs_value, context.getString(R.string.error_retrieving_data))
        appWidgetManager.updateAppWidget(appWidgetId, views)
        scheduleWidgetUpdate(context)
    }

    private fun scheduleWidgetUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val refreshRateMinutes = sharedPreferences.getString("refresh_rate", "10")?.toLongOrNull() ?: 10
        val refreshRateMillis = refreshRateMinutes * 60 * 1000

        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + refreshRateMillis,
            refreshRateMillis,
            pendingIntent
        )
    }

    companion object {
        fun updateWidget(context: Context) {
            val intent = Intent(context, WidgetUpdateReceiver::class.java)
            context.sendBroadcast(intent)
        }
    }

    fun RemoteViews.getLayoutId(): Int {
        val clazz = RemoteViews::class.java
        val method: Method = clazz.getDeclaredMethod("getLayoutId")
        method.isAccessible = true
        return method.invoke(this) as Int
    }
}

class WidgetUpdateReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EcsWidget.updateWidget(context)
    }
}