package vn.startupbench

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Observes only the selected target. No text-entry events, screenshots or action replay. */
class ReadinessService : AccessibilityService(), ReadinessObserver {
    companion object { @Volatile var instance: ReadinessService? = null }
    private val handler = Handler(Looper.getMainLooper())
    private val guard = Any()
    @Volatile private var criterion: ReadyCriterion? = null
    private var armAt = 0L
    private val marker = MarkerStability()
    private var foreground = ""
    private var assisted = false
    private var interrupted = false
    private var observerError = ""
    private var lastScan = 0L
    private var maxScanGap = 0L
    private var events = JSONArray()
    private var blockerVisible = false
    private var blockerScanComplete = false
    private var blockers = JSONArray()
    private var autoScreen = ScreenCandidate("ui_unreadable")
    private var previousSignature = ""
    private var discoveryReason = "ui_unreadable"
    private var panel: LinearLayout? = null
    private var label: TextView? = null
    private var prompt: AlertDialog? = null
    private var continueButton: Button? = null
    private var panelLayout: WindowManager.LayoutParams? = null
    private val scan = object : Runnable {
        override fun run() {
            runCatching { inspect() }.onFailure { broken(it) }
            if (criterion != null) handler.postDelayed(this, 200)
        }
    }
    private fun broken(error: Throwable) { synchronized(guard) {
        interrupted = true; marker.stable = -1; observerError = MeasurementFailure.short(error.message.orEmpty())
    } }
    override fun onServiceConnected() { instance = this }
    override fun onInterrupt() { synchronized(guard) { marker.stable = -1; interrupted = true }; hide() }
    override fun onDestroy() { instance = null; handler.removeCallbacksAndMessages(null); hide(); super.onDestroy() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val target = criterion ?: return
        if (event?.packageName?.toString() == target.packageName && event.eventType in listOf(AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)) {
            synchronized(guard) {
                if (event.eventTime >= armAt) {
                    assisted = true
                    if (events.length() < 100) events.put(JSONObject().put("uptimeMs", event.eventTime)
                        .put("type", event.eventType).put("resourceId", runCatching { event.source?.viewIdResourceName.orEmpty() }.getOrDefault("")))
                }
            }
        }
    }
    override fun arm(target: ReadyCriterion, message: String) {
        synchronized(guard) {
            criterion = target; armAt = SystemClock.uptimeMillis(); marker.first = -1; marker.stable = -1
            marker.missing = armAt; foreground = ""; assisted = false; interrupted = false; events = JSONArray()
            previousSignature = ""; autoScreen = ScreenCandidate("ui_unreadable"); discoveryReason = "ui_unreadable"
            blockerVisible = false; blockerScanComplete = false; blockers = JSONArray()
            observerError = ""; lastScan = armAt; maxScanGap = 0
        }
        handler.post { runCatching {
            serviceInfo = serviceInfo.apply { packageNames = arrayOf(target.packageName) }
            showPanel(message); handler.removeCallbacks(scan); handler.post(scan)
        }.onFailure { broken(it) } }
    }
    private fun inspect() {
        val target = criterion ?: return
        val now = SystemClock.uptimeMillis()
        synchronized(guard) { maxScanGap = maxOf(maxScanGap, now - lastScan); lastScan = now }
        if (!getSystemService(android.os.PowerManager::class.java).isInteractive || getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            synchronized(guard) { interrupted = true; marker.stable = -1 }; return
        }
        val root = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root
        val pkg = root?.packageName?.toString().orEmpty()
        fun matches(node: AccessibilityNodeInfo): Boolean {
            if (node.isPassword || !node.isVisibleToUser || !node.isEnabled) return false
            return (target.resourceId.isBlank() || node.viewIdResourceName == target.resourceId) &&
                (target.text.isBlank() || node.text?.toString()?.trim() == target.text)
        }
        val nodes = if (!target.configured || root == null || pkg != target.packageName) emptyList() else if (target.resourceId.isNotBlank())
            root.findAccessibilityNodeInfosByViewId(target.resourceId) else root.findAccessibilityNodeInfosByText(target.text)
        // Inspect bounded visible UI, including descriptions and common SDK close IDs.
        // Unknown/custom-drawn ads remain a limitation; a configured blocker improves detection.
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        if (root != null && pkg == target.packageName) pending.add(root)
        val elements = mutableListOf<ScreenElement>()
        var scanned = 0
        var blocker: String? = null
        while (pending.isNotEmpty() && scanned < 1500) {
            val node = pending.removeFirst(); scanned++
            if (!node.isVisibleToUser || node.isPassword || node.isEditable) continue
            val hint = AdSignals.detect(node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty(),
                node.viewIdResourceName.orEmpty(), target.blockerText)
            if (hint != null) blocker = hint
            elements.add(ScreenElement(node.className?.toString().orEmpty(), node.viewIdResourceName.orEmpty(),
                (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty(), node.isEnabled && (node.isClickable || node.isScrollable)))
            for (i in 0 until node.childCount) node.getChild(i)?.let { pending.add(it) }
        }
        val complete = root != null && pkg == target.packageName && pending.isEmpty()
        val candidate = AutoScreen.evaluate(elements, complete, blocker != null)
        val matched = (if (target.configured) nodes.count { matches(it) } == 1 else candidate.eligible) && blocker == null && (!target.automatic || complete)
        synchronized(guard) {
            if (criterion !== target) return
            foreground = pkg
            autoScreen = candidate
            discoveryReason = candidate.reason
            if (!target.configured && previousSignature != candidate.signature) marker.reset(now)
            previousSignature = candidate.signature
            blockerScanComplete = complete
            if (blocker != null) {
                assisted = true
                if (!blockerVisible && blockers.length() < 100) blockers.put(JSONObject().put("uptimeMs", now).put("signal", blocker))
                label?.text = "Có dấu hiệu quảng cáo. Bạn chỉ cần đóng/bỏ qua trong app; công cụ sẽ tự tiếp tục khi thấy nội dung đích."
            } else if (blockerVisible) label?.text = "Đã hết dấu hiệu quảng cáo. Đang chờ mốc nội dung ổn định để tự tiếp tục…"
            blockerVisible = blocker != null
            if (!target.configured && blocker == null) label?.text = when (candidate.reason) {
                "candidate" -> "Đang kiểm tra giao diện ổn định 1,5 giây. Khi đủ bằng chứng sẽ tự lưu và đo tiếp."
                "loading_visible" -> "Còn dấu hiệu Splash/đang tải; đang chờ nội dung. Chưa có kết quả."
                "ui_unreadable" -> "Chưa đọc đủ giao diện app đích. Đang thử lại; quá thời gian chờ sẽ báo hướng xử lý."
                else -> "Chưa đủ dấu hiệu nội dung để tự chọn mốc. Nếu có ads, bạn chỉ cần đóng/bỏ qua."
            }
            marker.observe(matched, now, if (target.automatic) 1500 else 500)
        }
    }
    override fun evidence(launchAt: Long): JSONObject = synchronized(guard) {
        JSONObject().put("targetPackage", criterion?.packageName.orEmpty()).put("foregroundPackage", foreground)
            .put("launchUptimeMs", launchAt).put("firstMatchUptimeMs", marker.first).put("confirmedStableUptimeMs", marker.stable)
            .put("lastMissingUptimeMs", marker.missing).put("assisted", assisted).put("actions", JSONArray(events.toString()))
            .put("interrupted", interrupted).put("blockerVisible", blockerVisible).put("blockerScanComplete", blockerScanComplete)
            .put("blockerSignals", JSONArray(blockers.toString())).put("autoScreen", autoScreen.json()).put("discoveryReason", discoveryReason)
            .put("observerError", observerError).put("maxObservedScanGapMs", maxScanGap)
            .put("criterion", criterion?.json()).put("observationMethod", "accessibility-visible-enabled-exact-match; stable>=${if (criterion?.automatic == true) 1500 else 500}ms; nominal-poll=200ms; bounded-ad-hint-scan")
            .put("observedUpperMs", if (marker.first >= launchAt) marker.first - launchAt else -1)
            .put("observedLowerMs", (marker.missing - launchAt).coerceAtLeast(0))
    }
    override fun awaitReady(launchAt: Long, timeoutMs: Long): JSONObject {
        val deadline = launchAt + timeoutMs
        while (!BenchEngine.cancel && instance === this) {
            val snapshot = evidence(launchAt)
            if (snapshot.optBoolean("interrupted")) return snapshot.put("status", "interrupted")
            if (snapshot.optLong("firstMatchUptimeMs") >= launchAt && snapshot.optLong("confirmedStableUptimeMs") in launchAt..deadline) return snapshot.put("status", "marker_observed")
            if (SystemClock.uptimeMillis() >= deadline) break
            Thread.sleep(100)
        }
        return evidence(launchAt).put("status", "timeout")
    }
    override fun ask(title: String, message: String, choices: List<String>): Int {
        val latch = CountDownLatch(1)
        var answer = -1
        handler.post {
            if (instance !== this || BenchEngine.cancel) { latch.countDown(); return@post }
            runCatching {
            prompt?.dismiss()
            prompt = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title).setMessage(message).setCancelable(false).create()
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12)
                choices.forEachIndexed { index, choice -> addView(Button(this@ReadinessService).apply {
                    text = choice; isAllCaps = false; setOnClickListener {
                        answer = index; prompt?.dismiss(); prompt = null; latch.countDown()
                    }
                }) }
            }
            prompt!!.setView(buttons)
            prompt!!.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            prompt!!.show()
            }.onFailure { broken(it); latch.countDown() }
        }
        // Waiting for explicit input has no effect on recorded launch/readiness timestamps.
        while (!latch.await(200, TimeUnit.MILLISECONDS)) {
            if (BenchEngine.cancel || instance !== this) { handler.post { prompt?.dismiss(); prompt = null }; return -1 }
        }
        return answer
    }
    override fun allowInteraction(message: String): Boolean {
        val latch = CountDownLatch(1)
        var continued = false
        handler.post {
            runCatching {
            showPanel(message)
            continueButton?.visibility = View.VISIBLE
            continueButton?.setOnClickListener { continued = true; continueButton?.visibility = View.GONE; latch.countDown() }
            }.onFailure { broken(it); latch.countDown() }
        }
        while (!latch.await(200, TimeUnit.MILLISECONDS)) if (BenchEngine.cancel || instance !== this) return false
        return continued
    }
    private fun showPanel(message: String) {
        if (panel == null) {
            panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 10); setBackgroundColor(Color.rgb(25, 36, 52))
                val grip = TextView(this@ReadinessService).apply { text = getString(R.string.drag_guidance); setTextColor(Color.LTGRAY); textSize = 12f }
                var originX = 0; var originY = 0; var downX = 0f; var downY = 0f
                grip.setOnTouchListener { view, event ->
                    val layout = panelLayout ?: return@setOnTouchListener false
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            originX = layout.x; originY = layout.y; downX = event.rawX; downY = event.rawY
                            synchronized(guard) {
                                assisted = true
                                if (events.length() < 100) events.put(JSONObject().put("uptimeMs", SystemClock.uptimeMillis()).put("type", "guidance_panel_drag_started"))
                            }; true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            layout.x = (originX + event.rawX - downX).toInt().coerceIn(0, (resources.displayMetrics.widthPixels - layout.width).coerceAtLeast(0))
                            layout.y = (originY + event.rawY - downY).toInt().coerceIn(0, (resources.displayMetrics.heightPixels - 100).coerceAtLeast(0))
                            synchronized(guard) { assisted = true }
                            runCatching { getSystemService(WindowManager::class.java).updateViewLayout(panel, layout) }; true
                        }
                        android.view.MotionEvent.ACTION_UP -> { view.performClick(); true }
                        else -> false
                    }
                }; addView(grip)
                label = TextView(this@ReadinessService).apply {
                    setTextColor(Color.WHITE); textSize = 13f; maxLines = 4; ellipsize = android.text.TextUtils.TruncateAt.END
                }; addView(label)
                addView(Button(this@ReadinessService).apply {
                    text = getString(R.string.mark_ads); isAllCaps = false
                    setOnClickListener {
                        synchronized(guard) {
                            assisted = true
                            if (events.length() < 100) events.put(JSONObject().put("uptimeMs", SystemClock.uptimeMillis()).put("type", "tester_marked_ads_or_action"))
                        }
                        label?.text = getString(R.string.ads_instructions)
                    }
                })
                continueButton = Button(this@ReadinessService).apply {
                    text = getString(R.string.check_ready_again); isAllCaps = false; visibility = View.GONE
                }; addView(continueButton)
            }
            val density = resources.displayMetrics.density
            panelLayout = WindowManager.LayoutParams(
                minOf((280 * density).toInt(), resources.displayMetrics.widthPixels - (24 * density).toInt()), -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; y = (48 * density).toInt() }
            getSystemService(WindowManager::class.java).addView(panel, panelLayout)
        }
        label?.text = message
    }
    fun hide() {
        synchronized(guard) { criterion = null }
        handler.post {
            handler.removeCallbacks(scan); runCatching { prompt?.dismiss() }; prompt = null
            panel?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }; panel = null; label = null; continueButton = null; panelLayout = null
        }
    }
}
