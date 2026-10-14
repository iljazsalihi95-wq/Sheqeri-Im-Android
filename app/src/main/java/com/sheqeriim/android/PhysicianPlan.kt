package com.sheqeriim.android

/** Physician chart transcribed from 13 Oct 2026. Reference only; never automatic dosing. */
object PhysicianPlan {
    data class ChartRow(val range: String, val documentedUnits: String, val attention: Boolean)
    fun lookup(value: Int): ChartRow = when {
        value < 90 -> ChartRow("< 90 mg/dL", "0", false)
        value == 90 -> ChartRow("90 mg/dL — nicht definiert", "?", true)
        value <= 120 -> ChartRow("91–120 mg/dL", "0", false)
        value <= 150 -> ChartRow("121–150 mg/dL", "2", true)
        value <= 180 -> ChartRow("151–180 mg/dL", "3", true)
        value <= 210 -> ChartRow("181–210 mg/dL", "4", true)
        value <= 240 -> ChartRow("211–240 mg/dL", "5", true)
        value <= 270 -> ChartRow("241–270 mg/dL", "6", true)
        value <= 300 -> ChartRow("271–300 mg/dL", "7", true)
        else -> ChartRow("> 300 mg/dL — ärztliche Rücksprache", "?", true)
    }
}
