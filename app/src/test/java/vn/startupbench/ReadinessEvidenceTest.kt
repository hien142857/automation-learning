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
class ReadinessEvidenceTest {
    private fun proof() = JSONObject().put("status", "verified_ui").put("testerConfirmed", true)
        .put("launchUptimeMs", 1000).put("firstMatchUptimeMs", 2000).put("confirmedStableUptimeMs", 2500)
        .put("lastMissingUptimeMs", 1800)
        .put("targetPackage", "com.example").put("foregroundPackage", "com.example").put("assisted", false).put("interrupted", false)
        .put("criterion", ReadyCriterion("com.example", "com.example:id/home", "").json())
    private fun session(evidence: JSONObject?) = JSONObject().put("samples", JSONArray().put(JSONObject()
        .put("mode", "COLD").put("raw", "Status: ok\nLaunchState: COLD\nTotalTime: 100\nBenchLaunchUptimeMs: 1000\n")
        .apply { if (evidence != null) put("evidence", evidence) }))
    private fun automatic() = proof().put("testerConfirmed", false)
        .put("confirmationMethod", "automatic-marker-v1").put("automaticConfirmed", true)
        .put("blockerScanComplete", true).put("blockerVisible", false).put("confirmedStableUptimeMs", 3500)
    @Test fun automaticEvidenceIsExplicitAndNeverFabricatesTesterConfirmation() {
        assertTrue(EvidencePolicy.accepted(automatic()))
        assertFalse(EvidencePolicy.accepted(automatic().put("testerConfirmed", true)))
        assertFalse(EvidencePolicy.accepted(automatic().put("automaticConfirmed", false)))
        assertFalse(EvidencePolicy.accepted(automatic().put("confirmedStableUptimeMs", 3499)))
        assertFalse(EvidencePolicy.accepted(automatic().put("criterion", ReadyCriterion("com.example", "com.example:id/home", "", autoContinue = false).json())))
    }
    @Test fun automaticProofRejectsVisibleAdsOrIncompleteScans() {
        assertFalse(EvidencePolicy.accepted(automatic().put("blockerVisible", true)))
        assertFalse(EvidencePolicy.accepted(automatic().put("blockerScanComplete", false)))
        assertFalse(EvidencePolicy.accepted(automatic().apply { remove("blockerVisible") }))
    }
    @Test fun automaticallyResumedAdSampleGoesToAssistedStatistics() {
        val p = automatic().put("assisted", true).put("status", "assisted_ui")
        assertTrue(EvidencePolicy.assisted(p)); assertFalse(EvidencePolicy.accepted(p))
        assertEquals(listOf(1000L), session(p).assistedValues("COLD"))
    }
    @Test fun discoveredMarkerNeedsCandidateEvidenceNotJustAutomaticConfirmation() {
        val e = automatic().put("criterion", ReadyCriterion("com.example", "", "").json())
        assertFalse(EvidencePolicy.accepted(e))
        e.put("autoScreen", AutoScreen.evaluate(listOf(ScreenElement("Text", "title", "Home", false),
            ScreenElement("Button", "create", "Create", true), ScreenElement("Button", "history", "History", true)), true, false).json())
        assertTrue(EvidencePolicy.accepted(e))
        assertEquals(listOf(1000L), session(e).readyValues("COLD"))
        assertEquals(1, session(e).discoveredSignatures().size)
        e.getJSONObject("autoScreen").put("reason", "insufficient_content")
        assertFalse(EvidencePolicy.accepted(e))
    }
    @Test fun failureReasonsDistinguishAdsLoadingUnreadableAndUnstable() {
        val e = automatic().put("criterion", ReadyCriterion("com.example", "", "").json())
        assertTrue(ObservationFailure.explain(e.put("blockerVisible", true)).contains("quảng cáo"))
        e.put("blockerVisible", false).put("discoveryReason", "loading_visible")
        assertTrue(ObservationFailure.explain(e).contains("Splash/đang tải"))
        e.put("discoveryReason", "ui_unreadable")
        assertTrue(ObservationFailure.explain(e).contains("không đọc đủ"))
        e.put("discoveryReason", "candidate")
        assertTrue(ObservationFailure.explain(e).contains("chưa giữ nguyên"))
    }
    @Test fun explicitMarkerDoesNotRequireAutomaticContentThresholds() {
        val e = automatic().put("discoveryReason", "insufficient_content")
        assertTrue(ObservationFailure.explain(e).contains("mốc đã cấu hình"))
        assertFalse(ObservationFailure.explain(e).contains("3 nhãn"))
    }
    @Test fun androidColdTimingAloneDoesNotProveTheAppPassedSplash() {
        val s = session(null)
        assertTrue(s.androidResults().single().valid)
        assertFalse(s.results().single().valid); assertTrue(s.values("COLD").isEmpty()); assertTrue(s.readyValues("COLD").isEmpty())
    }
    @Test fun validUiAndAndroidTimingStaySeparate() {
        val s = session(proof())
        assertEquals(listOf(100L), s.values("COLD")); assertEquals(listOf(1000L), s.readyValues("COLD"))
    }
    @Test fun timeoutNeverBecomesSuccessfulEvenIfAndroidReportedTiming() {
        assertTrue(session(proof().put("status", "timeout")).readyValues("COLD").isEmpty())
    }
    @Test fun falseOrMissingTesterConfirmationCannotEnterStatistics() {
        assertFalse(EvidencePolicy.accepted(proof().put("testerConfirmed", false)))
        assertFalse(EvidencePolicy.accepted(proof().apply { remove("testerConfirmed") }))
    }
    @Test fun adsAndHumanActionsAreKeptInSeparateCohort() {
        val s = session(proof().put("status", "assisted_ui").put("assisted", true))
        assertTrue(s.readyValues("COLD").isEmpty()); assertEquals(listOf(1000L), s.assistedValues("COLD"))
        assertEquals(listOf(100L), s.cohortAndroidValues("COLD", MeasurementCohort.ASSISTED))
        assertTrue(s.cohortAndroidValues("COLD", MeasurementCohort.AUTOMATIC).isEmpty())
    }
    @Test fun manualConfirmationDelayDoesNotChangeReadyObservation() {
        val p = proof().put("testerConfirmedUptimeMs", 90000)
        assertEquals(listOf(1000L), session(p).readyValues("COLD"))
    }
    @Test fun intervalUsesLastMissingObservationNotTesterReactionTime() {
        assertEquals(listOf(200L), session(proof().put("testerConfirmedUptimeMs", 90000)).observationIntervals("COLD"))
        assertFalse(EvidencePolicy.accepted(proof().put("lastMissingUptimeMs", 2100)))
    }
    @Test fun wrongForegroundPackageOrUnstableMarkerIsRejected() {
        assertFalse(EvidencePolicy.accepted(proof().put("foregroundPackage", "com.ad.browser")))
        assertFalse(EvidencePolicy.accepted(proof().put("confirmedStableUptimeMs", 2499)))
    }
    @Test fun markerSeenBeforeLaunchCannotBeCountedForNextLaunch() {
        assertFalse(EvidencePolicy.accepted(proof().put("firstMatchUptimeMs", 999)))
    }
    @Test fun screenInterruptionOrEmptyCriterionInvalidatesEvidence() {
        assertFalse(EvidencePolicy.accepted(proof().put("interrupted", true)))
        assertFalse(EvidencePolicy.accepted(proof().put("criterion", JSONObject())))
    }
    @Test fun launchClockMustComeFromExplicitShellMarker() {
        assertNull(EvidencePolicy.launchClock("TotalTime: 100\nWaitTime: 120"))
        assertEquals(1234L, EvidencePolicy.launchClock("Status: ok\nBenchLaunchUptimeMs: 1234\n"))
    }
    @Test fun wrongLaunchStateRemainsInvalidEvenWithGoodUiEvidence() {
        val s = session(proof())
        s.getJSONArray("samples").getJSONObject(0).put("mode", "HOT")
        assertTrue(s.readyValues("HOT").isEmpty())
    }
    @Test fun hotUiObservationCanBeVerifiedWithoutInventingAndroidTotalTime() {
        val s = session(proof())
        s.getJSONArray("samples").getJSONObject(0).put("mode", "HOT").put("raw", "Status: ok\nLaunchState: HOT\nBenchLaunchUptimeMs: 1000\n")
        assertFalse(s.androidResults().single().valid)
        assertTrue(s.results().single().valid)
        assertEquals(listOf(1000L), s.readyValues("HOT")); assertTrue(s.values("HOT").isEmpty())
    }
    @Test fun guidancePopupWaitsForTesterChoiceInsteadOfAutoConfirming() {
        val controller = org.robolectric.Robolectric.buildService(ReadinessService::class.java).create()
        val service = controller.get()
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        BenchEngine.cancel = false
        ReadinessService.instance = service
        val previous = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        val result = worker.submit<Int> { service.ask("Cần xử lý ads", "Mẫu chưa đủ bằng chứng. Chọn bước tiếp theo.", listOf("Thử lại", "Bỏ qua", "Dừng")) }
        try {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            var dialog: android.app.AlertDialog? = null
            while (System.nanoTime() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it !== previous && it.isShowing }
                if (dialog != null) break
                Thread.sleep(10)
            }
            assertNotNull(dialog); assertFalse(result.isDone)
            fun views(v: android.view.View): List<android.view.View> = listOf(v) +
                if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
            views(dialog!!.window!!.decorView).filterIsInstance<android.widget.Button>().single { it.text.toString() == "Bỏ qua" }.performClick()
            assertEquals(1, result.get(2, java.util.concurrent.TimeUnit.SECONDS).toInt())
        } finally {
            BenchEngine.cancel = true; service.hide(); ReadinessService.instance = null
            worker.shutdownNow(); controller.destroy(); BenchEngine.cancel = false
        }
    }
    @Test fun adsRecheckCannotBeReclassifiedAsUnassisted() {
        assertFalse(EvidencePolicy.accepted(proof().put("recheckedAfterTesterAction", true)))
        assertFalse(EvidencePolicy.accepted(proof().put("rejectedCandidates", JSONArray().put(JSONObject().put("status", "occluded_candidate_rejected")))))
    }
    @Test fun readinessClockMustMatchActualShellLaunchClock() {
        val s = session(proof().put("launchUptimeMs", 900))
        assertTrue(s.readyValues("COLD").isEmpty())
        val missing = session(proof())
        missing.getJSONArray("samples").getJSONObject(0).put("raw", "Status: ok\nLaunchState: COLD\nTotalTime: 100\n")
        assertTrue(missing.readyValues("COLD").isEmpty())
    }
}
