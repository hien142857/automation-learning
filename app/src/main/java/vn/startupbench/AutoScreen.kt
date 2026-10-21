package vn.startupbench

import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

data class ScreenElement(val kind: String, val id: String, val label: String, val actionable: Boolean)
data class ScreenCandidate(val reason: String, val signature: String = "", val labels: Int = 0, val actions: Int = 0) {
    val eligible get() = reason == "candidate"
    fun json() = JSONObject().put("reason", reason).put("signature", signature).put("labelCount", labels)
        .put("actionCount", actions).put("method", "stable-structure-v1").put("confidence", "heuristic")
}
object AutoScreen {
    fun evaluate(elements: List<ScreenElement>, complete: Boolean, blocked: Boolean): ScreenCandidate {
        if (blocked) return ScreenCandidate("ads_visible")
        if (!complete) return ScreenCandidate("ui_unreadable")
        if (elements.any { it.kind.contains("ProgressBar") ||
                Regex("(^|[/_])(splash|loading|loader)([_/]|$)").containsMatchIn(it.id.lowercase(Locale.ROOT)) ||
                it.label.trim().lowercase(Locale.ROOT) in setOf("loading", "loading…", "loading...", "đang tải", "đang tải…", "đang tải...") })
            return ScreenCandidate("loading_visible")
        val labeled = elements.filter { it.label.isNotBlank() }
        val count = labeled.map { it.label }.distinct().size
        val actions = elements.count { it.actionable }
        if (count < 3 || actions < 1) return ScreenCandidate("insufficient_content", labels = count, actions = actions)
        // Content remains in memory only; persist the hash, never entered text or raw UI labels.
        val canonical = elements.map { "${it.kind}|${it.id}|${it.label}|${it.actionable}" }.sorted().joinToString("\n")
        val signature = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return ScreenCandidate("candidate", signature, count, actions)
    }
}

object ObservationFailure {
    fun explain(proof: JSONObject): String = when {
        proof.optBoolean("interrupted") -> "Mất điều kiện quan sát (màn hình khóa, tắt màn hình hoặc lỗi Trợ năng). Mở khóa và kiểm tra quyền Trợ năng rồi thử lại."
        proof.optBoolean("blockerVisible") -> "Vẫn còn dấu hiệu quảng cáo khi hết thời gian chờ. Đóng/bỏ qua ads trong app đích rồi thử lại."
        proof.optString("foregroundPackage") != proof.optString("targetPackage") -> "Không quan sát được cửa sổ app đích: app có thể đã đóng, bị che hoặc chuyển sang app khác. Mở lại app, giữ nó ở foreground và thử lại."
        proof.optString("discoveryReason") == "loading_visible" -> "Vẫn thấy dấu hiệu Splash/đang tải. Kiểm tra mạng và app đích rồi thử lại; không dùng thời gian Splash làm kết quả."
        proof.optString("discoveryReason") == "ui_unreadable" || !proof.optBoolean("blockerScanComplete", true) -> "Trợ năng không đọc đủ giao diện (có thể do Canvas/WebView bảo vệ hoặc cây UI quá lớn). Kiểm tra quyền; nếu app vẫn không cung cấp nội dung thì không thể trả chỉ số giao diện tự động."
        proof.optString("discoveryReason") == "insufficient_content" && proof.optJSONObject("criterion")?.optString("mode") == "auto-discovery" -> "Chưa đủ dấu hiệu nội dung để chọn mốc: cần ít nhất 3 nhãn khác nhau và 1 thành phần có thể thao tác. Màn hình trống, Splash ít nội dung hoặc UI không cung cấp nhãn đều có thể gây ra tình trạng này."
        proof.optJSONObject("criterion")?.optString("mode") == "auto-discovery" -> "Giao diện chưa giữ nguyên đủ 1,5 giây trong thời gian chờ. Nội dung/đồng hồ/animation có thể đang thay đổi liên tục; chưa có mốc ổn định để trả kết quả."
        else -> "Không thấy mốc đã cấu hình ổn định trong thời gian chờ. Kiểm tra chữ/resource ID hoặc xóa mốc để dùng tự phát hiện."
    }
}
