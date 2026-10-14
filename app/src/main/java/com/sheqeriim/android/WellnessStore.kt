package com.sheqeriim.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WellnessEntry(val kind: String, val detail: String, val timestamp: Long)

class WellnessStore(context: Context) {
    private val prefs = context.getSharedPreferences("wellness_log", Context.MODE_PRIVATE)
    fun all(): List<WellnessEntry> = try {
        val a = JSONArray(prefs.getString("events", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            WellnessEntry(o.getString("kind"), o.getString("detail"), o.getLong("time"))
        }.sortedByDescending { it.timestamp }
    } catch (_: Exception) { emptyList() }

    fun add(kind: String, detail: String): Boolean {
        if (detail.isBlank()) return false
        val a = JSONArray()
        (listOf(WellnessEntry(kind, detail.trim().take(200), System.currentTimeMillis())) + all()).take(2000).forEach {
            a.put(JSONObject().put("kind", it.kind).put("detail", it.detail).put("time", it.timestamp))
        }
        return prefs.edit().putString("events", a.toString()).commit()
    }
}

object GlucoseEducation {
    fun message(mgdl: Int?, language: String): String {
        if (mgdl == null) return when (language) {
            "de" -> "Bitte zuerst einen Messwert erfassen. Individuelle Zielwerte mit der Arztpraxis vereinbaren."
            "en" -> "Record a reading first. Agree on personal glucose targets with your clinician."
            else -> "Së pari regjistro një matje. Objektivat personale përcaktoji me mjekun."
        }
        return when {
            mgdl < 54 -> when (language) {
                "de" -> "Sehr niedriger Wert: sofort nach Ihrem ärztlichen Hypoglykämieplan handeln. Bei Bewusstseinsstörung, Krampfanfall oder fehlender Schluckfähigkeit: 112. Kein Insulin aufgrund dieser App dosieren."
                "en" -> "Very low glucose: follow your prescribed hypo plan immediately. If unconscious, seizing or unable to swallow, call emergency services. Do not dose insulin from this app."
                else -> "Sheqer shumë i ulët: ndiq menjëherë planin e mjekut për hipoglikemi. Nëse humbet vetëdija, ka kriza ose nuk mund të gëlltisësh, telefono 112. Mos e cakto insulinën nga ky aplikacion."
            }
            mgdl < 70 -> when (language) {
                "de" -> "Unterzuckerung möglich. Nach Ihrem Hypoglykämieplan behandeln und erneut messen. Bewegung vorerst vermeiden."
                "en" -> "Possible low glucose. Follow your hypo treatment plan and recheck. Avoid exercise for now."
                else -> "Sheqer i ulët: ndiq planin për hipoglikemi dhe mate sërish. Shmang aktivitetin fizik për momentin."
            }
            mgdl >= 250 -> when (language) {
                "de" -> "Hoher Wert. Nach Ihrem ärztlichen Plan vorgehen, bei Bedarf Ketone prüfen. Bei Erbrechen, Bauchschmerzen, schneller Atmung oder starker Schwäche sofort ärztliche Hilfe suchen. Keine automatische Insulindosis."
                "en" -> "High glucose. Follow your care plan and check ketones if indicated. Seek urgent care for vomiting, abdominal pain, rapid breathing or marked weakness. No automatic insulin dosing."
                else -> "Sheqer i lartë: ndiq planin e mjekut dhe kontrollo ketonet kur kërkohet. Me të vjella, dhimbje barku, frymëmarrje të shpejtë ose dobësi të rëndë kërko ndihmë urgjente. Aplikacioni nuk cakton dozë insuline."
            }
            mgdl > 180 -> when (language) {
                "de" -> "Erhöhter Wert. Mahlzeit, Zeitpunkt und Medikamente dokumentieren und mit Ihren individuellen Zielwerten vergleichen. Keine Insulindosis aus diesem Wert ableiten."
                "en" -> "Elevated reading. Log meal, timing and medication and compare with personal targets. Do not derive an insulin dose from this reading."
                else -> "Matje e ngritur: shëno ushqimin, orën dhe barnat; krahasoji me objektivat e mjekut. Mos e nxirr dozën e insulinës nga kjo matje."
            }
            else -> when (language) {
                "de" -> "Wert dokumentiert. Die Bewertung hängt auch davon ab, ob vor oder nach dem Essen gemessen wurde. Individuelle Zielwerte gelten."
                "en" -> "Reading logged. Interpretation depends on whether it was before or after a meal. Personal targets apply."
                else -> "Matja u regjistrua. Interpretimi varet edhe nëse është bërë para apo pas ushqimit. Vlejnë objektivat personale të mjekut."
            }
        }
    }
}
