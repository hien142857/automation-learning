package vn.startupbench

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.app.KeyguardManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/** Application-scoped state survives Activity recreation and foreground app changes. */
object BenchEngine {
    @Volatile var remote: IMeasureService? = null
    @Volatile var running = false
    @Volatile var cancel = false
    @Volatile var progress = ""
    @Volatile var session: JSONObject? = null
    @Volatile var connectionError = ""
    @Volatile var pendingGuidance: String? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var binding = false
    private var boundAt = 0L
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remote = IMeasureService.Stub.asInterface(binder); binding = false; connectionError = ""
        }
        override fun onServiceDisconnected(name: ComponentName) {
            remote = null; binding = false; connectionError = "Mất kết nối dịch vụ đo."
        }
    }
    private fun args(context: Context) = Shizuku.UserServiceArgs(ComponentName(context, MeasureService::class.java))
        .daemon(false).processNameSuffix("measure").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)

    fun connect(context: Context, retry: Boolean = false) {
        if (remote?.asBinder()?.isBinderAlive == true) return
        if (retry && !running) {
            try {
                // Remove a stale startup record before requesting a fresh UserService.
                Shizuku.unbindUserService(args(context.applicationContext), connection, true)
                remote = null; binding = false; connectionError = ""
            } catch (e: Exception) {
                connectionError = "Không khởi tạo lại được dịch vụ đo: ${e.message}"; return
            }
        }
        if (binding && System.currentTimeMillis() - boundAt >= 15000) {
            binding = false; connectionError = "Kết nối dịch vụ đo quá hạn 15 giây. Kiểm tra Shizuku đang chạy."
        }
        if (binding) return
        if (retry) connectionError = ""
        if (connectionError.isNotEmpty()) return
        binding = true; boundAt = System.currentTimeMillis()
        try { Shizuku.bindUserService(args(context.applicationContext), connection) }
        catch (e: Exception) { binding = false; connectionError = "Không kết nối được dịch vụ đo: ${e.message}" }
    }

    @Synchronized fun start(context: Context, component: String, pkg: String, label: String, version: String,
                            versionCode: Long, modes: List<String>, repeats: Int, note: String) {
        check(!running) { "Đã có phiên đo đang chạy." }
        check(remote?.asBinder()?.isBinderAlive == true) { "Dịch vụ đo chưa kết nối. Hãy kết nối lại Shizuku." }
        require(modes.isNotEmpty() && modes.all { it in listOf("COLD", "WARM", "HOT") }) { "Chọn kiểu đo hợp lệ." }
        require(repeats in listOf(1, 3, 5, 10)) { "Số lần đo không hợp lệ." }
        val observer = checkNotNull(ReadinessService.instance) { "Cần bật trợ năng quan sát mốc UI trước khi đo." }
        val criterion = ReadyCriterion.load(context, pkg)
        require(criterion.configured) { "Cần cấu hình chữ hoặc resource ID của nội dung đích, không dùng mốc Splash." }
        require(criterion.resourceId.isBlank() || criterion.resourceId.startsWith("$pkg:id/")) { "Resource ID cần đầy đủ, bắt đầu bằng $pkg:id/." }
        val app = context.applicationContext
        val runIntent = Intent(app, RunService::class.java)
        val item = JSONObject().apply {
            put("id", System.currentTimeMillis().toString()); put("package", pkg); put("label", label)
            put("version", version); put("versionCode", versionCode); put("note", note)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}"); put("android", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT); put("fingerprint", Build.FINGERPRINT)
            put("component", component); put("repeats", repeats); put("modes", JSONArray(modes))
            put("protocol", "observed-ui-v2; clock=uptime-before-am; stable=${if (criterion.autoContinue) 1500 else 500}ms; poll=200ms; warm=flags-0x10008000")
            put("readinessCriterion", criterion.json()); put("metric", "observed-ui-marker; separate-confirmation-method-and-cohort")
            put("status", "running"); put("samples", JSONArray())
            val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            put("batteryLevel", battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1)
            put("batteryTempTenthsC", battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1)
            put("plugged", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
            put("powerSave", app.getSystemService(PowerManager::class.java).isPowerSaveMode)
        }
        app.startForegroundService(runIntent)
        running = true; cancel = false
        pendingGuidance = null; progress = "Đã nhận yêu cầu. Đang chuẩn bị phiên đo…"
        session = item
        executor.execute {
            val history = History(app)
            var stopReason = ""
            var hasErrors = false
            try {
                history.save(item)
                for (mode in modes) {
                    var index = 1
                    while (index <= repeats) {
                        if (cancel) break
                        if (!app.getSystemService(PowerManager::class.java).isInteractive ||
                            app.getSystemService(KeyguardManager::class.java).isKeyguardLocked || ReadinessService.instance !== observer) {
                            cancel = true; stopReason = "Mở khóa màn hình, bật lại quan sát Trợ năng rồi chạy phiên mới. Kiểm tra Shizuku nếu dịch vụ đo mất kết nối."
                            pendingGuidance = stopReason; break
                        }
                        progress = "$mode · lần $index/$repeats · làm theo hướng dẫn trên app đích"
                        RunService.update(app, progress)
                        val result = try { GuidedSample.run(checkNotNull(remote), observer, criterion, component, mode, Process.myUid() / 100000) }
                        catch (e: Exception) {
                            pendingGuidance = "Không thể tiếp tục đo: ${MeasurementFailure.short(e.message.orEmpty())}. Kiểm tra Shizuku và quan sát Trợ năng, mở app đích xử lý màn hình chặn rồi thử phiên mới."
                            if (ReadinessService.instance === observer && observer.ask("Cần khôi phục điều kiện đo", pendingGuidance!!, listOf("Dừng để kiểm tra")) >= 0) pendingGuidance = null
                            GuidedSample("BenchError: ${MeasurementFailure.short(e.message.orEmpty())}\n${e.stackTraceToString().take(8000)}",
                                JSONObject().put("status", "service_error"), "stop")
                        }
                        synchronized(item) {
                            item.getJSONArray("samples").put(JSONObject().put("mode", mode).put("raw", result.raw)
                                .put("trial", index).put("evidence", result.evidence))
                            history.save(item)
                        }
                        if ((!EvidencePolicy.launchAccepted(result.evidence, result.raw) && !EvidencePolicy.launchAssisted(result.evidence, result.raw)) ||
                            !LaunchResult.parse(mode, result.raw).stateVerified) hasErrors = true
                        when (result.decision) {
                            "stop" -> {
                                cancel = true; stopReason = "Dừng theo hướng dẫn / mất điều kiện đo. Xem bằng chứng từng mẫu."
                                if (result.evidence.optString("observerError").isNotBlank()) pendingGuidance =
                                    "Không hiển thị/quan sát được hướng dẫn UI: ${result.evidence.optString("observerError")}. Bật lại Trợ năng cho Startup Bench, mở khóa màn hình rồi thử phiên mới."
                                break
                            }
                            "skip" -> break
                            "retry" -> continue
                            else -> index++
                        }
                    }
                    if (cancel) break
                }
                item.put("status", if (cancel) "stopped" else if (hasErrors) "completed_with_errors" else "completed")
                item.put("stopReason", stopReason)
                progress = if (cancel) "Đã dừng. ${stopReason.ifEmpty { "Theo yêu cầu người dùng." }} Các mẫu đã đo được giữ lại."
                    else if (hasErrors) "Đã kết thúc phiên đo, có mẫu lỗi. Xem Chi tiết từng lần / log Android."
                    else "Đã đo xong và lưu lịch sử."
            } catch (e: Exception) {
                item.put("status", "error"); item.put("errorDetail", e.stackTraceToString().take(8000))
                progress = "Phiên đo lỗi: ${MeasurementFailure.short(e.message ?: e.javaClass.simpleName)}"
                pendingGuidance = "$progress. Kiểm tra dịch vụ đo, Trợ năng và dung lượng lưu trữ rồi thử phiên mới."
            } finally {
                runCatching { history.save(item) }.onFailure { progress = "Không lưu được phiên đo. Kiểm tra dung lượng trống của thiết bị." }
                runCatching { history.close() }
                observer.hide()
                running = false
                RunService.update(app, progress, done = true)
                runCatching { app.stopService(runIntent) }
            }
        }
    }
}
