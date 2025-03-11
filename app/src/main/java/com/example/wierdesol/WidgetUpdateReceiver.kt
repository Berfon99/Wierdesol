package com.example.wierdesol

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("WidgetUpdateReceiver onReceive called with action: ${intent.action}")

        // Initialize SharedPreferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Get AppWidgetManager and widget IDs upfront
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, EcsWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        // Check if we have any widgets to update
        if (appWidgetIds.isEmpty()) {
            Timber.d("No widgets found to update")
            return
        }

        Timber.d("Found ${appWidgetIds.size} widgets to update")

        when (intent.action) {
            "com.example.wierdesol.PREFERENCE_CHANGED" -> {
                Timber.d("Preferences changed, rescheduling updates")
                // Cancel existing alarms and reschedule with new settings
                WidgetScheduler.cancelUpdates(context)
                WidgetScheduler.scheduleUpdate(context)
                // Also update widget immediately with current data
                updateWidgetsWithStoredData(context, appWidgetManager, appWidgetIds)
            }
            "com.example.wierdesol.WIDGET_UPDATE" -> {
                Timber.d("Widget update requested")
                // Update widgets immediately with stored data
                updateWidgetsWithStoredData(context, appWidgetManager, appWidgetIds)
                // Schedule next update
                WidgetScheduler.scheduleUpdate(context)

                // Additionally fetch fresh data if available
                fetchDataForWidgets(context, appWidgetManager, appWidgetIds)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Timber.d("Boot completed, updating widgets and scheduling updates")
                updateWidgetsWithStoredData(context, appWidgetManager, appWidgetIds)
                WidgetScheduler.scheduleUpdate(context)
            }
        }
    }

    private fun updateWidgetsWithStoredData(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("updateWidgetsWithStoredData called")

        // Get stored temperature values
        val ecsTemperature = sharedPreferences.getString(EcsWidget.PREF_ECS_TEMPERATURE, "N/A") ?: "N/A"
        val capteursTemperature = sharedPreferences.getString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, "N/A") ?: "N/A"
        val piscineTemperature = sharedPreferences.getString(EcsWidget.PREF_PISCINE_TEMPERATURE, "N/A") ?: "N/A"

        // Add this line to get filtration status
        val filtrationStatus = sharedPreferences.getFloat(EcsWidget.PREF_FILTRATION_STATUS, 0.0f).toDouble()

        Timber.d("Retrieved from SharedPreferences: ECS=$ecsTemperature, Capteurs=$capteursTemperature, Piscine=$piscineTemperature")

        // Update each widget with stored data
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, getLayoutIdForWidget(context, appWidgetManager, appWidgetId))

            // Set temperature values
            views.setTextViewText(R.id.widget_ecs_value, ecsTemperature)
            views.setTextViewText(R.id.widget_capteurs_value, capteursTemperature)
            views.setTextViewText(R.id.widget_piscine_value, piscineTemperature)


            // Set background colors based on temperature values
            val ecsTemp = ecsTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0
            val capteursTemp = capteursTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0
            val piscineTemp = piscineTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0

            views.setInt(R.id.ecs_section, "setBackgroundColor", getEcsBackgroundColor(ecsTemp, context))
            views.setInt(R.id.capteurs_section, "setBackgroundColor", getCapteursBackgroundColor(capteursTemp, context))
            views.setInt(
                R.id.piscine_section,
                "setBackgroundColor",
                getPiscineBackgroundColor(piscineTemp, filtrationStatus, context)
            )

            // Set click intent to open MainActivity
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Timber.d("Widget $appWidgetId updated with stored data")
        }
    }

    private fun fetchDataForWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("fetchDataForWidgets called")
        // Check if we should fetch data based on network settings
        if (!NetworkUtils.shouldFetchData(context)) {
            Timber.d("Skipping widget data fetch due to network settings (WiFi Only)")
            return
        }
        RetrofitClient.instance.getLiveData().enqueue(object : Callback<ResolResponse> {
            override fun onResponse(call: Call<ResolResponse>, response: Response<ResolResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    if (data.headersets.isNotEmpty() && data.headersets[0].packets.size > 1) {
                        val sensorValues = extractSensorValues(data)

                        // Save values to SharedPreferences
                        val ecsValue = sensorValues["ecs"]?.first ?: "N/A"
                        val capteursValue = sensorValues["capteurs"]?.first ?: "N/A"
                        val piscineValue = sensorValues["piscine"]?.first ?: "N/A"


                        sharedPreferences.edit()
                            .putString(EcsWidget.PREF_ECS_TEMPERATURE, ecsValue)
                            .putString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, capteursValue)
                            .putString(EcsWidget.PREF_PISCINE_TEMPERATURE, piscineValue)
                            .apply()

                        // Update widgets with new data
                        for (appWidgetId in appWidgetIds) {
                            updateWidgetWithValues(context, appWidgetManager, appWidgetId, sensorValues)
                        }

                        Timber.d("Widgets updated with fresh data")
                    } else {
                        Timber.e("Received empty or incomplete data")
                    }
                } else {
                    Timber.e("Error fetching data: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ResolResponse>, t: Throwable) {
                Timber.e(t, "Connection failed when fetching data for widgets")
            }
        })
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

            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
            Timber.d("PendingIntent set on widget_layout")

            val lastEcsTemperature = sharedPreferences.getString(EcsWidget.PREF_ECS_TEMPERATURE, "N/A") ?: "N/A"
            val lastCapteursTemperature = sharedPreferences.getString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, "N/A") ?: "N/A"
            val lastPiscineTemperature = sharedPreferences.getString(EcsWidget.PREF_PISCINE_TEMPERATURE, "N/A") ?: "N/A"
            views.setTextViewText(R.id.widget_ecs_value, lastEcsTemperature)
            views.setTextViewText(R.id.widget_capteurs_value, lastCapteursTemperature)
            views.setTextViewText(R.id.widget_piscine_value, lastPiscineTemperature)
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
            fetchData(context) { data ->
                if (data != null) {
                    val sensorValues = extractSensorValues(data)
                    updateWidgetWithValues(context, appWidgetManager, appWidgetId, sensorValues)
                } else {
                    updateWidgetWithError(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    private fun fetchData(context: Context, callback: (ResolResponse?) -> Unit) {
        Timber.d("fetchData called in WidgetUpdateReceiver")
        // Check if we should fetch data based on network settings
        if (!NetworkUtils.shouldFetchData(context)) {
            Timber.d("Skipping data fetch due to network settings (WiFi Only)")
            callback(null)
            return
        }
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

            val piscineValue = fieldValues[10]?.value ?: "N/A"
            val piscineTemperature = piscineValue.toDoubleOrNull() ?: 0.0
            sensorValues["piscine"] = Pair("$piscineValue°C", piscineTemperature)

            // Extract filtration value (index 45)
            val filtrationValue = fieldValues[45]?.value ?: "0"
            val filtrationStatus = filtrationValue.toDoubleOrNull() ?: 0.0
            sensorValues["filtration"] = Pair(filtrationValue, filtrationStatus)
        }

        return sensorValues
    }
    private fun getEcsBackgroundColor(temperature: Double, context: Context): Int {
        return when {
            temperature > 38 -> ContextCompat.getColor(context, R.color.green)
            temperature >= 36 -> ContextCompat.getColor(context, R.color.orange)
            temperature < 36 -> ContextCompat.getColor(context, R.color.red)
            else -> ContextCompat.getColor(context, R.color.grey)
        }
    }

    private fun getCapteursBackgroundColor(temperature: Double, context: Context): Int {
        return when {
            temperature < 100 -> ContextCompat.getColor(context, R.color.green)
            else -> ContextCompat.getColor(context, R.color.red)
        }
    }

    private fun getPiscineBackgroundColor(temperature: Double, filtrationStatus: Double, context: Context): Int {
        return when {
            filtrationStatus != 100.0 -> ContextCompat.getColor(context, R.color.grey)
            temperature > 26 -> ContextCompat.getColor(context, R.color.gold)
            temperature > 23 -> ContextCompat.getColor(context, R.color.green)
            temperature > 20 -> ContextCompat.getColor(context, R.color.lightblue)
            else -> ContextCompat.getColor(context, R.color.blue)
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

        val piscineTemperature = sensorValues["piscine"]?.first ?: "N/A"
        val piscineTemperatureValue = sensorValues["piscine"]?.second ?: 0.0
        views.setTextViewText(R.id.widget_piscine_value, piscineTemperature)

        // Get filtration status
        val filtrationStatus = sensorValues["filtration"]?.second ?: 0.0

        Timber.d("Saving to SharedPreferences: ECS=$ecsTemperature, Capteurs=$capteursTemperature, Piscine=$piscineTemperature")
        sharedPreferences.edit()
            .putString(EcsWidget.PREF_ECS_TEMPERATURE, ecsTemperature)
            .putString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, capteursTemperature)
            .putString(EcsWidget.PREF_PISCINE_TEMPERATURE, piscineTemperature)
            .putFloat(EcsWidget.PREF_FILTRATION_STATUS, filtrationStatus.toFloat())
            .apply()

        val ecsBackgroundColor = getEcsBackgroundColor(ecsTemperatureValue, context)
        views.setInt(R.id.ecs_section, "setBackgroundColor", ecsBackgroundColor)

        val capteursBackgroundColor = getCapteursBackgroundColor(capteursTemperatureValue, context)
        views.setInt(R.id.capteurs_section, "setBackgroundColor", capteursBackgroundColor)

        val piscineBackgroundColor = getPiscineBackgroundColor(piscineTemperatureValue, filtrationStatus, context)
        views.setInt(R.id.piscine_section, "setBackgroundColor", piscineBackgroundColor)

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
        val lastPiscineTemperature = sharedPreferences.getString(EcsWidget.PREF_PISCINE_TEMPERATURE, "N/A") ?: "N/A"

        views.setTextViewText(R.id.widget_ecs_value, lastEcsTemperature)
        views.setTextViewText(R.id.widget_capteurs_value, lastCapteursTemperature)
        views.setTextViewText(R.id.widget_piscine_value, lastPiscineTemperature)

        // Keep the last colors or use neutral colors instead of error red
        val ecsTemp = lastEcsTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0
        val capteursTemp = lastCapteursTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0
        val piscineTemp = lastPiscineTemperature.replace("°C", "").toDoubleOrNull() ?: 0.0

        // Use 0 for filtration status on error (will use grey)
        val filtrationStatus = 0.0

        views.setInt(R.id.ecs_section, "setBackgroundColor", getEcsBackgroundColor(ecsTemp, context))
        views.setInt(R.id.capteurs_section, "setBackgroundColor", getCapteursBackgroundColor(capteursTemp, context))
        views.setInt(R.id.piscine_section, "setBackgroundColor", getPiscineBackgroundColor(piscineTemp, filtrationStatus, context))

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