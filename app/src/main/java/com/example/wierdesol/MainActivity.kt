package com.example.wierdesol

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wierdesol.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var leftRecyclerView: RecyclerView
    private lateinit var rightRecyclerView: RecyclerView
    private lateinit var leftSensorAdapter: SensorAdapter
    private lateinit var rightSensorAdapter: SensorAdapter
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var statusRecyclerView: RecyclerView
    private lateinit var statusAdapter: StatusAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        leftRecyclerView = binding.leftRecyclerView
        rightRecyclerView = binding.rightRecyclerView
        statusRecyclerView = binding.statusRecyclerView  // Add this line

        // Initialize Timber
        val isDebug = true
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize the RecyclerViews
        leftRecyclerView.layoutManager = LinearLayoutManager(this)
        rightRecyclerView.layoutManager = LinearLayoutManager(this)
        statusRecyclerView.layoutManager = LinearLayoutManager(this)  // Add this line

        leftSensorAdapter = SensorAdapter(emptyList())
        rightSensorAdapter = SensorAdapter(emptyList())
        statusAdapter = StatusAdapter(emptyList())  // Add this line

        leftRecyclerView.adapter = leftSensorAdapter
        rightRecyclerView.adapter = rightSensorAdapter
        statusRecyclerView.adapter = statusAdapter  // Add this line

        // Initialize the refresh button
        binding.refreshButton.setOnClickListener {
            fetchDataAndRefresh()
        }

        // Start fetching data periodically
        fetchDataPeriodically()

        // Initialize the link and make it clickable
        setupLinkTextView()
    }

    private fun updateStatusList(statusList: List<StatusIndicator>) {
        statusAdapter.statusIndicators = statusList
        statusAdapter.notifyDataSetChanged()
    }

    private fun analyzeData(data: ResolResponse?) {
        if (data == null || data.headersets.isEmpty()) {
            // Handle the case where data is null or headersets is empty
            val errorSensor = Sensor(getString(R.string.data_retrieval_error), "")
            updateSensorLists(listOf(errorSensor), emptyList())
            updateStatusList(emptyList())
            return
        }

        // Log the size of headersets
        Timber.d("headersets size: ${data.headersets.size}")

        // Log the size of packets in the first headerset
        if (data.headersets.isNotEmpty()) {
            Timber.d("packets size in first headerset: ${data.headersets[0].packets.size}")

            // Log the size of fieldValues in each packet
            data.headersets[0].packets.forEachIndexed { index, packet ->
                Timber.d("fieldValues size in packet $index: ${packet.fieldValues.size}")
            }
        }

        // List of sensors to retrieve with their indexes
        val sensors = mapOf(
            "ECS" to 4, // Temperature Sensor 5 -> ECS
            "Capteurs" to 0, // field_index 0
            "Tampon" to 5, // field_index 5
            "Intérieur" to 11, // field_index 11
            "Extérieur" to 7, // field_index 7
            "Piscine" to 10, // field_index 10
            "Solar Pump" to 40, // field_index 40
            "Filtration" to 45 // field_index 45
        )

        // Find the packet with the correct data (packet 1 in this case)
        val correctPacket = data.headersets[0].packets.getOrNull(1)

        if (correctPacket != null) {
            // Search for values in the correct packet
            val sensorValues = correctPacket.fieldValues.associateBy { it.fieldIndex }

            // Log the first 10 fieldValues
            Timber.d("First 10 fieldValues:")
            sensorValues.entries.take(10).forEach { entry ->
                Timber.d("fieldIndex: ${entry.key}, value: ${entry.value.value}")
            }

            // Build the display of retrieved values
            val leftSensorList = mutableListOf<Sensor>()
            val rightSensorList = mutableListOf<Sensor>()
            val statusList = mutableListOf<StatusIndicator>()

            // Add sensors and status indicators
            for ((name, index) in sensors) {
                val value = sensorValues[index]?.value ?: getString(R.string.value_not_available)
                if (name == "ECS" || name == "Capteurs" || name == "Tampon" ||
                    name == "Intérieur" || name == "Extérieur" || name == "Piscine") {
                    // Add "°C" if the sensor is a temperature sensor
                    val sensor = Sensor(name, "$value°C")
                    when (name) {
                        "Capteurs", "Piscine", "Extérieur" -> leftSensorList.add(sensor)
                        "ECS", "Tampon", "Intérieur" -> rightSensorList.add(sensor)
                    }
                } else {
                    // Add status indicators
                    val sensor = Sensor(name, value)
                    when (name) {
                        "Solar Pump" -> leftSensorList.add(sensor)
                        "Filtration" -> rightSensorList.add(sensor)
                    }
                }
            }

            // Update the RecyclerViews
            updateSensorLists(leftSensorList, rightSensorList)
            updateStatusList(statusList)

            Timber.d(
                getString(
                    R.string.data_retrieved,
                    (leftSensorList + rightSensorList).joinToString("\n") +
                            "\nStatus Indicators: " + statusList.joinToString("\n")
                )
            )
        } else {
            Timber.e("Correct packet not found")
            updateStatusList(emptyList())
        }
    }

    private fun fetchDataAndRefresh() {
        // Check if we should fetch data based on network settings
        if (!NetworkUtils.shouldFetchData(this)) {
            Timber.d("Skipping data fetch due to network settings (WiFi Only)")

            // Get WiFi Only preference status
            val wifiOnlyEnabled = sharedPreferences.getBoolean("wifi_only", false)

            // If WiFi Only is enabled and we're not fetching data (meaning no WiFi),
            // update display to show "NoWifi"
            if (wifiOnlyEnabled) {
                // Create sensor objects with "NoWifi" values
                val noWifiLeftSensors = listOf(
                    Sensor("Capteurs", "NoWifi"),
                    Sensor("Piscine", "NoWifi"),
                    Sensor("Extérieur", "NoWifi")
                )

                val noWifiRightSensors = listOf(
                    Sensor("ECS", "NoWifi"),
                    Sensor("Tampon", "NoWifi"),
                    Sensor("Intérieur", "NoWifi")
                )

                val noWifiStatusIndicators = listOf(
                    StatusIndicator("Solar Pump", false, "NoWifi"),
                    StatusIndicator("Filtration", false, "NoWifi")
                )

                // Update the RecyclerViews
                updateSensorLists(noWifiLeftSensors, noWifiRightSensors)
                updateStatusList(noWifiStatusIndicators)

                // Also update SharedPreferences for widgets
                sharedPreferences.edit()
                    .putString(EcsWidget.PREF_ECS_TEMPERATURE, "NoWifi")
                    .putString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, "NoWifi")
                    .putString(EcsWidget.PREF_PISCINE_TEMPERATURE, "NoWifi")
                    .apply()

                // Send broadcast to update widgets
                val widgetUpdateIntent = Intent(this, WidgetUpdateReceiver::class.java)
                widgetUpdateIntent.action = "com.example.wierdesol.WIDGET_UPDATE"
                sendBroadcast(widgetUpdateIntent)

                Timber.d("Updated displays to show NoWifi status")
            }

            return
        }
        fetchData { data ->
            if (data != null) {
                Timber.d(getString(R.string.new_data_analysis_attempt))
                analyzeData(data)

                // Extract and save temperatures for widget access
                extractAndSaveTemperatures(data)

                // Use explicit intent to update widgets more reliably
                val widgetUpdateIntent = Intent(this, WidgetUpdateReceiver::class.java)
                widgetUpdateIntent.action = "com.example.wierdesol.WIDGET_UPDATE"
                sendBroadcast(widgetUpdateIntent)
                Timber.d("Sent explicit broadcast to update widgets after data refresh")
            } else {
                Timber.d(getString(R.string.data_not_available))
            }
        }
    }

    private fun setupLinkTextView() {
        val fullText = getString(R.string.schema)
        val spannableString = SpannableString(fullText)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val url = "https://wierde.vbus.io/dlx/live/view/99"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        }

        spannableString.setSpan(clickableSpan, 0, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.linkTextView.text = spannableString
        binding.linkTextView.movementMethod = LinkMovementMethod.getInstance()
    }
    private fun fetchDataPeriodically() {
        coroutineScope.launch {
            while (true) {
                fetchDataAndRefresh()
                val refreshRateMinutes = sharedPreferences.getString("refresh_rate", "10")?.toLongOrNull() ?: 10
                val refreshRateMillis = refreshRateMinutes * 60 * 1000
                delay(refreshRateMillis)
            }
        }
    }
    private fun extractAndSaveTemperatures(data: ResolResponse) {
        // Find the packet with the correct data (packet 1 in this case)
        val correctPacket = data.headersets.getOrNull(0)?.packets?.getOrNull(1)

        if (correctPacket != null) {
            // Search for values in the correct packet
            val sensorValues = correctPacket.fieldValues.associateBy { it.fieldIndex }

            // Get ECS and Capteurs temperatures
            val ecsValue = sensorValues[4]?.value ?: "N/A"
            val capteursValue = sensorValues[0]?.value ?: "N/A"
            val piscineValue = sensorValues[10]?.value ?: "N/A"

            // Save to SharedPreferences for the widget to access
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            sharedPreferences.edit()
                .putString(EcsWidget.PREF_ECS_TEMPERATURE, "${ecsValue}°C")
                .putString(EcsWidget.PREF_CAPTEURS_TEMPERATURE, "${capteursValue}°C")
                .putString(EcsWidget.PREF_PISCINE_TEMPERATURE, "${piscineValue}°C")
                .apply()

            Timber.d("Saved temperatures to SharedPreferences: ECS=${ecsValue}°C, Capteurs=${capteursValue}°C , Piscine=${piscineValue}°C")
        }
    }
    internal fun fetchData(callback: (ResolResponse?) -> Unit) {
        RetrofitClient.instance.getLiveData().enqueue(object : Callback<ResolResponse> {
            override fun onResponse(call: Call<ResolResponse>, response: Response<ResolResponse>) {
                if (response.isSuccessful) {
                    val data = response.body()
                    Timber.d(getString(R.string.data_received, data.toString()))
                    callback(data)
                } else {
                    Timber.e(getString(R.string.error, response.code().toString()))
                    callback(null)
                }
            }

            override fun onFailure(call: Call<ResolResponse>, t: Throwable) {
                Timber.e(t, getString(R.string.connection_failed))
                callback(null)
            }
        })
    }
    private fun updateSensorLists(leftSensorList: List<Sensor>, rightSensorList: List<Sensor>) {
        leftSensorAdapter.sensors = leftSensorList
        rightSensorAdapter.sensors = rightSensorList
        leftSensorAdapter.notifyDataSetChanged()
        rightSensorAdapter.notifyDataSetChanged()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}