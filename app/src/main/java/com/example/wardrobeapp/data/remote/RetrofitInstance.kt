package com.example.wardrobeapp.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.jvm.java
import okhttp3.OkHttpClient

object RetrofitInstance{
    private const val BASE_URL = "https://api.open-meteo.com/";
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    }
    val apiService: OpenMeteoApi by lazy {
        retrofit.create(OpenMeteoApi::class.java)
    }
}