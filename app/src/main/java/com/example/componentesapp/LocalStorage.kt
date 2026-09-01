package com.example.componentesapp

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class LocalStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_cache", Context.MODE_PRIVATE)

    fun guardarJson(json: String) {
        prefs.edit().putString("json_datos", json).apply()
    }

    fun obtenerJson(): String? {
        return prefs.getString("json_datos", null)
    }

    fun tieneDatos(): Boolean {
        return prefs.contains("json_datos")
    }

    fun limpiarMemoriaLocal() {
        prefs.edit().clear().apply()
    }

    // Nombre de función corregido sin espacios
    fun obtenerOCrearDeviceId(): String {
        var id = prefs.getString("device_unique_id", null)
        if (id == null) {
            id = "DEV-" + UUID.randomUUID().toString().take(8).uppercase()
            prefs.edit().putString("device_unique_id", id).apply()
        }
        return id
    }
}
