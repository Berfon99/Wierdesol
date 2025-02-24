package com.example.wierdesol

import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("dlx/download/live?channel=1")
    fun getLiveData(): Call<ResolResponse>
}
