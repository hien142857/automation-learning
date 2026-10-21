package vn.startupbench

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale
import java.util.concurrent.Executors

data class AppChoice(val label: String, val packageName: String) {
    val searchText = "$label $packageName".lowercase(Locale.ROOT)
}

/** Opens immediately; package-manager calls and label resolution never run on the UI thread. */
class AppPickerDialog(private val activity: Activity, private val onSelect: (String) -> Unit,
                      private val loadApps: (() -> List<AppChoice>)? = null) {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var closed = false
    private var choices = emptyList<AppChoice>()
    private var visible = emptyList<AppChoice>()
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private val accent = Color.rgb(91, 224, 194)
    private val search = EditText(activity).apply {
        hint = "Tìm tên app hoặc package"; isSingleLine = true; isEnabled = false
        setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
    }
    private val list = ListView(activity).apply { visibility = View.GONE }
    private val spinner = ProgressBar(activity).apply { isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(accent) }
    private val status = TextView(activity).apply {
        text = activity.getString(R.string.loading_apps); setTextColor(Color.WHITE); textSize = 15f
        gravity = Gravity.CENTER; setPadding(dp(12), dp(16), dp(12), dp(8))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val retry = Button(activity).apply { text = activity.getString(R.string.retry); visibility = View.GONE; setOnClickListener { load() } }
    private val loading = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(spinner, LinearLayout.LayoutParams(dp(40), dp(40))); addView(status); addView(retry)
    }
    private val frame = FrameLayout(activity).apply {
        addView(list, FrameLayout.LayoutParams(-1, -1))
        addView(loading, FrameLayout.LayoutParams(-1, -1))
    }
    private val dialog = AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
        .setTitle("Chọn ứng dụng")
        .setView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(8)); addView(search)
            val height = (activity.resources.configuration.screenHeightDp / 2).coerceIn(140, 380)
            addView(frame, LinearLayout.LayoutParams(-1, dp(height)))
        }).setNegativeButton("Đóng", null).create()

    init {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        list.setOnItemClickListener { _, _, position, _ ->
            visible.getOrNull(position)?.let { onSelect(it.packageName); dialog.dismiss() }
        }
        dialog.setOnDismissListener {
            closed = true; worker.shutdownNow(); handler.removeCallbacksAndMessages(null)
        }
    }
    fun show() { dialog.show(); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent); load() }
    fun dismiss() { dialog.dismiss() }

    private fun load() {
        if (closed) return
        list.visibility = View.GONE; loading.visibility = View.VISIBLE
        spinner.visibility = View.VISIBLE; retry.visibility = View.GONE
        status.text = activity.getString(R.string.loading_apps); search.isEnabled = false
        // Resolve labels once in the worker, then filter cached strings as the user types.
        val pm = activity.applicationContext.packageManager
        worker.execute {
            val result = runCatching {
                loadApps?.invoke() ?: pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.packageName !in listOf(activity.packageName, "moe.shizuku.privileged.api") }
                    .distinctBy { it.activityInfo.packageName }
                    .map {
                        val pkg = it.activityInfo.packageName
                        AppChoice(runCatching { it.loadLabel(pm).toString() }.getOrDefault(pkg), pkg)
                    }.sortedBy { it.label.lowercase(Locale.ROOT) }
            }
            handler.post {
                if (closed || activity.isDestroyed || activity.isFinishing) return@post
                spinner.visibility = View.GONE
                result.onSuccess {
                    choices = it; search.isEnabled = true
                    dialog.setTitle("Chọn ứng dụng (${it.size})"); filter(search.text.toString())
                }.onFailure {
                    status.text = activity.getString(R.string.load_apps_failed)
                    retry.visibility = View.VISIBLE
                }
            }
        }
    }
    private fun filter(query: String) {
        visible = choices.filter { it.searchText.contains(query.trim().lowercase(Locale.ROOT)) }
        val rows = visible
        list.adapter = object : ArrayAdapter<AppChoice>(activity, android.R.layout.simple_list_item_2, android.R.id.text1, rows) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                row.findViewById<TextView>(android.R.id.text1).apply { text = rows[position].label; setTextColor(Color.WHITE) }
                row.findViewById<TextView>(android.R.id.text2).apply { text = rows[position].packageName; setTextColor(Color.LTGRAY) }
                return row
            }
        }
        loading.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (visible.isEmpty()) View.GONE else View.VISIBLE
        if (visible.isEmpty()) status.text = activity.getString(R.string.no_matching_apps)
    }
}
