package vn.startupbench

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CohortStatisticsUiTest {
    private fun session(values: List<Long>) = JSONObject().apply {
        put("id", "1000"); put("label", "App with ads"); put("package", "com.example"); put("version", "1"); put("versionCode", 1)
        put("status", "completed"); put("repeats", values.size); put("modes", JSONArray().put("COLD"))
        put("fingerprint", "same"); put("protocol", "same"); put("component", "com.example/.Main"); put("powerSave", false); put("plugged", 0)
        put("readinessCriterion", ReadyCriterion("com.example", "com.example:id/home", "").json())
        put("samples", JSONArray().apply { values.forEach { value -> put(JSONObject().put("mode", "COLD")
            .put("raw", "Status: ok\nLaunchState: COLD\nTotalTime: 200\nBenchLaunchUptimeMs: 1000\n")
            .put("evidence", JSONObject().put("status", "assisted_ui").put("assisted", true).put("interrupted", false).put("testerConfirmed", true)
                .put("launchUptimeMs", 1000).put("firstMatchUptimeMs", 1000 + value).put("lastMissingUptimeMs", 990 + value)
                .put("confirmedStableUptimeMs", 1500 + value).put("targetPackage", "com.example").put("foregroundPackage", "com.example")
                .put("criterion", ReadyCriterion("com.example", "com.example:id/home", "").json()))) } })
    }
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()

    @Test fun assistedOnlySummaryProminentlyShowsRealArithmeticAverage() {
        val previous = BenchEngine.session
        BenchEngine.session = session(listOf(1000, 2000, 9000))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val texts = views(controller.get().window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.contains("Trung bình 4000.0 ms"))
            assertTrue(texts.any { it.contains("trung vị 2000.0 ms") })
            assertTrue(texts.any { it.contains("xác nhận 3") && it.contains("chưa đủ bằng chứng 0") })
        } finally { controller.pause().stop().destroy(); BenchEngine.session = previous }
    }
    @Test fun assistedComparisonDefaultsToAvailableCohortAndShowsMeanWithThreeSamples() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get(); val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }; activity.setContentView(content)
            ComparisonScreen(activity, content) {}.show(session(listOf(1000, 2000, 9000)), session(listOf(1000, 1000, 1000)))
            val texts = views(content).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.any { it.contains("nhóm Có ads/thao tác") })
            assertTrue(texts.contains("4000.0 ms")); assertTrue(texts.contains("1000.0 ms"))
            assertTrue(texts.any { it.contains("-3000.0 ms") && it.contains("-75.0%") })
            assertFalse(texts.contains("B có dấu hiệu nhanh hơn"))
        } finally { controller.pause().stop().destroy() }
    }
}
