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

class WidgetUpdateReceiver : BroadcastReceiver() {

    private lateinit var sharedPreferences: SharedPreferences
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("WidgetUpdateReceiver onReceive called")
        // Initialize SharedPreferences here
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (intent.action == "com.example.wierdesol.PREFERENCE_CHANGED") {
            // Handle preference change
            updateAppWidget(context)
        } else if (intent.action == "com.example.wierdesol.WIDGET_UPDATE") {
            // Handle regular update
            updateAppWidget(context)
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
        coroutineScope.launch {
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
        Timber.d("fetchData called")
        RetrofitClient.instance.getLiveData().enqueue(object : Callback<ResolResponse> {
            override fun onResponse(call: Call<ResolResponse>, response: Response<ResolResponse>) {
                Timber.d("response.isSuccessful: ${response.isSuccessful}")
                Timber.d("response.code(): ${response.code()}")
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
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val errorMsg = context.getString(R.string.error_retrieving_data)

        views.setTextViewText(R.id.widget_ecs_value, errorMsg)
        views.setTextViewText(R.id.widget_capteurs_value, errorMsg)

        val backgroundColor = ContextCompat.getColor(context, R.color.red)
        views.setInt(R.id.ecs_section, "setBackgroundColor", backgroundColor)
        views.setInt(R.id.capteurs_section, "setBackgroundColor", backgroundColor)

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
