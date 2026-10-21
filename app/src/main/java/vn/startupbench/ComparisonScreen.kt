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
        val samples = item.androidResults().filter { it.mode == mode }
        val captured = item.readyValues(mode).size + item.assistedValues(mode).size
        return item.optString("status") in listOf("completed", "completed_with_errors") &&
            samples.size >= item.optInt("repeats", 1) && captured == samples.size
    }
    fun show(base: JSONObject, candidate: JSONObject, cohort: MeasurementCohort? = null, statistic: SummaryStatistic = SummaryStatistic.MEAN) {
        val selected = cohort ?: when {
            listOf("COLD", "WARM", "HOT").any { base.readyValues(it).isNotEmpty() && candidate.readyValues(it).isNotEmpty() } -> MeasurementCohort.AUTOMATIC
            listOf("COLD", "WARM", "HOT").any { base.assistedValues(it).isNotEmpty() && candidate.assistedValues(it).isNotEmpty() } -> MeasurementCohort.ASSISTED
            else -> MeasurementCohort.AUTOMATIC
        }
        val fields = linkedMapOf("fingerprint" to "thiết bị / Android", "protocol" to "quy trình đo", "component" to "Activity", "powerSave" to "tiết kiệm pin", "plugged" to "trạng thái sạc", "readinessCriterion" to "mốc UI")
        val warnings = fields.mapNotNull { (key, label) ->
            when { !base.has(key) || !candidate.has(key) -> "Thiếu thông tin $label"
                base.optString(key) != candidate.optString(key) -> "Khác $label"
                else -> null }
        }.toMutableList()
        if (base.optInt("batteryTempTenthsC", -1) >= 0 && candidate.optInt("batteryTempTenthsC", -1) >= 0 &&
            kotlin.math.abs(base.optInt("batteryTempTenthsC") - candidate.optInt("batteryTempTenthsC")) >= 30) warnings.add("Nhiệt độ đầu phiên lệch từ 3°C")
        val assessments = listOf("COLD", "WARM", "HOT").associateWith { mode ->
            ComparisonAssessment.assess(base.cohortValues(mode, selected), candidate.cohortValues(mode, selected), warnings.isEmpty(), complete(base, mode) && complete(candidate, mode),
                maxOf(base.observationIntervals(mode, selected).maxOrNull()?.toDouble() ?: 0.0, candidate.observationIntervals(mode, selected).maxOrNull()?.toDouble() ?: 0.0), statistic)
        }
        content.removeAllViews()
        content.addView(action("← Quay lại lịch sử", onBack))
        content.addView(text("So sánh phiên đo", 24f).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(text(base.optString("label") + " · " + base.optString("package"), 14f, muted))
        content.addView(text("${statistic.label} thời gian quan sát mốc UI · nhóm ${selected.label}. Không phải TTFD chuẩn.", 13f, muted))
        fun selector(labels: List<String>, current: Int, changed: (Int) -> Unit): Spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(current)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    (view as? TextView)?.setTextColor(Color.WHITE)
                    if (position != current) changed(position)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        content.addView(text("Nhóm dữ liệu — luôn so sánh cùng nhóm A/B", 13f, muted))
        content.addView(selector(MeasurementCohort.entries.map { it.label }, selected.ordinal) { show(base, candidate, MeasurementCohort.entries[it], statistic) })
        content.addView(text("Chỉ số dùng tính chênh lệch", 13f, muted))
        content.addView(selector(SummaryStatistic.entries.map { it.label }, statistic.ordinal) { show(base, candidate, selected, SummaryStatistic.entries[it]) })
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
        content.addView(action("Đổi A ↔ B") { show(candidate, base, selected, statistic) })
        if (warnings.isNotEmpty()) card {
            addView(text("⚠ Điều kiện đo chưa tương đương", 16f, cautionColor))
            addView(text(warnings.joinToString("\n"), 13f, muted))
        }
        card {
            addView(text("Nhận xét tổng quan", 18f))
            addView(text(if (selected == MeasurementCohort.ASSISTED)
                "Có thể đối chứng số liệu trải nghiệm đến mốc UI, bao gồm ads, chờ và thao tác/kiểm tra lại của tester. Chênh lệch không chứng minh code khởi động nhanh hơn; hãy giữ cùng kịch bản quảng cáo và thao tác."
                else ComparisonAssessment.overall(assessments.values.toList()), 15f))
            addView(text(if (statistic == SummaryStatistic.MEAN) "Trung bình = tổng thời gian / số mẫu được xác nhận trong nhóm. Số liệu vẫn hiển thị khi ít mẫu; chỉ nhận xét tối ưu khi đủ điều kiện và vượt độ dao động/quan sát."
                else activity.getString(R.string.comparison_rules), 12f, muted))
        }
        assessments.forEach { (mode, result) -> card {
            addView(text(mode, 20f).apply { setTypeface(null, Typeface.BOLD) })
            val numbers = LinearLayout(activity)
            listOf(Triple("A · Mốc", result.baseline, baselineColor), Triple("B · Đối chứng", result.candidate, candidateColor)).forEachIndexed { index, (label, value, color) ->
                numbers.addView(column().apply {
                    addView(text(label, 13f, color)); addView(text(value?.let { ms(it) } ?: "—", 22f, color))
                    val stats = SampleStatistics.of((if (index == 0) base else candidate).cohortValues(mode, selected))
                    addView(text("${stats.count} mẫu\nTB ${stats.mean?.let { ms(it) } ?: "—"}\nTrung vị ${stats.median?.let { ms(it) } ?: "—"}\nMin ${stats.min ?: "—"} / max ${stats.max ?: "—"} ms", 12f, muted))
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
                val change = when { delta < 0 -> "↓ Thời gian B thấp hơn A"; delta > 0 -> "↑ Thời gian B cao hơn A"; else -> "= Không đổi" }
                val percentage = result.percent?.let { String.format(Locale.US, " (%+.1f%%)", it) } ?: " · không tính % từ mốc 0 ms"
                addView(text("$change\n${String.format(Locale.US, "%+.1f ms", delta)}$percentage", 18f, changeColor))
            }
            addView(text("Mẫu được xác nhận · ${selected.label}: A ${base.cohortValues(mode, selected).size} · B ${candidate.cohortValues(mode, selected).size}", 12f, muted))
            addView(text("Khoảng quan sát median: A ${LaunchResult.median(base.observationIntervals(mode, selected))?.let { ms(it) } ?: "—"} · B ${LaunchResult.median(candidate.observationIntervals(mode, selected))?.let { ms(it) } ?: "—"}", 12f, muted))
            addView(text("Nhận xét còn xét khoảng quan sát lớn nhất để tránh kết luận từ độ trễ phát hiện mốc.", 12f, muted))
            val verdictColor = when (result.verdict) { ComparisonVerdict.IMPROVED -> fasterColor; ComparisonVerdict.REGRESSED -> slowerColor; else -> cautionColor }
            addView(text(if (selected == MeasurementCohort.ASSISTED) "Đối chứng trải nghiệm có ads/thao tác" else result.title, 17f,
                if (selected == MeasurementCohort.ASSISTED) cautionColor else verdictColor).apply { setTypeface(null, Typeface.BOLD) })
            addView(text(if (selected == MeasurementCohort.ASSISTED) {
                if (result.baseline == null || result.candidate == null) "Nhóm này cần mẫu ở cả hai phiên mới có chênh lệch."
                else "Chênh lệch ở trên là số liệu của nhóm đã chọn. Có thể do thời lượng ads hoặc thao tác; không dùng để kết luận tốc độ khởi động thuần. Nên chạy 5–10 lần cùng kịch bản."
            } else result.advice, 14f))
        } }
    }
}
