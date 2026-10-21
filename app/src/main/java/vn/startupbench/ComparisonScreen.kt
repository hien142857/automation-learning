package vn.startupbench

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class ComparisonScreen(private val activity: Activity, private val content: LinearLayout, private val onBack: () -> Unit) {
    private val surface = Color.rgb(25, 36, 52)
    private val muted = Color.rgb(167, 184, 205)
    private val baselineColor = Color.rgb(143, 187, 255)
    private val candidateColor = Color.rgb(209, 173, 255)
    private val fasterColor = Color.rgb(91, 224, 194)
    private val slowerColor = Color.rgb(255, 157, 155)
    private val cautionColor = Color.rgb(255, 205, 126)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(6), 0, dp(6))
    }
    private fun shape() = GradientDrawable().apply { setColor(surface); cornerRadius = dp(18).toFloat() }
    private fun action(label: String, onClick: () -> Unit) = Button(activity).apply {
        text = label; isAllCaps = false; setTextColor(fasterColor)
        backgroundTintList = ColorStateList.valueOf(surface); setOnClickListener { onClick() }
    }
    private fun card(block: LinearLayout.() -> Unit) {
        content.addView(column().apply { background = shape(); setPadding(dp(16), dp(14), dp(16), dp(14)); block() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }
    private fun ms(value: Double) = String.format(Locale.US, "%.1f ms", value)
    private fun complete(item: JSONObject, mode: String): Boolean {
        val samples = item.results().filter { it.mode == mode }
        return item.optString("status") in listOf("completed", "completed_with_errors") &&
            samples.size >= item.optInt("repeats", 1) && samples.all { it.valid }
    }
    fun show(base: JSONObject, candidate: JSONObject) {
        val fields = linkedMapOf("fingerprint" to "thiết bị / Android", "protocol" to "quy trình đo", "component" to "Activity", "powerSave" to "tiết kiệm pin", "plugged" to "trạng thái sạc")
        val warnings = fields.mapNotNull { (key, label) ->
            when { !base.has(key) || !candidate.has(key) -> "Thiếu thông tin $label"
                base.optString(key) != candidate.optString(key) -> "Khác $label"
                else -> null }
        }.toMutableList()
        if (base.optInt("batteryTempTenthsC", -1) >= 0 && candidate.optInt("batteryTempTenthsC", -1) >= 0 &&
            kotlin.math.abs(base.optInt("batteryTempTenthsC") - candidate.optInt("batteryTempTenthsC")) >= 30) warnings.add("Nhiệt độ đầu phiên lệch từ 3°C")
        val assessments = listOf("COLD", "WARM", "HOT").associateWith { mode ->
            ComparisonAssessment.assess(base.values(mode), candidate.values(mode), warnings.isEmpty(), complete(base, mode) && complete(candidate, mode))
        }
        content.removeAllViews()
        content.addView(action("← Quay lại lịch sử", onBack))
        content.addView(text("So sánh phiên đo", 24f).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(text(base.optString("label") + " · " + base.optString("package"), 14f, muted))
        content.addView(text("Thời gian thấp hơn là nhanh hơn · so sánh median", 13f, muted))
        val sessions = LinearLayout(activity)
        listOf(Triple(base, "A · MỐC", baselineColor), Triple(candidate, "B · ĐỐI CHỨNG", candidateColor)).forEach { (item, label, color) ->
            sessions.addView(column().apply {
                setPadding(dp(12), dp(12), dp(12), dp(12)); background = shape()
                addView(text(label, 13f, color).apply { setTypeface(null, Typeface.BOLD) })
                addView(text("v${item.optString("version")} (${item.optLong("versionCode")})", 17f, color))
                addView(text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.getString("id").toLong())), 12f, muted))
                if (item.optString("note").isNotBlank()) addView(text(item.optString("note"), 12f))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(8), dp(2), dp(12)) })
        }
        content.addView(sessions)
        content.addView(action("Đổi A ↔ B") { show(candidate, base) })
        if (warnings.isNotEmpty()) card {
            addView(text("⚠ Điều kiện đo chưa tương đương", 16f, cautionColor))
            addView(text(warnings.joinToString("\n"), 13f, muted))
        }
        card {
            addView(text("Nhận xét tổng quan", 18f))
            addView(text(ComparisonAssessment.overall(assessments.values.toList()), 15f))
            addView(text(activity.getString(R.string.comparison_rules), 12f, muted))
        }
        assessments.forEach { (mode, result) -> card {
            addView(text(mode, 20f).apply { setTypeface(null, Typeface.BOLD) })
            val numbers = LinearLayout(activity)
            listOf(Triple("A · Mốc", result.baseline, baselineColor), Triple("B · Đối chứng", result.candidate, candidateColor)).forEach { (label, value, color) ->
                numbers.addView(column().apply {
                    addView(text(label, 13f, color)); addView(text(value?.let { ms(it) } ?: "—", 22f, color))
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            addView(numbers)
            val maxValue = maxOf(result.baseline ?: 0.0, result.candidate ?: 0.0, 1.0)
            listOf(Triple("A", result.baseline, baselineColor), Triple("B", result.candidate, candidateColor)).forEach { (label, value, color) ->
                if (value != null) {
                    val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
                    row.addView(text(label, 12f, color), LinearLayout.LayoutParams(dp(22), -2))
                    row.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 1000; progress = (value / maxValue * 1000).toInt(); progressTintList = ColorStateList.valueOf(color)
                        progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(13, 20, 32))
                        contentDescription = "$label · ${ms(value)}"
                    }, LinearLayout.LayoutParams(0, dp(12), 1f))
                    addView(row)
                }
            }
            val changeColor = when { result.delta == null || result.delta == 0.0 -> muted; result.delta < 0 -> fasterColor; else -> slowerColor }
            result.delta?.let { delta ->
                val change = when { delta < 0 -> "↓ B nhanh hơn A"; delta > 0 -> "↑ B chậm hơn A"; else -> "= Không đổi" }
                val percentage = result.percent?.let { String.format(Locale.US, " (%+.1f%%)", it) } ?: " · không tính % từ mốc 0 ms"
                addView(text("$change\n${String.format(Locale.US, "%+.1f ms", delta)}$percentage", 18f, changeColor))
            }
            addView(text("Mẫu hợp lệ: A ${base.values(mode).size} · B ${candidate.values(mode).size}", 12f, muted))
            val verdictColor = when (result.verdict) { ComparisonVerdict.IMPROVED -> fasterColor; ComparisonVerdict.REGRESSED -> slowerColor; else -> cautionColor }
            addView(text(result.title, 17f, verdictColor).apply { setTypeface(null, Typeface.BOLD) })
            addView(text(result.advice, 14f))
        } }
    }
}
