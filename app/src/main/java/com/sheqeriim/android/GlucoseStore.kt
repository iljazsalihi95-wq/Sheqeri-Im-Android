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
    /** Persist meter readings with their original timestamps, skipping repeats. */
    @Synchronized
    fun importMeterReading(valueMgDl: Int, timeMillis: Long, meterId: String, sequence: Int): Boolean {
        if (valueMgDl !in 20..600 || timeMillis <= 0L || sequence < 0) return false
        val key = "ble:" + meterId + ":" + sequence
        if (preferences.getBoolean(key, false)) return false
        val note = "Accu-Chek Guide · BLE #" + sequence
        val existing = all()
        val updated = (existing + GlucoseReading(valueMgDl, timeMillis, note))
            .sortedByDescending { it.timeMillis }.take(1000)
        val arr = JSONArray()
        updated.forEach {
            arr.put(JSONObject().put("value", it.valueMgDl).put("time", it.timeMillis).put("note", it.note))
        }
        return preferences.edit().putString("readings", arr.toString()).putBoolean(key, true).commit()
    }

    /** Import a user-confirmed historical reading without changing its measurement time. */
    @Synchronized
    fun addHistorical(valueMgDl: Int, timeMillis: Long, note: String): Boolean {
        if (valueMgDl !in 20..600 || timeMillis <= 0L) return false
        if (all().any { it.valueMgDl == valueMgDl && kotlin.math.abs(it.timeMillis - timeMillis) < 60_000L }) return false
        val arr = JSONArray()
        (all() + GlucoseReading(valueMgDl, timeMillis, note))
            .sortedByDescending { it.timeMillis }.take(1000).forEach {
                arr.put(JSONObject().put("value", it.valueMgDl).put("time", it.timeMillis).put("note", it.note))
            }
        return preferences.edit().putString("readings", arr.toString()).commit()
    }

}
