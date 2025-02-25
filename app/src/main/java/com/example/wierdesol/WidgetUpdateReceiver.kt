package com.example.wierdesol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import timber.log.Timber

class WidgetUpdateReceiver : BroadcastReceiver() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("WidgetUpdateReceiver onReceive called")
        // Initialize SharedPreferences here
        sharedPreferences = context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
        if (intent.action == "com.example.wierdesol.PREFERENCE_CHANGED") {
            // Handle preference change
            EcsWidget.updateWidget(context)
        } else if (intent.action == "com.example.wierdesol.WIDGET_UPDATE") {
            // Handle regular update
            EcsWidget.updateWidget(context)
        }
    }
}