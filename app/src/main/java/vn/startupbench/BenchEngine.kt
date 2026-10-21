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
        val app = context.applicationContext
        val runIntent = Intent(app, RunService::class.java)
        val item = JSONObject().apply {
            put("id", System.currentTimeMillis().toString()); put("package", pkg); put("label", label)
            put("version", version); put("versionCode", versionCode); put("note", note)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}"); put("android", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT); put("fingerprint", Build.FINGERPRINT)
            put("component", component); put("repeats", repeats); put("modes", JSONArray(modes))
            put("protocol", "am-start-W-v1; settle=1500ms; inter-sample=1500ms; warm=clear-task")
            put("status", "running"); put("samples", JSONArray())
            val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            put("batteryLevel", battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1)
            put("batteryTempTenthsC", battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1)
            put("plugged", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
            put("powerSave", app.getSystemService(PowerManager::class.java).isPowerSaveMode)
        }
        app.startForegroundService(runIntent)
        running = true; cancel = false
        progress = "Đã nhận yêu cầu. Đang chuẩn bị phiên đo…"
        session = item
        executor.execute {
            val history = History(app)
            var stopReason = ""
            try {
                history.save(item)
                for (mode in modes) {
                    for (index in 1..repeats) {
                        if (cancel) break
                        if (!app.getSystemService(PowerManager::class.java).isInteractive ||
                            app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                            cancel = true; stopReason = "Màn hình tắt hoặc bị khóa."; break
                        }
                        progress = "$mode · lần $index/$repeats"
                        RunService.update(app, progress)
                        var raw = try { remote?.measure(component, mode, Process.myUid() / 100000)
                            ?: "BenchError: Mất kết nối Shizuku" }
                        catch (e: Exception) { remote = null; "BenchError: ${e.message}" }
                        if (!app.getSystemService(PowerManager::class.java).isInteractive ||
                            app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                            raw = "BenchError: Màn hình tắt hoặc bị khóa trong khi đo\n$raw"
                        }
                        synchronized(item) {
                            item.getJSONArray("samples").put(JSONObject().put("mode", mode).put("raw", raw))
                            history.save(item)
                        }
                        if (raw.startsWith("BenchError:")) { cancel = true; stopReason = raw.lineSequence().first().removePrefix("BenchError: "); break }
                        if (!cancel) Thread.sleep(1500)
                    }
                    if (cancel) break
                }
                item.put("status", if (cancel) "stopped" else "completed")
                item.put("stopReason", stopReason)
                progress = if (cancel) "Đã dừng. ${stopReason.ifEmpty { "Theo yêu cầu người dùng." }} Các mẫu đã đo được giữ lại." else "Đã đo xong và lưu lịch sử."
            } catch (e: Exception) {
                item.put("status", "error"); progress = "Phiên đo lỗi: ${e.message}"
            } finally {
                try { history.save(item) } finally {
                    history.close(); running = false
                    RunService.update(app, progress, done = true)
                    app.stopService(runIntent)
                }
            }
        }
    }
}
