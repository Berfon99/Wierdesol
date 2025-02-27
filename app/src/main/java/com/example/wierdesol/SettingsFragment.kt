package com.example.wierdesol

import android.content.Intent // Add this import statement
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import timber.log.Timber

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Set the initial summary
        updateRefreshRateSummary()
    }

    override fun onResume() {
        super.onResume()
        // Register the listener
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Unregister the listener
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "refresh_rate") {
            updateRefreshRateSummary()

            // Fix: Broadcast the preference change with the correct action
            val intent = Intent("com.example.wierdesol.PREFERENCE_CHANGED")
            requireContext().sendBroadcast(intent)

            Timber.d("Preferences changed: refresh_rate = ${sharedPreferences?.getString(key, "10")}")
        }
    }

    private fun updateRefreshRateSummary() {
        val refreshRatePreference: EditTextPreference? = findPreference("refresh_rate")
        val sharedPreferences = preferenceManager.sharedPreferences
        val refreshRate = sharedPreferences?.getString("refresh_rate", "10")
        refreshRatePreference?.summary = refreshRate
    }
}