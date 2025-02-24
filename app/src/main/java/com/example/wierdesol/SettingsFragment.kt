package com.example.wierdesol

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

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
        }
    }

    private fun updateRefreshRateSummary() {
        val refreshRatePreference: EditTextPreference? = findPreference("refresh_rate")
        val sharedPreferences = preferenceManager.sharedPreferences
        val refreshRate = sharedPreferences?.getString("refresh_rate", "10")
        refreshRatePreference?.summary = refreshRate
    }
}