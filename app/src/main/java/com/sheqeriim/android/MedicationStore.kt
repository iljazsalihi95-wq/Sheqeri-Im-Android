package com.sheqeriim.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MedicationEntry(val name: String, val dose: String, val timeMillis: Long)

class MedicationStore(context: Context) {
    private val preferences = context.getSharedPreferences("sheqeri_medication_log", Context.MODE_PRIVATE)
    fun all(): List<MedicationEntry> = try {
        val arr = JSONArray(preferences.getString("entries", "[]"))
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            MedicationEntry(obj.getString("name"), obj.getString("dose"), obj.getLong("time"))
        }.sortedByDescending { it.timeMillis }
    } catch (_: Exception) { emptyList() }

    fun add(name: String, dose: String): Boolean {
        if (name.isBlank() || dose.isBlank()) return false
        val arr = JSONArray()
        (listOf(MedicationEntry(name.trim(), dose.trim(), System.currentTimeMillis())) + all()).take(1000).forEach {
            arr.put(JSONObject().put("name", it.name).put("dose", it.dose).put("time", it.timeMillis))
        }
        return preferences.edit().putString("entries", arr.toString()).commit()
    }
}
