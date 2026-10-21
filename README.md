# Startup Bench

Ứng dụng Android dành cho tester đo startup của app khác bằng **Shizuku**, lưu lịch sử và so sánh các phiên bản. Kotlin, Android native Views, SQLite cục bộ; không gửi kết quả lên máy chủ. Android 11+ (API 30).

## Sử dụng

1. Cài APK `app/build/outputs/apk/debug/app-debug.apk` lên điện thoại test.
2. Cài [Shizuku](https://shizuku.rikka.app/download/), bật Developer options và Wireless debugging, ghép nối và Start Shizuku. Có thể cần Start lại sau khi reboot.
3. Mở Startup Bench → **Kết nối / hướng dẫn** → cấp quyền Shizuku. Cho phép thông báo để dễ quay lại / dừng phiên đo.
4. Chọn app có launcher trong hồ sơ hiện tại hoặc nhập package. Không hỗ trợ đo chính Startup Bench, Shizuku hoặc app trong hồ sơ khác.
5. Chọn Cold / Warm / Hot, số lần và ghi chú build. Bấm **Bắt đầu đo & lưu phiên**.
6. Giữ màn hình mở khóa, không tương tác với app đích trong lúc chạy. Các app được tự mở; quay lại Startup Bench hoặc chạm thông báo để xem tiến độ. Không tự đưa công cụ lên foreground sau mỗi mẫu vì sẽ ảnh hưởng phép đo.
7. Vào **Lịch sử**, chọn hai phiên cùng package và bấm so sánh. Phiên chọn đầu tiên là mốc. Xuất CSV từ chi tiết phiên.

**Lưu công việc trong app đích trước khi chạy:** Cold force-stop tiến trình, Warm xóa task / tạo lại Activity. Không xóa dữ liệu ứng dụng. Một số app có thể tự lưu / thay đổi dữ liệu khi được mở; dùng tài khoản và dữ liệu test.

### Nút bắt đầu bị khóa / kết nối dịch vụ đo

Từ bản 0.1.1, nút bị khóa có màu xám và hiện lý do ngay phía trên. Nút chỉ bật khi Shizuku đã chạy, đã cấp quyền, dịch vụ đo đã kết nối, đã chọn package và ít nhất một kiểu đo. Trạng thái “Đang kết nối dịch vụ đo” chưa đủ để bắt đầu, dù Shizuku đã được cấp quyền.

Nếu kết nối quá 15 giây, công cụ hiển thị lỗi thay vì chờ vô hạn. Bấm **Kết nối / hướng dẫn** để gỡ phiên dịch vụ cũ và khởi tạo lại. Nếu vẫn không kết nối, kiểm tra quyền Startup Bench trong Shizuku → Authorized applications, khởi động lại Shizuku rồi mở lại công cụ. Cần kiểm tra log trên thiết bị để xác định lỗi khởi chạy dịch vụ cụ thể; thông báo timeout không tự xác định nguyên nhân.

### Điều hướng, chọn app và nhận xét so sánh (0.1.3)

- Thanh điều hướng dưới cùng có icon và trạng thái đang chọn cho **Đo startup** / **Lịch sử**. Hai mục này chuyển màn hình, tách biệt với các nút thực hiện thao tác.
- Popup chọn app mở ngay với vòng xoay loading. Việc đọc app và tên hiển thị chạy trên luồng nền; tìm kiếm dùng tên / package đã tải, không đọc lại toàn bộ danh sách mỗi ký tự. Có trạng thái trống, thử lại khi lỗi; đóng popup hủy tác vụ và bỏ qua kết quả đến muộn.
- Chọn hai phiên cùng package: phiên chọn đầu là **A · Mốc**, phiên thứ hai là **B · Đối chứng**. Màn so sánh có màu riêng cho A/B, thanh thời gian chung thang cho từng kiểu đo và nút **Đổi A ↔ B**. Chênh lệch = median B trừ A; âm là B nhanh hơn. Màu xanh/đỏ đi kèm chữ và mũi tên để không phụ thuộc vào màu sắc.
- Nhận xét tối ưu là gợi ý thận trọng, không phải kiểm định thống kê: cần tối thiểu 5 mẫu hợp lệ mỗi bên, đủ lượt và không có lỗi ở kiểu đo đang xét, cùng metadata thiết bị / Android / Activity / quy trình / chế độ nguồn. Nhiệt độ đầu phiên lệch từ 3°C cũng tạo cảnh báo khác điều kiện.
- Chỉ nêu dấu hiệu nhanh/chậm khi chênh lệch đạt cả 5% và 10 ms, đồng thời lớn hơn 2 lần MAD lớn nhất của hai nhóm (median độ lệch tuyệt đối so với median). Chênh lệch nhỏ, dao động lớn, thiếu mẫu, mốc 0 ms hoặc khác điều kiện sẽ có lời khuyên đo lại thay vì khẳng định tối ưu. Một kiểu đo chậm hơn ngăn kết luận tối ưu đồng đều.
- Nên đo lại 10 lần với cùng dữ liệu app, nhiệt độ, nguồn và mạng; cải thiện startup không đại diện cho tốc độ tải dữ liệu hoàn tất.

## Số đo và giới hạn

- Dùng `am start -W --user <current-user> -n <launcher-component>` với quyền shell từ Shizuku UserService. Chỉ truyền danh sách đối số đã kiểm tra, không chạy chuỗi shell tùy ý.
- **Cold:** force-stop package, chờ 1.5 giây, đo mở Activity.
- **Warm:** mở app để chuẩn bị, HOME, chờ 1.5 giây, đo với `-f 0x10008000` (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`). Android không có tùy chọn shell `--activity-new-task`. Đây là kịch bản tái tạo task / Activity, không mô phỏng mọi đường quay lại app.
- **Hot:** mở app để chuẩn bị, HOME, chờ 1.5 giây, đo đưa launcher Activity trở lại.
- Bước HOME trong Warm / Hot là chủ động. Công cụ sẽ mở lại app sau khoảng chờ; việc về Home ở bước này chưa đủ để kết luận app đích crash. Nếu app tự đóng sau khi đã mở lại, cần log crash của app đích để chẩn đoán.
- Có khoảng nghỉ 1.5 giây giữa các mẫu. App có launcher điều hướng / trampoline / nhiều task có thể không cho trạng thái mong muốn.
- Mẫu chỉ hợp lệ nếu Android trả `Status: ok`, `LaunchState` đúng yêu cầu và `TotalTime >= 0`. Trạng thái sai, thiếu thời gian, timeout, mất quyền là lỗi được giữ trong log, **không tính vào median/min/max**. Không thay TotalTime bằng WaitTime, không coi thiếu thời gian là 0. Hot có thể không có số đo hợp lệ trên một số ROM.
- Từ bản 0.1.2: lỗi lệnh chỉ bỏ phần còn lại của kiểu đo đó và tiếp tục kiểu kế tiếp. Mất kết nối, lỗi quyền hoặc khóa màn hình dừng toàn phiên. Phiên đã kết thúc nhưng có mẫu lỗi mang trạng thái `completed_with_errors`; các mẫu chưa chạy hiển thị rõ, không coi là 0 ms. Log đầy đủ ở Chi tiết / CSV; thông báo ngoài màn hình được rút gọn, kể cả lịch sử từ bản cũ.
- Đây là thời gian Activity Manager báo cho quá trình mở Activity. Không phải đo hoàn tất request mạng, tải dữ liệu, hoặc fully drawn / fully interactive của mọi ứng dụng. Những chỉ số đó cần app đích được instrument hoặc có kịch bản automation riêng.
- Cold là process cold, không xóa disk cache / page cache và không phải lần cài đặt / mở app đầu tiên.
- Đo khi màn hình mở khóa, cùng thiết bị, trạng thái pin, nhiệt độ, cấu hình mạng và dữ liệu app. Không chạy cùng profiler / tác vụ nặng. Đây là công cụ so sánh hỗ trợ tester, chưa thay thế Android Macrobenchmark hoặc Perfetto cho kết luận hiệu năng chính thức.
- Dịch vụ foreground giữ collector sống khi app khác ở foreground. Nếu Android / người dùng kết thúc tiến trình, những mẫu đã lưu vẫn còn, phiên có thể mang trạng thái `running` chưa hoàn tất; không coi đây là phiên đã chạy đủ.

Lịch sử lưu phiên bản / version code, package / Activity, thiết bị / build fingerprint, Android, pin / nhiệt độ ban đầu, chế độ tiết kiệm pin, quy trình, ghi chú, trạng thái phiên và log từng mẫu. CSV có log gốc để đối chứng; log / ghi chú có thể chứa thông tin app test, hãy kiểm tra trước khi chia sẻ. Dữ liệu bị xóa khi gỡ app hoặc clear data.

## Phát triển

Cần JDK 17 hoặc 21, Android SDK Platform 35, Build Tools 35.0.0. Mở project bằng Android Studio hoặc đặt `ANDROID_HOME` / `sdk.dir` trong `local.properties` (không commit file này).

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Trong cloud workspace hiện tại:

```bash
export JAVA_HOME=/workspace/toolchains/jdk-21.0.8+9
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export GRADLE_USER_HOME=/workspace/toolchains/gradle-user
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK debug dành cho thử nghiệm nội bộ. Chưa có signing / cấu hình phân phối production hoặc Play Store; việc dùng foreground `specialUse` cần đánh giá chính sách nếu phát hành lên Play.

## Checklist trên điện thoại thật

- Shizuku chưa chạy / bị từ chối quyền: có hướng dẫn, không cho bắt đầu đo.
- Cấp quyền, tìm app theo tên / package; package không tồn tại hiển thị lỗi.
- Chạy 3 kiểu × 3 lần trên app test: đối chiếu log gốc, kiểm tra LaunchState, thời gian và số mẫu hợp lệ. Hot / Warm không được âm thầm tính thành Cold.
- Chuyển về công cụ trong khi đang đo, xoay màn hình, bấm dừng từ thông báo: không tạo phiên trùng, giữ các mẫu đã thu.
- Khóa màn hình / dừng Shizuku: phiên dừng hoặc báo lỗi, không tạo kết quả hợp lệ giả.
- Khởi động lại công cụ: lịch sử còn. Cập nhật app đích và chạy lại: lịch sử giữ version code cũ / mới riêng biệt.
- So sánh cùng package: chênh lệch là đối chứng trừ mốc; phiên không có mẫu không xuất phần trăm. Khác thiết bị / build / quy trình có cảnh báo.
- Xuất CSV, mở lại kiểm tra log nhiều dòng và ký tự tiếng Việt.

Unit tests kiểm tra parser số đo, lỗi trạng thái, thiếu / âm TotalTime, 0 ms thực, line endings, median, điều kiện bật nút, cờ Intent cho Warm và xử lý log lỗi dài / tiếp tục kiểu đo sau lỗi lệnh. Kiểm thử nhận xét so sánh bao gồm đảo A/B, thiếu mẫu, dao động, khác điều kiện, mốc 0 và kết quả trái chiều giữa các kiểu đo. Robolectric kiểm tra chuyển tab, loading trong khi tác vụ nền chưa hoàn tất và màu / nhận xét sau khi đổi A/B. Build và các kiểm thử này không xác minh quyền Shizuku, hành vi ROM hoặc độ chính xác trên phần cứng thật.
