package vn.startupbench

import android.os.SystemClock
import org.json.JSONObject

data class GuidedSample(val raw: String, val evidence: JSONObject, val decision: String) {
    companion object {
        fun run(remote: IMeasureService, service: ReadinessObserver, criterion: ReadyCriterion,
                component: String, mode: String, userId: Int): GuidedSample {
            fun failure(message: String, evidence: JSONObject, raw: String = message): GuidedSample {
                evidence.put("failureReason", message)
                val reason = if (evidence.has("discoveryReason")) message.take(1200) else MeasurementFailure.short(message)
                val response = service.ask("Chưa thể trả kết quả mẫu đo",
                    "$reason\n\nMẫu này không được tính vào trung bình. Xử lý theo hướng dẫn rồi thử lại, bỏ qua kiểu đo này hoặc dừng phiên.",
                    listOf("Lưu mẫu lỗi và thử lại", "Lưu và bỏ qua kiểu đo này", "Dừng phiên để kiểm tra"))
                if (response < 0 && !BenchEngine.cancel) evidence.put("observerError", service.evidence(0).optString("observerError"))
                return GuidedSample(raw, evidence, when (response) { 0 -> "retry"; 1 -> "skip"; else -> "stop" })
            }
            if (mode != "COLD") {
                var prepared = false
                val opening = remote.launchOnly(component, "HOT", userId)
                if (opening.startsWith("BenchError:")) return failure(opening, JSONObject().put("status", "preparation_failed"))
                while (!prepared && !BenchEngine.cancel) {
                    if (!criterion.automatic && !service.allowInteraction("Chuẩn bị $mode: đóng ads, xử lý đăng nhập và tới màn hình đích. Sau đó bấm Kiểm tra mốc."))
                        return GuidedSample(opening, service.evidence(0).put("status", "preparation_failed"), "stop")
                    val start = SystemClock.uptimeMillis()
                    service.arm(criterion, "Đang kiểm tra mốc chuẩn bị $mode…")
                    val proof = service.awaitReady(start, criterion.timeoutMs)
                    if (proof.optString("status") != "marker_observed") return failure("Không thể chuẩn bị $mode. ${ObservationFailure.explain(proof)}", proof.put("status", "preparation_failed"), opening)
                    if (criterion.automatic) { prepared = true; continue }
                    prepared = service.ask("Xác nhận màn hình chuẩn bị", "Mốc UI đã xuất hiện. Nội dung đích đã dùng được và không còn ads che? Chỉ tiếp tục khi đúng; bước này chưa được tính thời gian.",
                        listOf("Đúng, tiếp tục đo", "Chưa, để tôi xử lý tiếp", "Dừng phiên")) .let {
                        if (it == 2 || it < 0) BenchEngine.cancel = true
                        it == 0
                    }
                }
            }
            if (BenchEngine.cancel) return GuidedSample("BenchError: Người dùng dừng", JSONObject().put("status", "cancelled"), "stop")
            val prep = remote.prepareLaunch(component, mode, userId)
            if (prep != "Prepared: ok") return failure(prep, JSONObject().put("status", "preparation_failed"))
            service.arm(criterion, if (criterion.automatic) "Đang đo $mode. Nếu có ads, chỉ cần đóng/bỏ qua trong app. Tự tiếp tục khi mốc nội dung ổn định; không cần bấm kiểm tra mốc." else "Đang đo $mode. Nếu có ads/thao tác, hãy đánh dấu và xử lý; công cụ sẽ hỏi xác nhận mốc.")
            val raw = remote.launchOnly(component, mode, userId)
            val launchAt = EvidencePolicy.launchClock(raw)
            val androidResult = LaunchResult.parse(mode, raw)
            if (!androidResult.stateVerified || launchAt == null) return failure(
                "Android chưa xác minh $mode: ${androidResult.error ?: "thiếu mốc đồng hồ bắt đầu"}",
                JSONObject().put("status", "android_invalid"), raw)
            val rejectedCandidates = org.json.JSONArray()
            while (!BenchEngine.cancel) {
                val proof = service.awaitReady(launchAt, criterion.timeoutMs)
                if (rejectedCandidates.length() > 0) proof.put("assisted", true).put("recheckedAfterTesterAction", true)
                    .put("rejectedCandidates", org.json.JSONArray(rejectedCandidates.toString()))
                if (proof.optString("status") != "marker_observed") return failure("Không có kết quả sau ${criterion.timeoutMs / 1000} giây. ${ObservationFailure.explain(proof)}", proof, raw)
                if (criterion.automatic) {
                    proof.put("confirmationMethod", "automatic-marker-v1").put("automaticConfirmed", true)
                        .put("testerConfirmed", false)
                        .put("status", if (proof.optBoolean("assisted")) "assisted_ui" else "verified_ui")
                    if (!EvidencePolicy.launchAccepted(proof, raw) && !EvidencePolicy.launchAssisted(proof, raw))
                        return failure("Bằng chứng tự động chưa đủ: kiểm tra mốc UI, dấu hiệu ads và quyền quan sát; có thể tắt tự tiếp tục để xác nhận từng mẫu.", proof.put("status", "automatic_incomplete"), raw)
                    return GuidedSample(raw, proof, "next")
                }
                val confirmation = service.ask("Xác minh mẫu $mode",
                    "Đã quan sát mốc UI sau ${proof.optLong("observedUpperMs")} ms từ trước lệnh mở. Đây là thời điểm quan sát, không phải TTFD chuẩn.\n\nMốc có thực sự là nội dung đích, không còn Splash/ads che và dùng được không? Nếu có thao tác/ads, mẫu được lưu riêng. Thời gian bấm xác nhận cuối không dùng làm thời gian sẵn sàng.",
                    listOf("Đúng, không có ads / thao tác tay", "Đúng, nhưng có ads / thao tác tay", "Còn ads, để tôi xử lý tiếp", "Mốc cấu hình sai", "Dừng phiên"))
                proof.put("confirmationMethod", "tester-explicit").put("testerConfirmed", confirmation in 0..1).put("testerConfirmedUptimeMs", SystemClock.uptimeMillis())
                if (confirmation == 2) {
                    rejectedCandidates.put(proof.put("status", "occluded_candidate_rejected"))
                    if (!service.allowInteraction("Xử lý ads / màn hình chặn, sau đó bấm kiểm tra mốc. Đồng hồ gốc vẫn chạy; chờ/thao tác được tính trong nhóm hỗ trợ, không phải tốc độ thuần."))
                        return GuidedSample(raw, proof, "stop")
                    service.arm(criterion, "Kiểm tra lại mốc sau thao tác. Lưu nhóm có hỗ trợ; không reset đồng hồ mở app.")
                    continue
                }
                if (confirmation == 1) proof.put("assisted", true).put("testerDeclaredAdsOrAction", true)
                if (confirmation !in 0..1) {
                    proof.put("status", if (confirmation == 3) "rejected_by_tester" else "cancelled")
                    if (confirmation < 0 && !BenchEngine.cancel) proof.put("observerError", service.evidence(launchAt).optString("observerError"))
                    if (confirmation == 3) return failure("Tester báo mốc sai. Dừng phiên để sửa mốc nội dung đích; không xác nhận Splash.", proof, raw)
                    return GuidedSample(raw, proof, "stop")
                }
                proof.put("status", if (proof.optBoolean("assisted")) "assisted_ui" else "verified_ui")
                return GuidedSample(raw, proof, "next")
            }
            return GuidedSample(raw, service.evidence(launchAt).put("status", "cancelled"), "stop")
        }
    }
}
