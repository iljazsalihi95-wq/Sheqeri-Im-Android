package com.sheqeriim.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GlucoseReading(val valueMgDl: Int, val timeMillis: Long, val note: String)

class GlucoseStore(context: Context) {
    private val preferences = context.getSharedPreferences("sheqeri_local_readings", Context.MODE_PRIVATE)

    fun all(): List<GlucoseReading> = try {
        val arr = JSONArray(preferences.getString("readings", "[]"))
        (0 until arr.length()).map { index ->
            val item = arr.getJSONObject(index)
            GlucoseReading(item.getInt("value"), item.getLong("time"), item.optString("note"))
        }.sortedByDescending { it.timeMillis }
    } catch (_: Exception) { emptyList() }

    fun add(valueMgDl: Int, note: String): Boolean {
        if (valueMgDl !in 20..600) return false
        val arr = JSONArray()
        (listOf(GlucoseReading(valueMgDl, System.currentTimeMillis(), note)) + all()).take(1000).forEach {
            arr.put(JSONObject().put("value", it.valueMgDl).put("time", it.timeMillis).put("note", it.note))
        }
        return preferences.edit().putString("readings", arr.toString()).commit()
    }
}
