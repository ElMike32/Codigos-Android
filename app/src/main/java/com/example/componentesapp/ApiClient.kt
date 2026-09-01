package com.example.componentesapp

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiClient private constructor() {

    companion object {
        val instance: ApiClient by lazy { ApiClient() }
    }

    suspend fun getDataFromScript(baseUrl: String, deviceId: String): ApiResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var currentUrl = if (baseUrl.contains("?")) "$baseUrl&deviceId=$deviceId" else "$baseUrl?deviceId=$deviceId"
        var redirects = 0
        val maxRedirects = 5

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 45000
                readTimeout = 45000
                instanceFollowRedirects = false // Manejo manual de redirecciones de Google
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android Native App)")
            }

            val responseCode = connection.responseCode

            // Si Google responde con redirección (301, 302, 303, 307)
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (!location.isNullOrEmpty()) {
                    currentUrl = location
                    redirects++
                    continue
                } else {
                    throw Exception("Redirección de Google sin cabecera Location.")
                }
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonString = reader.use { it.readText() }
                connection.disconnect()
                return@withContext Gson().fromJson(jsonString, ApiResponse::class.java)
            } else {
                connection.disconnect()
                throw Exception("Servidor respondió con código HTTP $responseCode")
            }
        }
        throw Exception("Demasiadas redirecciones al conectar con Google Apps Script.")
    }
}
