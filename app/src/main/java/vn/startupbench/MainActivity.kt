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
    private var appPicker: AppPickerDialog? = null
    private var criterionText: EditText? = null
    private var criterionId: EditText? = null
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        runOnUiThread {
            if (result == PackageManager.PERMISSION_GRANTED) BenchEngine.connect(this, retry = true)
            else message("Chưa được cấp quyền. Mở Shizuku → Authorized applications để cấp lại.")
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            refreshReadiness()
            BenchEngine.pendingGuidance?.let { guidance ->
                BenchEngine.pendingGuidance = null
                AlertDialog.Builder(this@MainActivity).setTitle("Cần xử lý để tiếp tục đo").setMessage(guidance).setPositiveButton("Đã hiểu", null).show()
            }
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
    override fun onDestroy() { appPicker?.dismiss(); Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy() }
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
        val baseState = StartReadiness.evaluate(binder, permission, BenchEngine.remote != null, BenchEngine.running,
            selectedPackage.trim(), modes.isNotEmpty(), BenchEngine.connectionError)
        val state = when {
            baseState.enabled && ReadinessService.instance == null -> StartReadiness(false, "Cần bật trợ năng quan sát mốc UI ở phần 02.")
            baseState.enabled && !ReadyCriterion.load(this, selectedPackage.trim()).configured -> StartReadiness(false, "Cần nhập mốc nội dung đích ở phần 02; không dùng mốc Splash.")
            else -> baseState
        }
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
        content = column().apply { setPadding(0, dp(14), 0, dp(24)) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(navigationBar())
        if (tab == 0) renderMeasure() else renderHistory()
    }
    private fun navigationBar(): View {
        val nav = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL; contentDescription = "Điều hướng chính"
            setPadding(dp(4), dp(6), dp(4), dp(6)); background = shape(surface)
        }
        val ids = listOf(View.generateViewId(), View.generateViewId())
        listOf("Đo startup" to android.R.drawable.ic_media_play, "Lịch sử" to android.R.drawable.ic_menu_recent_history).forEachIndexed { index, (label, icon) ->
            nav.addView(RadioButton(this).apply {
                id = ids[index]; buttonDrawable = null
                text = label; textSize = 13f; gravity = android.view.Gravity.CENTER; minHeight = dp(68)
                setPadding(dp(8), dp(6), dp(8), dp(6)); setTextColor(if (tab == index) accent else muted)
                setTypeface(null, if (tab == index) Typeface.BOLD else Typeface.NORMAL)
                background = shape(if (tab == index) Color.rgb(33, 65, 69) else Color.TRANSPARENT)
                val drawable = getDrawable(icon)!!.mutate().apply { setTint(if (tab == index) accent else muted) }
                drawable.setBounds(0, 0, dp(24), dp(24))
                setCompoundDrawables(null, drawable, null, null); compoundDrawablePadding = dp(4)
                stateDescription = if (tab == index) "Đang chọn" else "Chưa chọn"
                if (tab == index) setOnClickListener { render() }
            }, RadioGroup.LayoutParams(0, -2, 1f))
        }
        nav.check(ids[tab])
        nav.setOnCheckedChangeListener { _, id ->
            val next = ids.indexOf(id)
            if (next >= 0 && tab != next) { tab = next; render() }
        }
        return nav
    }
    private fun renderMeasure() {
        card {
            statusText = text("Đang kiểm tra Shizuku…", 16f, accent); addView(statusText)
            addView(text("Android 11+ • không cần root • cần cấp quyền debugging", 13f, muted))
            addView(button("Kết nối / hướng dẫn") { connect() })
        }
        card {
            addView(text("01   Chọn ứng dụng", 18f))
            packageInput = input("com.example.app", selectedPackage) {
                selectedPackage = it; updateSelection()
                val saved = ReadyCriterion.load(this@MainActivity, it.trim())
                criterionText?.setText(saved.text); criterionId?.setText(saved.resourceId); refreshReadiness()
            }; addView(packageInput)
            selectionPreview = LinearLayout(this@MainActivity); addView(selectionPreview); updateSelection()
            addView(button("Chọn app trên điện thoại") { chooseApp() })
            addView(text("Chỉ đo app có màn hình launcher, cùng hồ sơ người dùng.", 12f, muted))
        }
        card {
            addView(text("02   Xác định nội dung đích", 18f))
            addView(text("Chọn chữ xuất hiện ở màn hình dùng được hoặc resource ID đầy đủ, không phải Splash. Nếu nhập cả hai thì phải cùng khớp. App sẽ kiểm tra mốc và hỏi bạn xác nhận sau mỗi mẫu.", 13f, muted))
            val saved = ReadyCriterion.load(this@MainActivity, selectedPackage.trim())
            criterionText = input("Chữ chính xác, ví dụ: Tạo video", saved.text) { value ->
                ReadyCriterion.save(this@MainActivity, ReadyCriterion(selectedPackage.trim(), criterionId?.text?.toString()?.trim() ?: saved.resourceId, value.trim()))
                refreshReadiness()
            }; addView(criterionText)
            criterionId = input("Resource ID, ví dụ com.example:id/home", saved.resourceId) { value ->
                ReadyCriterion.save(this@MainActivity, ReadyCriterion(selectedPackage.trim(), value.trim(), criterionText?.text?.toString()?.trim().orEmpty()))
                refreshReadiness()
            }; addView(criterionId)
            addView(button("Bật quan sát mốc UI / hướng dẫn") {
                AlertDialog.Builder(this@MainActivity).setTitle("Quan sát mốc UI")
                    .setMessage("Trong Trợ năng, bật Startup Bench · Quan sát mốc UI. Công cụ đọc mốc của app được chọn và ghi nhận lần bấm; không ghi nội dung nhập. Nếu Android chặn APK cài ngoài, mở Thông tin ứng dụng → menu → Cho phép cài đặt bị hạn chế, rồi bật lại.\n\nHãy mở app đích trước để xác định chữ ở màn hình nội dung. Nếu app không cung cấp mốc qua trợ năng (ví dụ Canvas/WebView bảo vệ), công cụ sẽ báo không xác minh được và không tạo kết quả giả.")
                    .setPositiveButton("Mở Trợ năng") { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .setNegativeButton("Đóng", null).show()
            })
        }
        card {
            addView(text("03   Thiết lập phiên đo có xác minh", 18f))
            addView(text("Mỗi mẫu cần bạn xác nhận mốc không bị Splash/ads che. Lần có ads/thao tác được lưu riêng. Quá 60 giây chưa thấy mốc sẽ có hướng dẫn thử lại/bỏ qua/dừng.", 13f, muted))
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
            addView(text("Android TotalTime là thời gian Activity Manager báo, có thể chỉ đến Splash. Chỉ mẫu có LaunchState đúng, thấy mốc UI ổn định và được tester xác nhận mới được xác minh. Thời gian đến mốc là quan sát trợ năng từ trước lệnh mở, không phải TTFD chuẩn và có độ trễ quan sát. So sánh mặc định chỉ dùng nhóm không có ads/thao tác.", 13f, muted))
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
        appPicker?.dismiss()
        appPicker = AppPickerDialog(this, onSelect = { pkg -> selectedPackage = pkg; packageInput?.setText(pkg) })
        appPicker?.show()
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
            addView(text("Trung bình = tổng thời gian / số mẫu được xác nhận trong CÙNG nhóm. Không gộp ads và không ads.", 13f, muted))
            listOf("COLD", "WARM", "HOT").forEach { mode ->
                addView(text(mode, 23f, accent).apply { setTypeface(null, Typeface.BOLD) })
                val attempted = item.androidResults().count { it.mode == mode }
                val groups = MeasurementCohort.entries.associateWith { item.cohortValues(mode, it) }
                val accepted = groups.values.sumOf { it.size }
                addView(text("Đã chạy $attempted · xác nhận $accepted · chưa đủ bằng chứng ${attempted - accepted} · dự kiến ${item.optInt("repeats", attempted)} lần", 12f, muted))
                groups.forEach { (cohort, samples) ->
                    val color = if (cohort == MeasurementCohort.AUTOMATIC) accent else Color.rgb(255, 205, 126)
                    addView(text(cohort.label, 16f, color).apply { setTypeface(null, Typeface.BOLD) })
                    if (samples.isEmpty()) {
                        addView(text("Chưa có mẫu trong nhóm này", 13f, muted))
                    } else {
                        val stats = SampleStatistics.of(samples)
                        addView(text("Trung bình ${ms(stats.mean!!)}", 25f, color))
                        addView(text("${stats.count} mẫu · trung vị ${ms(stats.median!!)}\nMin ${stats.min} · max ${stats.max} ms", 14f))
                        val android = SampleStatistics.of(item.cohortAndroidValues(mode, cohort))
                        addView(text("Android TotalTime: TB ${android.mean?.let { ms(it) } ?: "không có"} (${android.count} mẫu có số đo Android)", 12f, muted))
                        addView(SampleChart(this@MainActivity, samples, color), LinearLayout.LayoutParams(-1, dp(90)))
                        addView(text("Từng mẫu: ${samples.joinToString(" · ")} ms", 12f, muted))
                        if (cohort == MeasurementCohort.ASSISTED) addView(text("Bao gồm thời gian ads, chờ và thao tác/kiểm tra lại của tester. Đây là thời gian trải nghiệm đến mốc UI, không phải tốc độ khởi động thuần.", 13f, color))
                        if (stats.count < 5) addView(text("Có số liệu nhưng mới ${stats.count} mẫu; nên đo ít nhất 5–10 lần để đối chứng.", 12f, muted))
                    }
                }
                if (attempted == 0) addView(text("Chưa chạy kiểu đo này; không gán thời gian 0.", 12f, muted))
                if (attempted > accepted) addView(text("${attempted - accepted} mẫu chưa đủ bằng chứng không được tính trung bình. Xem Chi tiết để biết lý do.", 12f, muted))
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
                listOf("COLD", "WARM", "HOT").forEach { mode ->
                    val cohort = if (item.readyValues(mode).isNotEmpty()) MeasurementCohort.AUTOMATIC else MeasurementCohort.ASSISTED
                    val stats = SampleStatistics.of(item.cohortValues(mode, cohort))
                    addView(text("$mode · TB ${stats.mean?.let { ms(it) } ?: "chưa có số liệu"} · ${stats.count} mẫu · ${cohort.label}", 14f))
                }
                addView(button("Xem phiên đo") {
                    content.removeAllViews(); content.addView(button("← Lịch sử") { render() }); renderSummary(item)
                })
            }
        }
    }
    private fun addHistoryHeader(sessions: List<JSONObject>) {
        content.addView(text("${sessions.size} phiên đã lưu trên thiết bị · chọn 2 phiên để đối chứng", 13f, muted))
        content.addView(text("Chọn đầu tiên = A (mốc), chọn tiếp theo = B (đối chứng). Có thể đổi A/B ở màn so sánh.", 12f, muted))
        content.addView(button("So sánh hai phiên đã chọn") {
            val pair = compared.mapNotNull { id -> sessions.find { it.getString("id") == id } }
            if (pair.size != 2) message("Chọn hai phiên trong lịch sử.")
            else compare(pair[0], pair[1])
        })
    }
    private fun compare(base: JSONObject, candidate: JSONObject) {
        if (base.getString("package") != candidate.getString("package")) { message("Hãy chọn hai phiên của cùng package."); return }
        ComparisonScreen(this, content) { render() }.show(base, candidate)
    }
    private fun details(item: JSONObject) {
        val samples = item.optJSONArray("samples")
        val body = text(item.optString("errorDetail").let { if (it.isBlank()) "" else "$it\n\n" } + item.results().mapIndexed { index, r ->
            "#${index + 1} ${r.mode} · ${if (r.valid) "Đã xác minh UI; Android TotalTime ${r.totalMs?.let { "$it ms" } ?: "không có"}" else "CHƯA XÁC MINH TỰ ĐỘNG: ${r.error}"}\n${r.raw}\nBằng chứng UI / hành động:\n${samples?.getJSONObject(index)?.optJSONObject("evidence")?.toString(2) ?: "Không có: dữ liệu cũ không chứng minh đã qua Splash."}"
        }.joinToString("\n\n"), 13f, Color.BLACK).apply { setTextIsSelectable(true); setPadding(dp(18), 0, dp(18), 0) }
        AlertDialog.Builder(this).setTitle("Mẫu đo · log gốc").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Đóng", null).show()
    }
    private fun export(item: JSONObject) {
        fun csv(value: Any?) = "\"${value.toString().replace("\"", "\"\"")}\""
        val header = listOf("session", "package", "version", "versionCode", "device", "android", "fingerprint", "protocol", "sessionStatus", "note", "sample", "requested", "actual", "verifiedAutomaticUi", "androidTotalTimeMs", "androidWaitTimeMs", "error", "raw", "uiEvidenceJson")
        pendingCsv = header.joinToString(",") + "\r\n" + item.results().mapIndexed { index, r ->
            listOf(item.optString("id"), item.optString("package"), item.optString("version"), item.optLong("versionCode"), item.optString("device"), item.optString("android"), item.optString("fingerprint"), item.optString("protocol"), item.optString("status"), item.optString("note"), index + 1, r.mode, r.state ?: "", r.valid, r.totalMs ?: "", r.waitMs ?: "", r.error ?: "", r.raw, item.optJSONArray("samples")?.getJSONObject(index)?.optJSONObject("evidence")?.toString() ?: "").joinToString(",") { csv(it) }
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
