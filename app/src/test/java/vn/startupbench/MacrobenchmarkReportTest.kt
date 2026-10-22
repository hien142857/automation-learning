package vn.startupbench

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MacrobenchmarkReportTest {
    private fun report() = JSONObject().put("source", MacrobenchmarkReport.SOURCE)
        .put("scope", "local-lab-not-play-console").put("targetPackage", "com.example.app").put("targetVersion", "build456")
        .put("results", JSONArray().put(JSONObject().put("mode", "HOT").put("planned", 3)
            .put("ttidMs", JSONArray().put(1500.0).put(1500.01))))

    @Test fun importedTtidPreservesFractionsAndMissingSamplesWithoutFakeAndroidResults() {
        val session = MacrobenchmarkReport.read(report().toString(), 1234)
        assertEquals("1234", session.getString("id"))
        assertEquals("com.example.app", session.getString("package"))
        assertEquals("completed_with_errors", session.getString("status"))
        val stats = StartupVitals.from(session, "HOT")
        assertEquals(3, stats.attempted); assertEquals(2, stats.measured)
        assertEquals(1, stats.missing); assertEquals(1, stats.slow)
        assertEquals(50.0, stats.slowPercent!!, 0.0)
        assertTrue(session.androidResults().isEmpty()); assertTrue(session.readyValues("HOT").isEmpty())
        assertNull(StartupVitals.from(session, "COLD").slowPercent)
    }

    @Test fun refusesMixedSourcesDuplicateModesAndInvalidDurations() {
        fun reject(data: JSONObject) { assertThrows(Exception::class.java) { MacrobenchmarkReport.read(data.toString()) } }
        reject(report().put("source", "activity-manager-am-start-W"))
        val duplicate = report(); duplicate.getJSONArray("results").put(duplicate.getJSONArray("results").getJSONObject(0))
        reject(duplicate)
        val bad = report(); bad.getJSONArray("results").getJSONObject(0).put("ttidMs", JSONArray().put(-1))
        reject(bad)
        val wrongType = report(); wrongType.getJSONArray("results").getJSONObject(0).put("ttidMs", JSONArray().put("1501"))
        reject(wrongType)
    }

    @Test fun historyRoundtripAndComparisonKeepTtidSeparateFromActivityManager() {
        val session = MacrobenchmarkReport.read(report().toString(), 9876)
        History(RuntimeEnvironment.getApplication()).use { history ->
            history.save(session)
            val restored = history.all().single { it.getString("id") == "9876" }
            assertEquals(50.0, StartupVitals.from(restored, "HOT").slowPercent!!, 0.0)
        }
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val content = LinearLayout(activity)
            activity.setContentView(content)
            val android = JSONObject(session.toString()).put("startupSource", "activity-manager-am-start-W")
            ComparisonScreen(activity, content) {}.show(session, android)
            fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
            val texts = views(content).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.any { it.contains("Khác nguồn") })
            assertFalse(texts.any { it.contains("điểm %") && (it.contains("+0.00") || it.contains("-50.00")) })
        } finally { controller.pause().stop().destroy() }
    }
}
