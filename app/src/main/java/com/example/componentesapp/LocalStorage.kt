package com.example.componentesapp

import android.content.Context
import android.content.SharedPreferences

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
}
