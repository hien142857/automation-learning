package vn.startupbench

import kotlin.math.abs
import kotlin.math.max

enum class ComparisonVerdict { IMPROVED, REGRESSED, UNCHANGED, INCONCLUSIVE, MISSING }

data class ComparisonAssessment(
    val baseline: Double?, val candidate: Double?, val delta: Double?, val percent: Double?,
    val verdict: ComparisonVerdict, val title: String, val advice: String
) {
    companion object {
        fun assess(a: List<Long>, b: List<Long>, sameConditions: Boolean, complete: Boolean, observationIntervalMs: Double = 0.0): ComparisonAssessment {
            val before = LaunchResult.median(a); val after = LaunchResult.median(b)
            val delta = if (before != null && after != null) after - before else null
            val percent = if (delta != null && before!! > 0) delta / before * 100 else null
            fun result(verdict: ComparisonVerdict, title: String, advice: String) =
                ComparisonAssessment(before, after, delta, percent, verdict, title, advice)
            if (before == null || after == null) return result(ComparisonVerdict.MISSING, "Chưa có dữ liệu đối chứng", "Cần mẫu hợp lệ ở cả hai phiên cho kiểu đo này.")
            if (!sameConditions) return result(ComparisonVerdict.INCONCLUSIVE, "Điều kiện đo khác nhau", "Đo lại trên cùng thiết bị, hệ điều hành, chế độ nguồn và quy trình trước khi đánh giá tối ưu.")
            if (!complete) return result(ComparisonVerdict.INCONCLUSIVE, "Phiên đo chưa đủ hoặc có mẫu lỗi", "Chạy lại đủ số lượt và xử lý mẫu lỗi trước khi kết luận.")
            if (a.size < 5 || b.size < 5) return result(ComparisonVerdict.INCONCLUSIVE, "Cần thêm mẫu đo", "Chạy ít nhất 5 mẫu hợp lệ ở mỗi phiên; nên dùng 10 lần để đối chứng ổn định hơn.")
            if (before == 0.0) return result(ComparisonVerdict.INCONCLUSIVE, "Mốc A bằng 0 ms", "Không tính phần trăm từ mốc 0 ms. Đo lại và kiểm tra giới hạn thời gian Android cung cấp.")
            val absolute = abs(delta!!)
            if (absolute <= observationIntervalMs && observationIntervalMs > 0) return result(ComparisonVerdict.INCONCLUSIVE,
                "Chênh lệch nằm trong khoảng quan sát", "Chưa phân biệt được cải thiện với độ trễ phát hiện mốc UI. Không kết luận nhanh/chậm từ chênh lệch này.")
            if (absolute < max(10.0, before * .05)) return result(ComparisonVerdict.UNCHANGED, "Chưa thấy thay đổi rõ", "Chênh lệch nhỏ hơn ngưỡng tham khảo 5% hoặc 10 ms. Chưa đủ cơ sở nói bản B đã tối ưu hơn.")
            fun mad(values: List<Long>, median: Double): Double {
                val distances = values.map { abs(it.toDouble() - median) }.sorted()
                val n = distances.size
                return if (n % 2 == 1) distances[n / 2] else (distances[n / 2 - 1] + distances[n / 2]) / 2
            }
            if (absolute <= 2 * max(mad(a, before), mad(b, after))) return result(ComparisonVerdict.INCONCLUSIVE, "Kết quả còn dao động lớn", "Chênh lệch chưa vượt rõ độ dao động giữa các lần đo. Giữ nhiệt độ, pin và mạng tương đương rồi đo lại 10 lần.")
            return if (delta < 0) result(ComparisonVerdict.IMPROVED, "B có dấu hiệu nhanh hơn", "Kết quả cho thấy cải thiện ở kiểu đo này. Lặp lại một phiên cùng điều kiện để xác nhận trước khi chốt tối ưu.")
            else result(ComparisonVerdict.REGRESSED, "B có dấu hiệu chậm hơn", "Ưu tiên kiểm tra kiểu khởi động này. Đo lại cùng điều kiện; nếu tiếp tục chậm, ghi nhận hồi quy hiệu năng.")
        }

        fun overall(items: List<ComparisonAssessment>): String = when {
            items.any { it.verdict == ComparisonVerdict.REGRESSED } -> "B chưa tối ưu đồng đều: có kiểu đo chậm hơn. Xem các mục màu đỏ để ưu tiên kiểm tra."
            items.any { it.verdict == ComparisonVerdict.IMPROVED } && items.all { it.verdict in listOf(ComparisonVerdict.IMPROVED, ComparisonVerdict.UNCHANGED) } ->
                "B có dấu hiệu tối ưu hơn ở các mục màu xanh, các mục còn lại chưa thay đổi rõ. Nên chạy lại để xác nhận."
            items.any { it.verdict == ComparisonVerdict.IMPROVED } -> "B có dấu hiệu cải thiện ở một số kiểu đo; các kiểu còn lại chưa đủ cơ sở kết luận."
            items.isNotEmpty() && items.all { it.verdict == ComparisonVerdict.UNCHANGED } -> "Chưa thấy B tối ưu hơn rõ rệt so với A."
            else -> "Chưa đủ cơ sở kết luận B tối ưu hơn. Xem hướng dẫn bổ sung dữ liệu ở từng kiểu đo."
        }
    }
}
