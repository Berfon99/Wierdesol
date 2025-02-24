package com.example.wierdesol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        leftRecyclerView = binding.leftRecyclerView
        rightRecyclerView = binding.rightRecyclerView

        // Initialize Timber
        val isDebug = true
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize the link and make it clickable
        setupLinkTextView()

        // Initialize the RecyclerViews
        leftRecyclerView.layoutManager = LinearLayoutManager(this)
        rightRecyclerView.layoutManager = LinearLayoutManager(this)
        leftSensorAdapter = SensorAdapter(emptyList())
        rightSensorAdapter = SensorAdapter(emptyList())
        leftRecyclerView.adapter = leftSensorAdapter
        rightRecyclerView.adapter = rightSensorAdapter

        // Initialize the refresh button
        binding.refreshButton.setOnClickListener {
            fetchDataAndRefresh()
        }

        // Start fetching data periodically
        fetchDataPeriodically()
    }

    private fun setupLinkTextView() {
        val fullText = getString(R.string.check_url)
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
                delay(10000) // Wait for 10 seconds
            }
        }
    }

    private fun fetchDataAndRefresh() {
        fetchData { data ->
            if (data != null) {
                Timber.d(getString(R.string.new_data_analysis_attempt))
                analyzeData(data)
            } else {
                Timber.d(getString(R.string.data_not_available))
            }
        }
    }

    private fun fetchData(callback: (ResolResponse?) -> Unit) {
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

    private fun analyzeData(data: ResolResponse?) {
        if (data == null || data.headersets.isEmpty()) {
            // Handle the case where data is null or headersets is empty
            val errorSensor = Sensor(getString(R.string.data_retrieval_error), "")
            updateSensorLists(listOf(errorSensor), emptyList())
            return
        }

        // List of sensors to retrieve with their indexes
        val sensors = mapOf(
            "ECS" to 4, // Temperature Sensor 5 -> ECS
            "Capteurs" to 0, // field_index 0
            "Tampon" to 5, // field_index 5
            "Intérieur" to 11, // field_index 11
            "Extérieur" to 7, // field_index 7
            "Piscine" to 10 // field_index 10
        )

        // Search for values
        val sensorValues = data.headersets
            .flatMap { it.packets }
            .flatMap { it.fieldValues }
            .associateBy { it.fieldIndex }

        // Build the display of retrieved values
        val leftSensorList = listOf(
            sensors["Capteurs"]?.let { index ->
                Sensor("Capteurs", sensorValues[index]?.value ?: getString(R.string.value_not_available))
            } ?: Sensor("Capteurs", getString(R.string.value_not_available)),
            sensors["Piscine"]?.let { index ->
                Sensor("Piscine", sensorValues[index]?.value ?: getString(R.string.value_not_available))
            } ?: Sensor("Piscine", getString(R.string.value_not_available)),
            sensors["Extérieur"]?.let { index ->
                Sensor("Extérieur", sensorValues[index]?.value ?: getString(R.string.value_not_available))
            } ?: Sensor("Extérieur", getString(R.string.value_not_available))
        )

        val rightSensorList = listOf(
            sensors["ECS"]?.let { index ->
                Sensor("ECS", sensorValues[index]?.value ?: getString(R.string.value_not_available))
            } ?: Sensor("ECS", getString(R.string.value_not_available)),
            sensors["Tampon"]?.let { index ->
                Sensor("Tampon", sensorValues[index]?.value ?: getString(R.string.value_not_available))
            } ?: Sensor("Tampon", getString(R.string.value_not_available)),
            sensors["Intérieur"]?.let { index ->
                Sensor("Intérieur", sensorValues[index]?.value ?: getString(R.string.value_not_available))
            } ?: Sensor("Intérieur", getString(R.string.value_not_available))
        )

        // Update the RecyclerViews
        updateSensorLists(leftSensorList, rightSensorList)
        Timber.d(getString(R.string.data_retrieved, (leftSensorList + rightSensorList).joinToString("\n")))
    }
    private fun updateSensorLists(leftSensorList: List<Sensor>, rightSensorList: List<Sensor>) {
        leftSensorAdapter.sensors = leftSensorList
        rightSensorAdapter.sensors = rightSensorList
        leftSensorAdapter.notifyDataSetChanged()
        rightSensorAdapter.notifyDataSetChanged()
    }
}