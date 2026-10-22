package vn.startupbench

import org.json.JSONArray
import org.json.JSONObject

/** Imports the validated TTID summary produced by scripts/startup_vitals_report.py. */
object MacrobenchmarkReport {
    const val SOURCE = "androidx-macrobenchmark-perfetto-ttid"

    fun read(text: String, now: Long = System.currentTimeMillis()): JSONObject {
        val report = JSONObject(text)
        require(report.getString("source") == SOURCE && report.getString("scope") == "local-lab-not-play-console") {
            "Chọn file summary TTID tạo bởi startup_vitals_report.py, không phải số đo UI/TotalTime."
        }
        val rows = report.getJSONArray("results")
        require(rows.length() in 1..3)
        val seen = mutableSetOf<String>()
        val samples = JSONArray()
        val modes = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val mode = row.getString("mode")
            StartupVitals.threshold(mode)
            require(seen.add(mode)) { "Kiểu đo bị trùng" }
            val planned = row.getInt("planned")
            require(planned in 1..1000)
            val runs = row.getJSONArray("ttidMs")
            require(runs.length() <= planned)
            for (j in 0 until planned) {
                val sample = JSONObject().put("mode", mode)
                if (j < runs.length()) {
                    val raw = runs.get(j)
                    require(raw is Number) { "TTID cần là số" }
                    val value = raw.toDouble()
                    require(value.isFinite() && value >= 0) { "TTID không hợp lệ" }
                    sample.put("ttidMs", value)
                }
                samples.put(sample)
            }
            modes.put(mode)
        }
        val build = report.optJSONObject("context")?.optJSONObject("build")
        val target = report.getString("targetPackage")
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(target))
        return JSONObject().put("id", now.toString()).put("package", target).put("label", target)
            .put("version", report.getString("targetVersion")).put("versionCode", 0)
            .put("device", build?.optString("model", "không rõ") ?: "không rõ")
            .put("fingerprint", build?.optString("fingerprint", "") ?: "")
            .put("android", build?.optJSONObject("version")?.optInt("sdk", -1) ?: -1)
            .put("observeUi", false).put("startupSource", SOURCE).put("modes", modes)
            .put("protocol", "macrobenchmark-1.3.4; StartupTimingMetric; CompilationMode.Ignore")
            .put("status", if (samples.length() == (0 until samples.length()).count { samples.getJSONObject(it).has("ttidMs") } && modes.length() == 3) "completed" else "completed_with_errors")
            .put("ttidSamples", samples).put("samples", JSONArray()).put("importedReport", report)
            .put("note", "TTID lab · phiên bản app do tester ghi: ${report.getString("targetVersion")}")
    }
}
