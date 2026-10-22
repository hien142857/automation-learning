# Kiểm thử startup cho Android vitals

Mục tiêu là phát hiện startup chậm bằng **TTID (time to initial display)** trên thiết bị test và so sánh các build. Tỷ lệ lab không phải tỷ lệ người dùng trong 28 ngày trên Play Console; phân bố thiết bị, phiên bản, cache, dữ liệu, nguồn mở app và điều kiện chạy khác nhau.

| Kiểu | Chậm khi TTID vượt | Chuẩn bị bởi Macrobenchmark |
| --- | --- | --- |
| Cold | 5.000 ms | Tiến trình chưa chạy |
| Warm | 2.000 ms | Tiến trình còn, tạo lại Activity |
| Hot | 1.500 ms | Activity còn trong bộ nhớ, đưa về foreground |

Đúng bằng ngưỡng chưa tính là chậm. Tỷ lệ lab = số mẫu vượt ngưỡng / số mẫu TTID đo được × 100. Mẫu thiếu, lỗi và kiểu chưa chạy hiển thị riêng; không thay bằng 0. Chênh lệch tỷ lệ A/B tính bằng **điểm phần trăm**. Ít mẫu không chứng minh tỷ lệ production tốt hơn.

## Luồng chính: TTID bằng Macrobenchmark / Perfetto

1. Dùng điện thoại thật Android 11+, mở khóa, kết nối ADB. Cài **app đích** có launcher, bản gần release, `debuggable=false` và bật `<profileable android:shell="true"/>` trong application. App đích do nhóm phát triển cung cấp; không tự sửa/re-sign APK production để vượt kiểm tra. Runner giữ các kiểm tra môi trường mặc định của AndroidX; không suppress debuggable/emulator/low-battery errors.
2. Chuẩn bị dữ liệu/tài khoản test, quyền, onboarding theo cùng kịch bản ở mỗi build. Lưu công việc: Cold dừng tiến trình, Warm tạo lại Activity. Tắt quan sát Trợ năng của Startup Bench và các overlay để tránh tải đo phụ. Không thao tác vào app trong khoảng đo.
3. Dùng JDK 17/21, SDK 35, Build Tools 35.0.0. Từ thư mục gốc repo chạy (thay package bằng app cần đo):

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.targetPackage=com.example.app \
  -Pandroid.testInstrumentationRunnerArguments.iterations=20
```

Runner chạy riêng Cold/Warm/Hot bằng `StartupTimingMetric` và tạo Perfetto trace. `CompilationMode.Ignore()` giữ trạng thái biên dịch đã cài, không reset dữ liệu; ghi và giữ cách cài/khởi động/warmup giống nhau khi so sánh. Đây là quy trình launcher, không bao phủ mọi deep link/notification. Không nhập `targetPackage` thì test phải thất bại, không mặc định đo công cụ.

4. Kiểm tra kết quả instrumentation: cả ba test phải thực sự chạy và pass. Gradle thu JSON `*-benchmarkData.json` và trace trong `macrobenchmark/build/outputs/connected_android_test_additional_output/`. Chọn file của **lần chạy hiện tại, đúng thiết bị**, không gộp các file cũ. Khi có lỗi, xem JUnit XML và trace; không suy diễn lỗi là một lần mở nhanh.
5. Tạo báo cáo từ file JSON vừa thu (package và version phải khớp app đã đo; version do tester ghi):

```bash
python3 scripts/startup_vitals_report.py /path/current-benchmarkData.json \
  --package com.example.app --version '1.2.3-build456' \
  --output /tmp/ttid-summary.json --csv /tmp/ttid-summary.csv
```

Script chỉ đọc `timeToInitialDisplayMs.runs` của `StartupVitalsBenchmark`, không dùng trung bình, WaitTime hay mốc UI để phân loại. Giữ thời gian thập phân. Báo cáo thiếu kiểu/mẫu trả exit code 2 và vẫn lưu dữ liệu để điều tra; không có test đúng thì thất bại. Một test fail trước khi ghi JSON có thể không xuất hiện: luôn đối chiếu kết quả instrumentation, không chỉ dựa vào file báo cáo.

6. Chép `ttid-summary.json` vào điện thoại; trong Startup Bench chọn **Nhập báo cáo TTID · Macrobenchmark**. Lịch sử hiển thị nguồn **TTID / Perfetto**, tỷ lệ chậm theo từng kiểu, số mẫu thiếu; có thể chọn hai phiên cùng package để so sánh và xuất CSV. Giữ file JSON gốc, trace, APK/build ID và báo cáo cùng test case.

App không suy ra TTFD từ UI ổn định. `StartupTimingMetric` có thể xuất TTFD nếu app đích gọi `reportFullyDrawn`; chỉ TTID được dùng cho báo cáo startup chậm này. Ads/network sau khung hình đầu thuộc phép đo trải nghiệm nội dung riêng, không kéo TTID đến lúc đóng ads.

## Luồng phụ trên điện thoại: Shizuku

Chọn app và chạy Cold/Warm/Hot như trước. Mặc định **không cần Accessibility**. Số đo `ActivityManager TotalTime` được ghi rõ là **tham khảo**, không phải TTID Perfetto/Android vitals. Hot có thể không có TotalTime: giữ mẫu thiếu, không dùng WaitTime hay tự gán 0.

Tuỳ chọn **Đo thêm thời gian đến nội dung UI** giữ quy trình cũ, nhóm có/không ads và bằng chứng quan sát. UI timeout/ads không làm mất số đo Android hợp lệ trong bảng startup riêng. Không so sánh chéo nguồn TTID với TotalTime; ứng dụng không tính chênh lệch tỷ lệ cho hai nguồn khác nhau.

## Xác minh trước khi giao tester

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :macrobenchmark:assembleBenchmark
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Build/test JVM không thay thế chạy trên thiết bị: cần kiểm chứng cả ba kiểu startup, trace chứa app đích, thử build chậm có chủ đích quanh các ngưỡng, hot thiếu số đo, app thoát/crash, nhập báo cáo, lưu lịch sử và CSV. Chạy nhiều phiên trên cùng thiết bị và nhiều nhóm thiết bị đại diện. Đối chiếu thay đổi production bằng Android vitals sau phát hành, không cam kết tỷ lệ lab khớp bảng 28 ngày.

Tài liệu: [Android startup / TTID](https://developer.android.com/topic/performance/vitals/launch-time), [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [StartupTimingMetric](https://developer.android.com/reference/kotlin/androidx/benchmark/macro/StartupTimingMetric).
