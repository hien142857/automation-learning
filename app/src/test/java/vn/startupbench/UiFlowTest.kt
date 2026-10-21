package vn.startupbench

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class UiFlowTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun navigationShowsOneSelectedDestinationAndSwitchesToHistory() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            fun nav() = descendants(activity.window.decorView).filterIsInstance<RadioGroup>().single()
            assertTrue((nav().getChildAt(0) as RadioButton).isChecked)
            assertFalse((nav().getChildAt(1) as RadioButton).isChecked)
            nav().getChildAt(1).performClick()
            assertTrue((nav().getChildAt(1) as RadioButton).isChecked)
            assertFalse((nav().getChildAt(0) as RadioButton).isChecked)
            assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Lịch sử & đối chứng" })
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun appPickerShowsLoadingWhileWorkerIsBlockedAndThenShowsResults() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        var workerThread: Thread? = null
        val picker = AppPickerDialog(controller.get(), onSelect = {}, loadApps = {
            workerThread = Thread.currentThread(); started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            listOf(AppChoice("Test app", "com.example"))
        })
        try {
            picker.show()
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            assertTrue(descendants(dialog.window!!.decorView).filterIsInstance<ProgressBar>().any { it.visibility == View.VISIBLE && it.isIndeterminate })
            assertNotEquals(Looper.getMainLooper().thread, workerThread)
            // The main looper remains responsive even though the app query has not finished.
            shadowOf(Looper.getMainLooper()).idle()
            release.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var list: ListView? = null
            while (System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                list = descendants(dialog.window!!.decorView).filterIsInstance<ListView>().firstOrNull { it.adapter?.count == 1 }
                if (list != null) break
                Thread.sleep(10)
            }
            assertNotNull(list); assertEquals(View.VISIBLE, list!!.visibility)
        } finally { release.countDown(); picker.dismiss(); controller.pause().stop().destroy() }
    }

    @Test fun comparisonUsesDifferentColorsAndUpdatesDirectionWhenSwapped() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            activity.setContentView(content)
            fun session(time: Long, version: String) = JSONObject().apply {
                put("id", "1000"); put("package", "com.example"); put("label", "Test app"); put("version", version)
                put("versionCode", 1); put("status", "completed"); put("repeats", 5)
                put("fingerprint", "device"); put("protocol", "same"); put("component", "com.example/.Main")
                put("powerSave", false); put("plugged", 0)
                put("samples", JSONArray().apply {
                    repeat(5) { put(JSONObject().put("mode", "COLD").put("raw", "Status: ok\nLaunchState: COLD\nTotalTime: $time\n")) }
                })
            }
            ComparisonScreen(activity, content) {}.show(session(100, "1"), session(80, "2"))
            fun texts() = descendants(content).filterIsInstance<TextView>()
            assertNotEquals(texts().first { it.text.toString() == "A · Mốc" }.currentTextColor,
                texts().first { it.text.toString() == "B · Đối chứng" }.currentTextColor)
            assertTrue(texts().any { it.text.toString().contains("B có dấu hiệu nhanh hơn") })
            texts().filterIsInstance<Button>().single { it.text.toString() == "Đổi A ↔ B" }.performClick()
            assertTrue(texts().any { it.text.toString().contains("B có dấu hiệu chậm hơn") })
        } finally { controller.pause().stop().destroy() }
    }
}
