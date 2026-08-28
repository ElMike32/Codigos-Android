package com.example.componentesapp

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET
    suspend fun getDataFromScript(@Url url: String): ApiResponse
}

object ApiClient {
    // Configurar OkHttpClient con 60 segundos de Timeout para evitar el error de conexión
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true) // Reintentar automáticamente en fallos temporales de red
        .build()

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
