package vn.startupbench

import org.json.JSONObject

/** Lab classification only. ActivityManager timing is not a Play Console measurement. */
data class StartupVitals(val mode: String, val attempted: Int, val measured: Int, val slow: Int) {
    val missing get() = attempted - measured
    val thresholdMs get() = threshold(mode)
    val slowPercent: Double? get() = if (measured == 0) null else slow * 100.0 / measured

    companion object {
        fun threshold(mode: String): Long = when (mode) {
            "COLD" -> 5000L
            "WARM" -> 2000L
            "HOT" -> 1500L
            else -> error("Kiểu khởi động không hợp lệ: $mode")
        }

        fun from(session: JSONObject, mode: String): StartupVitals {
            if (session.optString("startupSource") == MacrobenchmarkReport.SOURCE) {
                val samples = session.getJSONArray("ttidSamples")
                val attempts = (0 until samples.length()).map { samples.getJSONObject(it) }.filter { it.getString("mode") == mode }
                val values = attempts.filter { it.has("ttidMs") }.map { it.getDouble("ttidMs") }
                return StartupVitals(mode, attempts.size, values.size, values.count { it > threshold(mode) })
            }
            val attempts = session.androidResults().filter { it.mode == mode }
            val values = attempts.filter { it.valid && it.stateVerified }.mapNotNull { it.totalMs }
            return StartupVitals(mode, attempts.size, values.size, values.count { it > threshold(mode) })
        }
    }
}
