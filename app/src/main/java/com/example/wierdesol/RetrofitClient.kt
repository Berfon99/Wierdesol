package com.example.wierdesol

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import timber.log.Timber

object RetrofitClient {
    private const val BASE_URL = "https://wierde.vbus.io/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Increased timeout
        .readTimeout(60, TimeUnit.SECONDS)    // Increased timeout
        .writeTimeout(60, TimeUnit.SECONDS)   // Increased timeout
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Log request and response details
        })
        .addInterceptor { chain ->
            val request = chain.request()
            Timber.d("Request URL: ${request.url}")
            Timber.d("Request Headers: ${request.headers}")
            val response = chain.proceed(request)
            Timber.d("Response Code: ${response.code}")
            Timber.d("Response Headers: ${response.headers}")
            response
        }
        .build()

    val instance: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}