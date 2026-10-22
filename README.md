# Startup Bench

## Kiểm thử Android vitals (0.5.0)

Luồng TTID mới dùng AndroidX Macrobenchmark / Perfetto để đo Cold/Warm/Hot và tính tỷ lệ startup chậm theo ngưỡng 5.000 / 2.000 / 1.500 ms. Có thể nhập báo cáo vào app để lưu lịch sử, so sánh cùng nguồn và xuất CSV. [Hướng dẫn tester và lệnh chạy](docs/android-vitals-testing.md).

Phần Shizuku trong APK đo ActivityManager TotalTime **tham khảo**, mặc định không cần Trợ năng. Quan sát nội dung UI là tuỳ chọn riêng; không dùng thời gian này thay TTID hoặc tỷ lệ 28 ngày của Play Console. Hướng dẫn quan sát UI bên dưới áp dụng khi bật tuỳ chọn đó.

Ứng dụng Android dành cho tester đo startup của app khác bằng **Shizuku**, lưu lịch sử và so sánh các phiên bản. Kotlin, Android native Views, SQLite cục bộ; không gửi kết quả lên máy chủ. Android 11+ (API 30).

## Sử dụng

1. Cài APK `app/build/outputs/apk/debug/app-debug.apk` lên điện thoại test.
2. Cài [Shizuku](https://shizuku.rikka.app/download/), bật Developer options và Wireless debugging, ghép nối và Start Shizuku. Có thể cần Start lại sau khi reboot.
3. Mở Startup Bench → **Kết nối / hướng dẫn** → cấp quyền Shizuku. Cho phép thông báo để dễ quay lại / dừng phiên đo.
4. Chọn app có launcher trong hồ sơ hiện tại hoặc nhập package. Không hỗ trợ đo chính Startup Bench, Shizuku hoặc app trong hồ sơ khác.
5. Không cần nhập mốc: để trống hai ô nâng cao để tự phát hiện giao diện. Có thể nhập mốc UI riêng nếu muốn dùng quy trình đã cấu hình (chữ/resource ID ổn định, duy nhất, không phải Splash hoặc dữ liệu riêng tư). Bật **Startup Bench · Quan sát mốc UI** trong Trợ năng. Nếu Android chặn APK cài ngoài, mở Thông tin ứng dụng → menu → Cho phép cài đặt bị hạn chế trước.
6. Chọn Cold / Warm / Hot, số lần và ghi chú build. Bấm **Bắt đầu đo & lưu phiên**.
7. Giữ màn hình mở khóa và làm theo bảng hướng dẫn trên app đích. Warm/Hot yêu cầu tới màn hình đích để chuẩn bị trước; chế độ tự động quan sát mốc để tiếp tục. Mặc định **Tự tiếp tục** bật: nếu có ads, đóng/bỏ qua ngay trong app đích, công cụ chờ mốc rồi tự lưu/đo tiếp. Có thể bấm **Có ads / cần thao tác** nếu công cụ chưa nhận ra. Nếu tắt tự tiếp tục, mỗi mẫu yêu cầu xác nhận nội dung không bị ads/Splash che.
8. Vào **Lịch sử**, chọn hai phiên cùng package và bấm so sánh. Phiên chọn đầu tiên là mốc. Xuất CSV từ chi tiết phiên.

**Lưu công việc trong app đích trước khi chạy:** Cold force-stop tiến trình, Warm xóa task / tạo lại Activity. Không xóa dữ liệu ứng dụng. Một số app có thể tự lưu / thay đổi dữ liệu khi được mở; dùng tài khoản và dữ liệu test.

### Nút bắt đầu bị khóa / kết nối dịch vụ đo

Từ bản 0.1.1, nút bị khóa có màu xám và hiện lý do ngay phía trên. Từ 0.2.0, nút chỉ bật khi Shizuku và dịch vụ đo đã kết nối, đã chọn package / kiểu đo, dịch vụ quan sát Trợ năng đã bật. Từ 0.4.0 không cần nhập mốc UI để bắt đầu. Trạng thái “Đang kết nối dịch vụ đo” chưa đủ để bắt đầu, dù Shizuku đã được cấp quyền.

Nếu kết nối quá 15 giây, công cụ hiển thị lỗi thay vì chờ vô hạn. Bấm **Kết nối / hướng dẫn** để gỡ phiên dịch vụ cũ và khởi tạo lại. Nếu vẫn không kết nối, kiểm tra quyền Startup Bench trong Shizuku → Authorized applications, khởi động lại Shizuku rồi mở lại công cụ. Cần kiểm tra log trên thiết bị để xác định lỗi khởi chạy dịch vụ cụ thể; thông báo timeout không tự xác định nguyên nhân.

### Điều hướng, chọn app và nhận xét so sánh (0.1.3)

- Thanh điều hướng dưới cùng có icon và trạng thái đang chọn cho **Đo startup** / **Lịch sử**. Hai mục này chuyển màn hình, tách biệt với các nút thực hiện thao tác.
- Popup chọn app mở ngay với vòng xoay loading. Việc đọc app và tên hiển thị chạy trên luồng nền; tìm kiếm dùng tên / package đã tải, không đọc lại toàn bộ danh sách mỗi ký tự. Có trạng thái trống, thử lại khi lỗi; đóng popup hủy tác vụ và bỏ qua kết quả đến muộn.
- Chọn hai phiên cùng package: phiên chọn đầu là **A · Mốc**, phiên thứ hai là **B · Đối chứng**. Màn so sánh có màu riêng cho A/B, thanh thời gian chung thang cho từng kiểu đo và nút **Đổi A ↔ B**. Từ 0.2.1, mặc định so sánh trung bình cộng; có thể chuyển sang trung vị (median). Chênh lệch = chỉ số B trừ A; âm là thời gian B thấp hơn A. Chọn cùng nhóm có hoặc không ads/thao tác cho cả hai phiên. Màu xanh/đỏ đi kèm chữ và mũi tên để không phụ thuộc vào màu sắc.
- Nhận xét tối ưu là gợi ý thận trọng, không phải kiểm định thống kê: cần tối thiểu 5 mẫu hợp lệ mỗi bên, đủ lượt và không có lỗi ở kiểu đo đang xét, cùng metadata thiết bị / Android / Activity / quy trình / chế độ nguồn. Nhiệt độ đầu phiên lệch từ 3°C cũng tạo cảnh báo khác điều kiện.
- Chỉ nêu dấu hiệu nhanh/chậm khi chênh lệch đạt cả 5% và 10 ms, đồng thời lớn hơn 2 lần độ phân tán lớn nhất của hai nhóm: độ lệch chuẩn mẫu khi chọn trung bình, MAD khi chọn trung vị (median độ lệch tuyệt đối so với median). Chênh lệch nhỏ, nằm trong khoảng trễ quan sát UI, dao động lớn, thiếu mẫu, mốc 0 ms hoặc khác điều kiện sẽ có lời khuyên đo lại thay vì khẳng định tối ưu. Một kiểu đo chậm hơn ngăn kết luận tối ưu đồng đều.
- Nên đo lại 10 lần với cùng dữ liệu app, nhiệt độ, nguồn và mạng; cải thiện startup không đại diện cho tốc độ tải dữ liệu hoàn tất.

## Tự phát hiện mốc và giải thích không có kết quả (0.4.0)

- Để trống chữ/resource ID: không cần cấu hình mốc, luôn tự tiếp tục. Công cụ đọc cây UI của app đích (tối đa 1.500 node), tìm giao diện có ít nhất 3 nhãn khác nhau và 1 thành phần thao tác, không có dấu hiệu ads/ProgressBar/loading/Splash đã nhận biết. Chữ trong ô nhập/password không được dùng. Dấu vết giao diện phải giữ nguyên ít nhất 1,5 giây; thay đổi nội dung làm bắt đầu lại khoảng ổn định, không đặt lại đồng hồ mở app.
- Chỉ số được gọi **thời gian đến giao diện ổn định tự phát hiện**, mức tin cậy heuristic. Đây không phải TTFD hoặc bằng chứng mọi nội dung đã tải xong. Màn hình đăng nhập/onboarding hay ads không nhận diện được vẫn có thể giống giao diện ổn định; không thể bảo đảm phân biệt ngữ nghĩa mọi APK. Nếu cần màn hình đích cụ thể, vẫn có ô nâng cao để cấu hình.
- Sau 60 giây mà không đủ điều kiện, popup chỉ rõ: còn dấu hiệu ads, còn Splash/loading, không đọc đủ cây UI, thiếu nhãn/thành phần thao tác, giao diện thay đổi liên tục, mất foreground hoặc mất điều kiện quan sát. Mô tả nêu bằng chứng quan sát và khả năng xảy ra, không tự khẳng định app đã crash. Tester có thể thử lại/bỏ qua/dừng; lý do lưu vào lịch sử/JSON/CSV. Mẫu lỗi không vào trung bình và không đổi thành 0 ms.
- Bằng chứng lưu `autoScreen` (hash SHA-256 của dấu vết UI, số nhãn/thành phần thao tác, phương pháp và mức tin cậy), không lưu chữ UI thô. Giữ thời gian quan sát đầu tiên của giao diện cuối đã ổn định, không tính thêm 1,5 giây chờ kiểm tra vào thời gian mốc. Thời gian chờ ads/thao tác vẫn nằm trong nhóm hỗ trợ.
- Khi hai phiên không cùng một dấu vết giao diện tự chọn, màn so sánh hiện cảnh báo và không kết luận tối ưu, dù vẫn hiển thị số liệu. Thay đổi nội dung động cũng có thể gây cảnh báo hoặc không có kết quả; đây là giới hạn có chủ đích để tránh so sánh các màn hình khác nhau.

## Tự tiếp tục sau quảng cáo (0.3.0)

- Mặc định tự tiếp tục ở phần 02. Từ 0.4.0 có thể để trống mốc để tự phát hiện; nhập chữ/resource ID nếu cần mốc nội dung riêng. Công cụ chờ mốc ổn định 1,5 giây, không còn dấu hiệu ads đang quan sát và cây UI đã được quét đủ trong giới hạn 1.500 node; sau đó tự lưu mẫu và chạy tiếp. Chuẩn bị Warm/Hot cũng tự tiếp tục, không cần bấm kiểm tra mốc hay xác nhận mỗi lần.
- Nhận diện **dấu hiệu** ads qua nhãn/mô tả cụ thể (Skip ad, Bỏ qua quảng cáo…), một số ID đóng ads phổ biến, hoặc chữ chính xác do tester nhập vào ô **Chữ trên ads cần chờ biến mất**. Nhãn này phải biến mất sau khi ads đóng, tránh chọn chữ thường trực ở màn hình chính. Công cụ không tự nhấn bỏ qua/đóng/quảng cáo.
- Tester chỉ đóng/bỏ qua ads trong app đích. Công cụ quan sát liên tục; khi ads/mốc mất đi thì hủy trạng thái sẵn sàng cũ, chờ nội dung ổn định lại. Phát hiện dấu hiệu ads hoặc thao tác sẽ lưu nhóm có hỗ trợ. Giữ nguyên đồng hồ từ trước lệnh mở app, bao gồm thời gian chờ/thao tác, không đặt lại sau ads và không coi đó là startup thuần.
- Bằng chứng ghi `confirmationMethod=automatic-marker-v1`, `automaticConfirmed`, dấu hiệu ads, trạng thái quét và sự kiện thao tác. Không giả lập `testerConfirmed=true`. Mẫu tự động và mẫu tester xác nhận dùng quy trình/metadata khác nhau nên không kết luận tối ưu khi trộn điều kiện.
- Không nhận diện chắc chắn mọi quảng cáo: Canvas/WebView bảo vệ, SDK lạ hoặc nội dung nằm sau lớp che có thể thiếu dấu hiệu trợ năng. Không ghi nhận dấu hiệu không chứng minh chắc chắn không có ads. Hãy thử mốc/nhãn trên app đích; nếu không đáng tin cậy, tắt tự tiếp tục để dùng xác nhận từng mẫu. Chỉ có APK không đủ để suy ra TTFD chuẩn của mọi ứng dụng.
- Không thấy mốc, cây UI không quét đủ hoặc mất điều kiện trong 60 giây thì lưu lỗi và hỏi thử lại/bỏ qua/dừng, không tạo số đo thành công. Cần kiểm tra trên điện thoại thật với SDK quảng cáo của app đang test.

## Thống kê rõ từng nhóm (0.2.1)

- Mỗi kiểu Cold/Warm/Hot hiển thị riêng hai nhóm **Không ads/thao tác** và **Có ads/thao tác**, với trung bình cộng, trung vị, nhỏ nhất/lớn nhất, số mẫu và thời gian từng lần. Trung bình cộng = tổng thời gian các mẫu trong nhóm / số mẫu; không trộn hai nhóm.
- Có một mẫu cũng hiển thị số liệu; dưới 5 mẫu vẫn có trung bình và chênh lệch A/B, kèm khuyến nghị đo thêm. Không có mẫu thì ghi rõ chưa có mẫu, không gán 0. Mẫu thiếu bằng chứng không được tính; số lượt thử, mẫu xác nhận và mẫu thiếu bằng chứng hiển thị riêng.
- Android TotalTime có trung bình và số mẫu riêng vì một mẫu UI hợp lệ có thể không có TotalTime. Lịch sử cũ có đủ bằng chứng được tính lại từ từng mẫu đã lưu khi mở; không suy ra trung bình từ median.
- Ví dụ ba lần 1.000, 2.000 và 9.000 ms: trung bình **4.000 ms**, trung vị **2.000 ms**. Nhóm có ads/thao tác bao gồm thời gian chờ và xử lý của tester, nên cần giữ cùng kịch bản khi đối chiếu.

## Phép đo có bằng chứng (0.2.0)

- **Hai chỉ số tách biệt:** Android `TotalTime` do Activity Manager báo (có thể chỉ tới Splash), và thời gian **quan sát mốc UI** kể từ mốc `SystemClock.uptimeMillis()` ngay trước khi khởi chạy tiến trình lệnh `am`. Cả hai không được gắn nhãn TTFD chuẩn.
- **Cold:** force-stop, HOME, chờ 1.5 giây, arm quan sát rồi mở app. **Warm/Hot:** mở app để chuẩn bị; tester xử lý ads / đăng nhập đến nội dung đích, chờ mốc ổn định (hoặc bấm kiểm tra mốc và xác nhận khi tắt tự tiếp tục); sau đó HOME, chờ 1.5 giây và mở lại. Warm dùng `-f 0x10008000` để tạo lại task, Hot không xóa task. Không đo mọi kịch bản warm start và không xóa disk cache trong cold start.
- Android phải trả `Status: ok`, `LaunchState` đúng kiểu yêu cầu. Nếu không đúng, popup giải thích và cho thử lại / bỏ qua / dừng. Không tự coi Warm thành Cold. Hot thiếu `TotalTime` vẫn có thể đo mốc UI nếu có LaunchState đúng và đủ bằng chứng; Android TotalTime để trống, không tự gán 0 hoặc thay bằng WaitTime.
- Trợ năng chỉ ghi nhận một node DUY NHẤT, visible, enabled, không phải password, khớp chính xác chữ / resource ID đã cấu hình trong window ứng dụng đang active của package đích. Nếu nhập cả chữ và ID, cả hai phải khớp cùng node. Mốc phải tồn tại ổn định ít nhất 1.500 ms khi tự tiếp tục, hoặc 500 ms khi xác nhận tay. Không thấy node trong 60 giây, thiếu quyền, màn hình khóa hoặc mất điều kiện thì không tạo mẫu xác minh giả.
- Quan sát theo chu kỳ danh nghĩa 200 ms, có thể chậm hơn do IPC / UI / tải thiết bị. Bằng chứng giữ mốc lần trước chưa thấy, lần đầu thấy, thời điểm xác nhận ổn định và khoảng cách scan lớn nhất. Thời gian mốc UI là mốc QUAN SÁT (cận trên), không bảo đảm chính xác tới mili giây. Cặp `observedLowerMs` / `observedUpperMs` mô tả khoảng giữa các lần quan sát, không phải cam kết sai số cố định.
- Khi tắt tự tiếp tục, tester phải xác nhận nội dung đích dùng được và không bị ads/Splash che; không dựa riêng vào node vốn có thể nằm phía sau lớp quảng cáo. Mốc sai, ads không được khai báo hoặc xác nhận sai có thể làm kết quả sai. Cần kiểm tra mốc và quy trình trên từng app; công cụ không tự hiểu nội dung kinh doanh của mọi APK.
- Sự kiện bấm/nhấn giữ/cuộn, thao tác đánh dấu ads và khai báo của tester được lưu. Trợ năng có thể không phát đủ sự kiện đối với Canvas/WebView, nên chế độ xác nhận tay có popup khai báo lại có ads/thao tác hay không; chế độ tự động ghi rõ giới hạn nhận diện. Không ghi nội dung nhập, không chụp màn hình, không tự bấm quảng cáo.
- `verified_ui`: đúng trạng thái Android + đúng mốc + ổn định + đúng package + xác nhận hợp lệ (tự động hoặc tester) + không ghi nhận ads/thao tác/gián đoạn. Nhóm này được dùng để nhận xét dấu hiệu cải thiện startup. `assisted_ui`: có mốc và xác nhận hợp lệ nhưng có dấu hiệu ads/thao tác, hiển thị nhóm riêng; bao gồm thời gian ads/chờ/thao tác, không đại diện tốc độ khởi động thuần. Có thể chọn so sánh riêng nhóm assisted để đối chiếu trải nghiệm có ads/thao tác; app hiển thị chênh lệch nhưng không kết luận tối ưu mã nguồn từ nhóm này. Thời gian bấm xác nhận sau cùng được lưu riêng và KHÔNG dùng làm thời gian đến mốc.
- Trong chế độ xác nhận tay, nếu mốc có thể nằm sau ads, chọn **Còn ads, để tôi xử lý tiếp** để tiếp tục cùng đồng hồ mở ban đầu, không mở lại app. Khi xử lý xong, bấm kiểm tra lại mốc. Lần kiểm tra lại được lưu bắt buộc vào nhóm assisted, bao gồm thời gian chờ / thao tác / yêu cầu kiểm tra lại của tester. Những ứng viên mốc bị ads che được giữ trong bằng chứng nhưng không làm kết quả thành công. Quá 60 giây từ lệnh mở thì timeout, không gia hạn ngầm.
- Timeout / sai mốc / sai trạng thái giữ raw log và bằng chứng rồi hỏi **thử lại, bỏ qua kiểu đo hoặc dừng**. Thử lại không ghi đè mẫu lỗi trước. Dữ liệu cũ từ 0.1.x được giữ lại nhưng thiếu bằng chứng UI sẽ không vào thống kê mới. Không trộn các mốc / quy trình khác nhau khi so sánh.
- Có thể đọc bằng chứng JSON trong Chi tiết và CSV: mốc cấu hình, clock, package foreground, timestamps quan sát, xác nhận tester, nhóm mẫu, sự kiện thao tác. Dữ liệu lưu cục bộ, mất khi gỡ app / clear data; kiểm tra nội dung trước khi chia sẻ.
- Trợ năng không đọc được mọi app/Surface/Canvas hoặc xác định chắc chắn mọi lớp che. Khi không xác minh được phải báo không có dữ liệu, không dùng xác nhận tay làm phép đo thời gian chính xác giả. Nếu cần TTID/TTFD chuẩn hoặc trace lifecycle chính xác, cần Macrobenchmark/Perfetto và tín hiệu từ app đích.
- Giữ cùng thiết bị, nhiệt độ, nguồn, mạng, dữ liệu và chiến lược ads. Không dùng các con số này để khẳng định mọi chức năng đã tải hoàn tất. Mất tiến trình công cụ có thể để lại phiên `running` chưa hoàn tất; không coi là đã chạy đủ.

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
- Cấp quyền, tìm app theo tên / package; package không tồn tại hiển thị lỗi. Bật quan sát Trợ năng, chọn mốc duy nhất không phải Splash. Mốc trống không cho bắt đầu.
- Thử mốc sai / app kẹt Splash: có popup timeout, không có mẫu xác minh. Đóng ads / thao tác tay: chỉ có nhóm assisted, không trộn nhóm tự động. Từ chối xác nhận: giữ log và cho thử lại / bỏ qua / dừng.
- Chạy 3 kiểu × 3 lần trên app test: đối chiếu log gốc, kiểm tra LaunchState, thời gian và số mẫu hợp lệ. Hot / Warm không được âm thầm tính thành Cold.
- Chuyển về công cụ trong khi đang đo, xoay màn hình, bấm dừng từ thông báo: không tạo phiên trùng, giữ các mẫu đã thu.
- Khóa màn hình / dừng Shizuku: phiên dừng hoặc báo lỗi, không tạo kết quả hợp lệ giả.
- Khởi động lại công cụ: lịch sử còn. Cập nhật app đích và chạy lại: lịch sử giữ version code cũ / mới riêng biệt.
- So sánh cùng package: chênh lệch là đối chứng trừ mốc; phiên không có mẫu không xuất phần trăm. Khác thiết bị / build / quy trình có cảnh báo.
- Xuất CSV, mở lại kiểm tra log nhiều dòng và ký tự tiếng Việt.

Unit tests kiểm tra parser số đo, lỗi trạng thái, thiếu / âm TotalTime, 0 ms thực, line endings, median, điều kiện bật nút, cờ Intent cho Warm và xử lý log lỗi dài / tiếp tục kiểu đo sau lỗi lệnh. Kiểm thử nhận xét so sánh bao gồm đảo A/B, thiếu mẫu, dao động, khác điều kiện, mốc 0 và kết quả trái chiều giữa các kiểu đo. Robolectric kiểm tra chuyển tab, loading trong khi tác vụ nền chưa hoàn tất và màu / nhận xét sau khi đổi A/B. Build và các kiểm thử này không xác minh quyền Shizuku, hành vi ROM hoặc độ chính xác trên phần cứng thật.

Kiểm thử bằng chứng kiểm tra loại dữ liệu Splash-only/legacy, timeout, trạng thái sai, sai package, thiếu xác nhận, mốc chưa ổn định, clock cũ, phân nhóm ads/thao tác và tách thời gian xác nhận tay khỏi thời gian quan sát. Cần chạy checklist trên thiết bị thật để xác minh Trợ năng, overlay và các app có quảng cáo.

Kiểm thử 0.3.0 bổ sung luồng Cold sau ads tự tiếp tục, chuẩn bị Warm/Hot không hỏi từng mẫu, timeout vẫn có hướng dẫn, phát hiện nhãn/ID/custom blocker, hủy mốc khi bị che và chính sách bằng chứng tự động không giả xác nhận tester.
