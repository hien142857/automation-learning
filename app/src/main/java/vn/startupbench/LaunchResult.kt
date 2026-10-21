package vn.startupbench

data class LaunchResult(val mode: String, val state: String?, val totalMs: Long?, val waitMs: Long?, val error: String?, val raw: String) {
    val valid: Boolean get() = error == null && totalMs != null
    companion object {
        fun parse(mode: String, output: String): LaunchResult {
            fun field(name: String) = Regex("(?m)^${Regex.escape(name)}:\\s*([^\\r\\n]+)").find(output)?.groupValues?.get(1)?.trim()
            val state = field("LaunchState")?.uppercase()
            val total = field("TotalTime")?.toLongOrNull()?.takeIf { it >= 0 }
            val wait = field("WaitTime")?.toLongOrNull()?.takeIf { it >= 0 }
            val error = when {
                field("BenchError") != null -> field("BenchError")
                field("Status") != "ok" -> "Android không báo Status: ok"
                state != mode -> "Yêu cầu $mode, Android báo ${state ?: "không có LaunchState"}"
                total == null -> "Android không cung cấp TotalTime hợp lệ; không thay bằng WaitTime"
                else -> null
            }
            return LaunchResult(mode, state, total, wait, error, output)
        }
        fun median(values: List<Long>): Double? {
            if (values.isEmpty()) return null
            val sorted = values.sorted(); val n = sorted.size
            return if (n % 2 == 1) sorted[n / 2].toDouble() else sorted[n / 2 - 1] / 2.0 + sorted[n / 2] / 2.0
        }
    }
}
