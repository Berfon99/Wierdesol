package com.example.wierdesol

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import timber.log.Timber

object NetworkUtils {

    /**
     * Checks if the device has an active network connection
     * @param context Application context
     * @return true if connected, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * Checks if the device is connected to WiFi
     * @param context Application context
     * @return true if connected to WiFi, false otherwise
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
        }
    }

    /**
     * Checks if data fetching should proceed based on network settings
     * @param context Application context
     * @return true if data fetching should proceed, false if it should be skipped
     */
    fun shouldFetchData(context: Context): Boolean {
        val sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val wifiOnlyEnabled = sharedPreferences.getBoolean("wifi_only", false)

        // If WiFi only setting is enabled, check for WiFi connection
        if (wifiOnlyEnabled) {
            val isWifi = isWifiConnected(context)
            Timber.d("WiFi Only mode enabled. WiFi connected: $isWifi")
            return isWifi
        }

        // Otherwise, check for any network connection
        val isNetworkAvailable = isNetworkAvailable(context)
        Timber.d("Network available: $isNetworkAvailable")
        return isNetworkAvailable
    }
}