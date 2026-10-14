package com.sheqeriim.android

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DoctorReport {
    fun write(context: Context, uri: Uri, readings: List<GlucoseReading>, meds: List<MedicationEntry>) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let(document::finishPage)
            pageNo++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            y = 48f
        }
        fun line(text: String, bold: Boolean = false) {
            if (page == null || y > 795f) newPage()
            paint.isFakeBoldText = bold
            page!!.canvas.drawText(text.take(94), 40f, y, paint)
            paint.isFakeBoldText = false
            y += 19f
        }
        try {
            line("SHEQERI IM - Blutzuckerprotokoll", true)
            line("Erstellt am: ${dateFormat.format(Date())}")
            line("Hinweis: Selbstdokumentation; Werte nicht medizinisch validiert.")
            y += 12f
            line("Blutzuckermessungen (mg/dL)", true)
            if (readings.isEmpty()) line("Keine Messungen erfasst.")
            readings.forEach { line("${dateFormat.format(Date(it.timeMillis))}  |  ${it.valueMgDl} mg/dL  |  ${it.note}") }
            y += 12f
            line("Medikamentenprotokoll (selbst eingetragen)", true)
            if (meds.isEmpty()) line("Keine Medikamenteneintraege erfasst.")
            meds.forEach { line("${dateFormat.format(Date(it.timeMillis))}  |  ${it.name}  |  ${it.dose}") }
            page?.let(document::finishPage)
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: error("PDF output unavailable")
        } finally { document.close() }
    }
}
