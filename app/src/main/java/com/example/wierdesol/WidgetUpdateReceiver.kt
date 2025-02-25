package com.example.wierdesol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName

class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.xc.air3upgrader.PREFERENCE_CHANGED") {
            // Handle preference change
            EcsWidget.updateWidget(context)
        } else {
            // Handle regular update
            EcsWidget.updateWidget(context)
        }
    }
}