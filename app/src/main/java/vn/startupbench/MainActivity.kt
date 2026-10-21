package vn.startupbench

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 20, 32)
    private val surface = Color.rgb(25, 36, 52)
    private val accent = Color.rgb(91, 224, 194)
    private val muted = Color.rgb(167, 184, 205)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var packageInput: EditText? = null
    private var noteInput: EditText? = null
    private var progressText: TextView? = null
    private var statusText: TextView? = null
    private var startButton: Button? = null
    private var readinessText: TextView? = null
    private var selectionPreview: LinearLayout? = null
    private var selectedPackage = ""
    private var note = ""
    private var repeats = 5
    private var tab = 0
    private val modes = linkedSetOf("COLD", "WARM", "HOT")
    private val compared = linkedSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var wasRunning = false
    private var pendingCsv = ""
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        runOnUiThread {
            if (result == PackageManager.PERMISSION_GRANTED) BenchEngine.connect(this, retry = true)
            else message("Chưa được cấp quyền. Mở Shizuku → Authorized applications để cấp lại.")
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            refreshReadiness()
            progressText?.text = BenchEngine.progress.takeIf { it.isNotBlank() }?.let { MeasurementFailure.short(it) }
                ?: "Sẵn sàng. Màn hình cần mở khóa trong suốt phiên đo."
            if (wasRunning && !BenchEngine.running) render()
            wasRunning = BenchEngine.running
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedPackage = state?.getString("package") ?: ""
        note = state?.getString("note") ?: ""
        repeats = state?.getInt("repeats", 5) ?: 5
        state?.getStringArrayList("modes")?.let { modes.clear(); modes.addAll(it) }
        tab = state?.getInt("tab", 0) ?: 0
        pendingCsv = state?.getString("csv") ?: ""
        Shizuku.addRequestPermissionResultListener(permissionListener)
        window.setDecorFitsSystemWindows(false)
        render()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }
    override fun onDestroy() { Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("package", selectedPackage); out.putString("note", note); out.putInt("repeats", repeats)
        out.putStringArrayList("modes", ArrayList(modes)); out.putInt("tab", tab); out.putString("csv", pendingCsv)
        super.onSaveInstanceState(out)
    }
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(6), 0, dp(6))
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
        text = label; isAllCaps = false
        setTextColor(android.content.res.ColorStateList(states, intArrayOf(muted, bg)))
        backgroundTintList = android.content.res.ColorStateList(states, intArrayOf(Color.rgb(57, 68, 84), accent))
        setOnClickListener { action() }
    }
    private fun refreshReadiness(): StartReadiness {
        val binder = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = binder && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (!permission || BenchEngine.remote?.asBinder()?.isBinderAlive == false) BenchEngine.remote = null
        if (permission && BenchEngine.remote == null && !BenchEngine.running) BenchEngine.connect(this)
        val state = StartReadiness.evaluate(binder, permission, BenchEngine.remote != null, BenchEngine.running,
            selectedPackage.trim(), modes.isNotEmpty(), BenchEngine.connectionError)
        statusText?.text = when {
            !binder -> "○ Cần chạy Shizuku"
            !permission -> "○ Chưa cấp quyền Shizuku"
            BenchEngine.connectionError.isNotBlank() -> "○ Lỗi kết nối dịch vụ đo"
            BenchEngine.remote != null -> "● Shizuku và dịch vụ đo đã kết nối"
            else -> "○ Đang kết nối dịch vụ đo…"
        }
        startButton?.isEnabled = state.enabled
        startButton?.text = if (BenchEngine.running) "Đang đo…" else "Bắt đầu đo & lưu phiên"
        readinessText?.text = state.message
        return state
    }
    private fun card(parent: LinearLayout = content, block: LinearLayout.() -> Unit): LinearLayout = column().apply {
        background = shape(surface); setPadding(dp(18), dp(14), dp(18), dp(14))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }); block()
    }
    private fun input(hintText: String, value: String, changed: (String) -> Unit) = EditText(this).apply {
        hint = hintText; setText(value); setTextColor(Color.WHITE); setHintTextColor(muted); isSingleLine = true
        textSize = 16f
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
    private fun render() {
        statusText = null; progressText = null; startButton = null; readinessText = null
        root = column().apply { setBackgroundColor(bg); setPadding(dp(20), dp(14), dp(20), 0) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(dp(20) + bars.left, dp(14) + bars.top, dp(20) + bars.right, bars.bottom); insets
        }
        setContentView(root)
        window.insetsController?.setSystemBarsAppearance(0,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root.addView(text("STARTUP BENCH · ${BuildConfig.VERSION_NAME}", 12f, accent).apply { letterSpacing = .12f })
        root.addView(text(if (tab == 0) "Đo khởi động ứng dụng" else "Lịch sử & đối chứng", 26f).apply { setTypeface(null, Typeface.BOLD) })
        val nav = LinearLayout(this)
        nav.addView(button("Đo startup") { tab = 0; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(button("Lịch sử") { tab = 1; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(nav)
        content = column().apply { setPadding(0, dp(14), 0, dp(24)) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        if (tab == 0) renderMeasure() else renderHistory()
    }
    private fun renderMeasure() {
        card {
            statusText = text("Đang kiểm tra Shizuku…", 16f, accent); addView(statusText)
            addView(text("Android 11+ • không cần root • cần cấp quyền debugging", 13f, muted))
            addView(button("Kết nối / hướng dẫn") { connect() })
        }
        card {
            addView(text("01   Chọn ứng dụng", 18f))
            packageInput = input("com.example.app", selectedPackage) { selectedPackage = it; updateSelection(); refreshReadiness() }; addView(packageInput)
            selectionPreview = LinearLayout(this@MainActivity); addView(selectionPreview); updateSelection()
            addView(button("Chọn app trên điện thoại") { chooseApp() })
            addView(text("Chỉ đo app có màn hình launcher, cùng hồ sơ người dùng.", 12f, muted))
        }
        card {
            addView(text("02   Thiết lập phiên đo", 18f))
            listOf("COLD" to "Cold · dừng tiến trình trước khi mở", "WARM" to "Warm · tạo lại task / Activity", "HOT" to "Hot · đưa Activity trở lại foreground").forEach { (mode, label) ->
                addView(CheckBox(this@MainActivity).apply {
                    text = label; setTextColor(Color.WHITE); isChecked = mode in modes
                    setOnCheckedChangeListener { _, checked -> if (checked) modes.add(mode) else modes.remove(mode); refreshReadiness() }
                })
            }
            addView(text("Số lần / kiểu đo", 13f, muted))
            addView(Spinner(this@MainActivity).apply {
                val options = listOf(1, 3, 5, 10)
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
                setSelection(options.indexOf(repeats).coerceAtLeast(0)); backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                        repeats = options[position]; (v as? TextView)?.setTextColor(accent)
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) = Unit
                }
            })
            noteInput = input("Nhãn / ghi chú, ví dụ: staging build 102", note) { note = it }; addView(noteInput)
            addView(text("Cold sẽ force-stop app; Warm sẽ xóa task đang mở. Hãy lưu công việc trong app đích trước khi đo.", 13f, muted))
            readinessText = text("Đang kiểm tra điều kiện đo…", 14f, accent); addView(readinessText)
            startButton = button("Bắt đầu đo & lưu phiên") { startRun() }.apply { isEnabled = false }; addView(startButton)
            progressText = text(BenchEngine.progress.takeIf { it.isNotBlank() }?.let { MeasurementFailure.short(it) } ?: "", 14f, accent).apply {
                maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
            }; addView(progressText)
            if (BenchEngine.running) addView(button("Dừng sau mẫu hiện tại") { BenchEngine.cancel = true; message("Sẽ dừng sau khi lệnh đang chạy kết thúc.") })
        }
        card {
            addView(text("Đọc số đo đúng cách", 17f))
            addView(text("Đo TotalTime từ Android Activity Manager, không phải thời gian tải xong dữ liệu. Chỉ mẫu có LaunchState đúng yêu cầu mới được tính. Hot có thể không có TotalTime trên một số máy; khi đó báo không hợp lệ, không tự gán 0 ms.", 13f, muted))
        }
        BenchEngine.session?.let { item -> if (!BenchEngine.running) renderSummary(item) }
        refreshReadiness()
    }
    private fun connect() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            AlertDialog.Builder(this).setTitle("Thiết lập Shizuku")
                .setMessage("1. Cài Shizuku từ shizuku.rikka.app.\n2. Bật Developer options → Wireless debugging.\n3. Trong Shizuku, ghép nối và Start.\n4. Quay lại đây, bấm Kết nối và cấp quyền.\n\nSau khi khởi động lại điện thoại, có thể cần Start Shizuku lại.")
                .setPositiveButton("Đã hiểu", null).show(); return
        }
        runCatching {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) BenchEngine.connect(this, retry = true)
            else Shizuku.requestPermission(100)
        }.onFailure { message("Không cấp được quyền: ${it.message}") }
    }
    private fun updateSelection() {
        val preview = selectionPreview ?: return
        preview.removeAllViews()
        runCatching {
            val pkg = selectedPackage.trim()
            val info = packageManager.getPackageInfo(pkg, 0)
            val app = packageManager.getApplicationInfo(pkg, 0)
            preview.addView(ImageView(this).apply {
                setImageDrawable(app.loadIcon(packageManager)); contentDescription = "Biểu tượng ứng dụng"
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })
            preview.addView(text("${app.loadLabel(packageManager)}\nv${info.versionName} (${info.longVersionCode})", 14f, accent))
        }
    }
    private fun chooseApp() {
        val search = input("Tìm tên app hoặc package", "") { }
        val list = ListView(this)
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName !in listOf(packageName, "moe.shizuku.privileged.api") }
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        var visible = apps
        fun update(query: String) {
            visible = apps.filter { it.loadLabel(packageManager).toString().contains(query, true) || it.activityInfo.packageName.contains(query, true) }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
                visible.map { "${it.loadLabel(packageManager)}\n${it.activityInfo.packageName}" })
        }
        update("")
        search.setTextColor(Color.BLACK); search.setHintTextColor(Color.GRAY)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val body = column().apply { addView(search); addView(list, LinearLayout.LayoutParams(-1, dp(380))) }
        val dialog = AlertDialog.Builder(this).setTitle("Chọn ứng dụng (${apps.size})").setView(body).setNegativeButton("Đóng", null).create()
        list.setOnItemClickListener { _, _, position, _ ->
            selectedPackage = visible[position].activityInfo.packageName; packageInput?.setText(selectedPackage); dialog.dismiss()
        }
        dialog.show()
    }
    private fun startRun() {
        val state = refreshReadiness()
        if (!state.enabled) { startError(state.message); return }
        progressText?.text = "Đã nhận thao tác bấm. Đang kiểm tra ứng dụng…"
        if (modes.isEmpty()) { message("Chọn ít nhất một kiểu đo."); return }
        val pkg = selectedPackage.trim()
        if (pkg == packageName || pkg == "moe.shizuku.privileged.api") { message("Không đo chính công cụ hoặc Shizuku."); return }
        try {
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: error("Không tìm thấy app có launcher trong hồ sơ hiện tại.")
            val component = launch.component ?: error("Không xác định được Activity.")
            val info = packageManager.getPackageInfo(pkg, 0)
            BenchEngine.start(this, component.flattenToString(), pkg,
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString(),
                info.versionName ?: "?", info.longVersionCode, modes.toList(), repeats, note)
            wasRunning = true; render()
        } catch (e: Exception) { startError(e.message ?: "Không thể bắt đầu đo") }
    }
    private fun startError(reason: String) {
        val value = "Không thể bắt đầu đo: $reason"
        progressText?.text = value
        BenchEngine.progress = value
        AlertDialog.Builder(this).setTitle("Chưa bắt đầu phiên đo").setMessage(reason).setPositiveButton("Đã hiểu", null).show()
    }
    private fun renderSummary(item: JSONObject) {
        card {
            addView(text("${item.optString("label")} · ${item.optString("version")} (${item.optLong("versionCode")})", 18f))
            val status = when (item.optString("status")) {
                "completed" -> "Hoàn tất"
                "completed_with_errors" -> "Đã kết thúc, có mẫu lỗi"
                "stopped" -> "Đã dừng trước khi hoàn tất"
                "error" -> "Phiên đo gặp lỗi"
                else -> "Đang chạy hoặc chưa hoàn tất"
            }
            addView(text("${item.optString("device")} · Android ${item.optString("android")}\n${date(item)} · $status\n${item.optString("note")}", 12f, muted))
            addView(text("Pin ${item.optInt("batteryLevel", -1)}% · ${item.optInt("batteryTempTenthsC", -1) / 10.0}°C · tiết kiệm pin: ${item.optBoolean("powerSave")}", 12f, muted))
            listOf("COLD", "WARM", "HOT").forEach { mode ->
                val values = item.values(mode)
                val results = item.results().filter { it.mode == mode }
                val total = results.size
                addView(text("$mode   ${LaunchResult.median(values)?.let { ms(it) } ?: "—"}", 23f, accent))
                val requested = item.optJSONArray("modes")?.let { a -> (0 until a.length()).any { a.optString(it) == mode } } ?: true
                val count = when {
                    !requested -> "Không chọn kiểu đo này"
                    total == 0 -> "Chưa chạy"
                    else -> "${values.size}/$total mẫu đã chạy hợp lệ · dự kiến ${item.optInt("repeats", total)} lần"
                }
                addView(text(count, 12f, muted))
                if (values.isNotEmpty()) {
                    addView(text("Min ${values.min()} · max ${values.max()} ms", 12f, muted))
                    addView(SampleChart(this@MainActivity, values, accent), LinearLayout.LayoutParams(-1, dp(90)))
                    addView(text("Thứ tự các mẫu hợp lệ · thang 0–${values.max()} ms", 12f, muted))
                }
                results.firstOrNull { !it.valid }?.let { result ->
                    addView(text(MeasurementFailure.short(result.error ?: result.raw), 13f, Color.rgb(255, 185, 160)).apply {
                        maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
            }
            addView(button("Chi tiết từng lần / log Android") { details(item) })
            addView(button("Xuất CSV") { export(item) })
        }
    }
    private fun renderHistory() {
        val sessions = History(this).use { it.all() }
        addHistoryHeader(sessions)
        if (sessions.isEmpty()) content.addView(text("Chưa có phiên đo. Chọn ứng dụng và chạy phiên đầu tiên.", 16f, muted))
        sessions.forEach { item ->
            card {
                addView(CheckBox(this@MainActivity).apply {
                    text = getString(R.string.compare_session, item.optString("label"), item.optString("version")); setTextColor(accent)
                    val id = item.getString("id"); isChecked = id in compared
                    setOnCheckedChangeListener { box, checked ->
                        if (checked && compared.size >= 2) { box.isChecked = false; message("Chọn tối đa hai phiên.") }
                        else if (checked) compared.add(id) else compared.remove(id)
                    }
                })
                addView(text("${date(item)}\n${item.optString("package")}\n${item.optString("note")}", 13f, muted))
                addView(text(listOf("COLD", "WARM", "HOT").joinToString("  •  ") { "$it ${LaunchResult.median(item.values(it))?.let { v -> ms(v) } ?: "—"}" }, 14f))
                addView(button("Xem phiên đo") {
                    content.removeAllViews(); content.addView(button("← Lịch sử") { render() }); renderSummary(item)
                })
            }
        }
    }
    private fun addHistoryHeader(sessions: List<JSONObject>) {
        content.addView(text("${sessions.size} phiên đã lưu trên thiết bị · chọn 2 phiên để đối chứng", 13f, muted))
        content.addView(button("So sánh hai phiên đã chọn") {
            val pair = compared.mapNotNull { id -> sessions.find { it.getString("id") == id } }
            if (pair.size != 2) message("Chọn hai phiên trong lịch sử.")
            else compare(pair[0], pair[1])
        })
    }
    private fun compare(base: JSONObject, candidate: JSONObject) {
        if (base.getString("package") != candidate.getString("package")) { message("Hãy chọn hai phiên của cùng package."); return }
        val warning = listOf("fingerprint", "protocol", "component", "powerSave", "plugged").filter { base.optString(it) != candidate.optString(it) }
        val result = buildString {
            append("Mốc: ${base.optString("version")} (${base.optLong("versionCode")}) · ${date(base)}\n")
            append("Đối chứng: ${candidate.optString("version")} (${candidate.optLong("versionCode")}) · ${date(candidate)}\n\n")
            if (warning.isNotEmpty()) append("⚠ Khác thiết bị / hệ điều hành / quy trình / Activity; hạn chế khả năng so sánh.\n\n")
            if (base.optString("status") != "completed" || candidate.optString("status") != "completed") append("⚠ Có phiên chưa hoàn tất hoặc có mẫu lỗi.\n\n")
            listOf("COLD", "WARM", "HOT").forEach { mode ->
                val a = LaunchResult.median(base.values(mode)); val b = LaunchResult.median(candidate.values(mode))
                append("$mode: ")
                if (a == null || b == null) append("thiếu mẫu hợp lệ\n")
                else {
                    val delta = b - a
                    append("${ms(a)} → ${ms(b)}\n${if (delta > 0) "+" else ""}${ms(delta)}")
                    if (a > 0) append(String.format(Locale.US, " (%+.1f%%)", delta / a * 100))
                    append(if (delta > 0) " · chậm hơn\n" else if (delta < 0) " · nhanh hơn\n" else " · bằng nhau\n")
                    append("Mẫu hợp lệ: ${base.values(mode).size} → ${candidate.values(mode).size}\n\n")
                }
            }
            append("Dùng cùng thiết bị, pin/nhiệt độ và điều kiện mạng. Chênh lệch nhỏ chưa đủ kết luận hồi quy.")
        }
        AlertDialog.Builder(this).setTitle("So sánh median").setMessage(result).setPositiveButton("Đóng", null).show()
    }
    private fun details(item: JSONObject) {
        val body = text(item.optString("errorDetail").let { if (it.isBlank()) "" else "$it\n\n" } + item.results().mapIndexed { index, r ->
            "#${index + 1} ${r.mode} · ${if (r.valid) "${r.totalMs} ms" else "KHÔNG HỢP LỆ: ${r.error}"}\n${r.raw}"
        }.joinToString("\n\n"), 13f, Color.BLACK).apply { setTextIsSelectable(true); setPadding(dp(18), 0, dp(18), 0) }
        AlertDialog.Builder(this).setTitle("Mẫu đo · log gốc").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Đóng", null).show()
    }
    private fun export(item: JSONObject) {
        fun csv(value: Any?) = "\"${value.toString().replace("\"", "\"\"")}\""
        val header = listOf("session", "package", "version", "versionCode", "device", "android", "fingerprint", "protocol", "sessionStatus", "note", "sample", "requested", "actual", "valid", "totalMs", "waitMs", "error", "raw")
        pendingCsv = header.joinToString(",") + "\r\n" + item.results().mapIndexed { index, r ->
            listOf(item.optString("id"), item.optString("package"), item.optString("version"), item.optLong("versionCode"), item.optString("device"), item.optString("android"), item.optString("fingerprint"), item.optString("protocol"), item.optString("status"), item.optString("note"), index + 1, r.mode, r.state ?: "", r.valid, r.totalMs ?: "", r.waitMs ?: "", r.error ?: "", r.raw).joinToString(",") { csv(it) }
        }.joinToString("\r\n")
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "startup-${item.getString("id")}.csv")
        }, 8)
    }
    @Deprecated("Android Activity callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 8 && resultCode == RESULT_OK) data?.data?.let { uri ->
            runCatching { checkNotNull(contentResolver.openOutputStream(uri)).bufferedWriter(Charsets.UTF_8).use { it.write(pendingCsv) } }
                .onSuccess { message("Đã xuất CSV."); pendingCsv = "" }.onFailure { message("Không lưu được CSV: ${it.message}") }
        }
    }
    private fun message(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun date(item: JSONObject) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.getString("id").toLong()))
    private fun ms(value: Double) = String.format(Locale.US, "%.1f ms", value)
}
