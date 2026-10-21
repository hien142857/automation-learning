package vn.startupbench

data class StartReadiness(val enabled: Boolean, val message: String) {
    companion object {
        fun evaluate(binderAvailable: Boolean, permissionGranted: Boolean, serviceAlive: Boolean,
                     running: Boolean, packageName: String, hasModes: Boolean, connectionError: String): StartReadiness = when {
            running -> StartReadiness(false, "Phiên đo đang chạy. Có thể dừng sau mẫu hiện tại.")
            !binderAvailable -> StartReadiness(false, "Chưa chạy Shizuku. Bấm Kết nối / hướng dẫn ở phía trên.")
            !permissionGranted -> StartReadiness(false, "Chưa cấp quyền Shizuku. Bấm Kết nối / hướng dẫn để cấp quyền.")
            connectionError.isNotBlank() -> StartReadiness(false, "$connectionError Bấm Kết nối / hướng dẫn để thử lại.")
            !serviceAlive -> StartReadiness(false, "Đang kết nối dịch vụ đo. Nếu quá 15 giây, hãy thử kết nối lại.")
            packageName.isBlank() -> StartReadiness(false, "Chọn ứng dụng hoặc nhập package trước khi đo.")
            !hasModes -> StartReadiness(false, "Chọn ít nhất một kiểu Cold / Warm / Hot.")
            else -> StartReadiness(true, "Sẵn sàng đo. Giữ màn hình mở khóa.")
        }
    }
}
