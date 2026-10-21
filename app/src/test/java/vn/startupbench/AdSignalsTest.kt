package vn.startupbench

import org.junit.Assert.*
import org.junit.Test

class AdSignalsTest {
    @Test fun identifiesSpecificAdLabelsDescriptionsAndSdkIds() {
        assertEquals("ad_label", AdSignals.detect("Skip ad", "", "", ""))
        assertEquals("ad_label", AdSignals.detect("", "Bỏ qua quảng cáo", "", ""))
        assertEquals("ad_resource_id", AdSignals.detect("", "", "pkg:id/mbridge_iv_close", ""))
        assertEquals("ad_label", AdSignals.detect("Skip ad 5 seconds", "", "", ""))
    }
    @Test fun customBlockerRequiresExactMatchAndOrdinaryCloseIsNotProofOfAds() {
        assertEquals("configured_blocker", AdSignals.detect("Nhận thưởng", "", "", "Nhận thưởng"))
        assertNull(AdSignals.detect("Nhận thưởng hôm nay", "", "", "Nhận thưởng"))
        assertNull(AdSignals.detect("Close", "", "pkg:id/close", ""))
        assertNull(AdSignals.detect("My advertisement settings", "", "", ""))
    }
    @Test fun blockerOrMissingMarkerResetsStabilityAndPreservesNewObservedTime() {
        val marker = MarkerStability()
        marker.reset(1000)
        marker.observe(true, 1100, 1500)
        marker.observe(true, 2600, 1500)
        assertEquals(2600L, marker.stable)
        marker.observe(false, 2800, 1500) // ad appears over previously ready content
        assertEquals(-1L, marker.first); assertEquals(-1L, marker.stable)
        marker.observe(true, 5000, 1500) // user closes ad, original launch clock remains unchanged
        marker.observe(true, 6400, 1500)
        assertEquals(-1L, marker.stable)
        marker.observe(true, 6500, 1500)
        marker.observe(true, 6700, 1500)
        assertEquals(5000L, marker.first); assertEquals(2800L, marker.missing)
        assertEquals(6500L, marker.stable)
    }
}
