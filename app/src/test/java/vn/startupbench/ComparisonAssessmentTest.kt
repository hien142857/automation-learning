package vn.startupbench

import org.junit.Assert.*
import org.junit.Test

class ComparisonAssessmentTest {
    private fun assess(a: List<Long> = List(5) { 100L }, b: List<Long> = List(5) { 80L }, same: Boolean = true, complete: Boolean = true) =
        ComparisonAssessment.assess(a, b, same, complete)
    @Test fun clearImprovementUsesCandidateMinusBaseline() {
        val r = assess()
        assertEquals(ComparisonVerdict.IMPROVED, r.verdict)
        assertEquals(-20.0, r.delta!!, 0.0); assertEquals(-20.0, r.percent!!, 0.0)
    }
    @Test fun swappingSessionsReversesDirectionAndUsesNewDenominator() {
        val r = assess(List(5) { 80L }, List(5) { 100L })
        assertEquals(ComparisonVerdict.REGRESSED, r.verdict)
        assertEquals(20.0, r.delta!!, 0.0); assertEquals(25.0, r.percent!!, 0.0)
    }
    @Test fun oneFastSampleDoesNotEstablishOptimization() {
        assertEquals(ComparisonVerdict.INCONCLUSIVE, assess(listOf(100), listOf(1)).verdict)
    }
    @Test fun smallChangeDoesNotGetAnOptimizationVerdict() {
        assertEquals(ComparisonVerdict.UNCHANGED, assess(b = List(5) { 96L }).verdict)
        assertEquals(ComparisonVerdict.UNCHANGED, assess(a = List(5) { 1000L }, b = List(5) { 980L }).verdict)
    }
    @Test fun differenceMustExceedMeasurementVariation() {
        val r = assess(listOf(80, 90, 100, 110, 120), listOf(90, 100, 115, 130, 140))
        assertEquals(ComparisonVerdict.INCONCLUSIVE, r.verdict)
        assertTrue(r.title.contains("dao động"))
    }
    @Test fun medianAndMadResistOneExtremeOutlier() {
        assertEquals(ComparisonVerdict.IMPROVED, assess(listOf(100, 100, 100, 100, 10000), listOf(80, 80, 80, 80, 10000)).verdict)
    }
    @Test fun unequalConditionsPreventOptimizationAdviceEvenForLargeDifference() {
        assertEquals(ComparisonVerdict.INCONCLUSIVE, assess(same = false).verdict)
    }
    @Test fun incompleteOrFailedRunsCannotClaimImprovement() {
        assertEquals(ComparisonVerdict.INCONCLUSIVE, assess(complete = false).verdict)
    }
    @Test fun missingSamplesDoNotBecomeZeroMilliseconds() {
        val r = assess(b = emptyList())
        assertEquals(ComparisonVerdict.MISSING, r.verdict); assertNull(r.candidate); assertNull(r.percent)
    }
    @Test fun zeroBaselineNeverCreatesInfinitePercentOrOptimizationClaim() {
        val r = assess(a = List(5) { 0L }, b = List(5) { 0L })
        assertNull(r.percent); assertEquals(ComparisonVerdict.INCONCLUSIVE, r.verdict)
    }
    @Test fun mixedImprovementAndRegressionCannotClaimOverallOptimization() {
        val summary = ComparisonAssessment.overall(listOf(assess(), assess(b = List(5) { 120L })))
        assertTrue(summary.contains("chưa tối ưu đồng đều"))
    }
    @Test fun partialResultsAreExplicitlyLimited() {
        val summary = ComparisonAssessment.overall(listOf(assess(), assess(b = emptyList())))
        assertTrue(summary.contains("chưa đủ cơ sở"))
    }
    @Test fun differenceInsideObservationIntervalCannotClaimOptimization() {
        val result = ComparisonAssessment.assess(List(5) { 1000L }, List(5) { 800L }, true, true, 250.0)
        assertEquals(ComparisonVerdict.INCONCLUSIVE, result.verdict)
        assertTrue(result.title.contains("khoảng quan sát"))
    }
}
