package vn.startupbench

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class StartupVitalsTest {
    private fun sample(mode: String, time: Long?, state: String = mode) = JSONObject()
        .put("mode", mode).put("raw", "Status: ok\nLaunchState: $state\n${time?.let { "TotalTime: $it\n" } ?: ""}")
        .put("evidence", JSONObject().put("status", "timeout"))

    @Test fun slowBoundaryIsStrictAndIndependentOfUiAdsAndTimeout() {
        for (mode in listOf("COLD", "WARM", "HOT")) {
            val threshold = StartupVitals.threshold(mode)
            val session = JSONObject().put("samples", JSONArray().put(sample(mode, threshold-1))
                .put(sample(mode, threshold)).put(sample(mode, threshold+1)))
            val result = StartupVitals.from(session, mode)
            assertEquals(3, result.measured); assertEquals(1, result.slow)
            assertEquals(100.0/3, result.slowPercent!!, .00001)
            assertTrue(session.readyValues(mode).isEmpty())
        }
    }

    @Test fun errorsWrongStateAndMissingHotAreNotFastMeasurements() {
        val session = JSONObject().put("samples", JSONArray().put(sample("HOT", null))
            .put(sample("HOT", 20, "COLD")).put(sample("HOT", -1)).put(sample("HOT", 0)))
        val result = StartupVitals.from(session, "HOT")
        assertEquals(4, result.attempted); assertEquals(1, result.measured)
        assertEquals(3, result.missing); assertEquals(0.0, result.slowPercent!!, 0.0)
        assertNull(StartupVitals.from(session, "COLD").slowPercent)
    }
}
