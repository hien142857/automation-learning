package vn.startupbench

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class GuidedAutomationTest {
    private val criterion = ReadyCriterion("com.example", "com.example:id/home", "")
    private class Remote : IMeasureService.Stub() {
        val calls = mutableListOf<String>()
        override fun prepareLaunch(component: String, mode: String, userId: Int): String { calls.add("prepare:$mode"); return "Prepared: ok" }
        override fun launchOnly(component: String, mode: String, userId: Int): String {
            calls.add("launch:$mode")
            return "Status: ok\nLaunchState: $mode\nTotalTime: 100\nBenchLaunchUptimeMs: 1000\n"
        }
        override fun destroy() {}
    }
    private inner class Observer(val withAds: Boolean = false, val timeout: Boolean = false) : ReadinessObserver {
        var prompts = 0; var interactions = 0; var waits = 0
        override fun arm(target: ReadyCriterion, message: String) {}
        override fun evidence(launchAt: Long) = JSONObject().put("status", if (timeout) "timeout" else "marker_observed")
            .put("launchUptimeMs", launchAt).put("firstMatchUptimeMs", launchAt + 4000)
            .put("confirmedStableUptimeMs", launchAt + 5500).put("lastMissingUptimeMs", launchAt + 3800)
            .put("criterion", criterion.json()).put("targetPackage", criterion.packageName).put("foregroundPackage", criterion.packageName)
            .put("assisted", withAds).put("interrupted", false).put("blockerVisible", false).put("blockerScanComplete", true)
        override fun awaitReady(launchAt: Long, timeoutMs: Long): JSONObject { waits++; return evidence(launchAt) }
        override fun ask(title: String, message: String, choices: List<String>): Int { prompts++; return 1 }
        override fun allowInteraction(message: String): Boolean { interactions++; return true }
    }
    @Test fun coldAfterUserClosesAdFinishesWithoutManualCheckOrConfirmation() {
        BenchEngine.cancel = false
        val remote = Remote(); val observer = Observer(withAds = true)
        val result = GuidedSample.run(remote, observer, criterion, "com.example/.Main", "COLD", 0)
        assertEquals("next", result.decision); assertEquals(0, observer.prompts); assertEquals(0, observer.interactions)
        assertTrue(EvidencePolicy.launchAssisted(result.evidence, result.raw))
        assertFalse(result.evidence.optBoolean("testerConfirmed"))
        assertEquals(4000L, result.evidence.optLong("firstMatchUptimeMs") - result.evidence.optLong("launchUptimeMs"))
        assertEquals(listOf("prepare:COLD", "launch:COLD"), remote.calls)
    }
    @Test fun warmAndHotPreparationAlsoProceedAutomatically() {
        BenchEngine.cancel = false
        for (mode in listOf("WARM", "HOT")) {
            val remote = Remote(); val observer = Observer()
            val result = GuidedSample.run(remote, observer, criterion, "com.example/.Main", mode, 0)
            assertEquals("next", result.decision); assertEquals(0, observer.prompts); assertEquals(0, observer.interactions)
            assertEquals(2, observer.waits)
            assertEquals(listOf("launch:HOT", "prepare:$mode", "launch:$mode"), remote.calls)
            assertTrue(EvidencePolicy.launchAccepted(result.evidence, result.raw))
        }
    }
    @Test fun missingMarkerStillAsksForGuidanceAndNeverCreatesSuccessfulEvidence() {
        BenchEngine.cancel = false
        val observer = Observer(timeout = true)
        val result = GuidedSample.run(Remote(), observer, criterion, "com.example/.Main", "COLD", 0)
        assertEquals("skip", result.decision); assertEquals(1, observer.prompts)
        assertFalse(EvidencePolicy.accepted(result.evidence)); assertFalse(EvidencePolicy.assisted(result.evidence))
    }
}
