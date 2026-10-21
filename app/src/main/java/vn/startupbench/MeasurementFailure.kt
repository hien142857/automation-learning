package vn.startupbench

object MeasurementFailure {
    fun short(raw: String): String {
        val option = Regex("Unknown option:?\\s*['\"]?([A-Za-z0-9_-]+)").find(raw)?.groupValues?.get(1)
        val message = when {
            option != null -> "Android không hỗ trợ tùy chọn $option."
            raw.contains("SecurityException", true) || raw.contains("Permission denied", true) -> "Android từ chối quyền thực hiện lệnh đo."
            raw.contains("DeadObjectException", true) -> "Mất kết nối dịch vụ Shizuku."
            else -> raw.lineSequence().firstOrNull { it.startsWith("BenchError:") }?.removePrefix("BenchError:")?.trim()
                ?: raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }
        // Handles historical sessions that stored the entire exception on one line.
        val clean = message.substringBefore(" at android.").substringBefore("\tat ").replace(Regex("\\s+"), " ").trim()
        return clean.ifEmpty { "Không đo được mẫu này." }.let { if (it.length <= 150) it else it.take(147) + "…" }
    }

    fun stopsSession(raw: String, serviceAlive: Boolean): Boolean = !serviceAlive ||
        raw.contains("BenchErrorType: CONNECTION") || raw.contains("BenchErrorType: SCREEN") ||
        raw.contains("SecurityException") || raw.contains("Permission denied", true) || raw.contains("DeadObjectException")
}
