package vn.startupbench

import org.junit.Assert.*
import org.junit.Test

class SampleStatisticsTest {
    @Test fun arithmeticMeanIsNotMedianForThreeUnequalSamples() {
        val s = SampleStatistics.of(listOf(1000, 2000, 9000))
        assertEquals(3, s.count); assertEquals(4000.0, s.mean!!, .0001); assertEquals(2000.0, s.median!!, 0.0)
        assertEquals(1000L, s.min); assertEquals(9000L, s.max)
    }
    @Test fun missingGroupHasNoSyntheticZeroAverage() {
        val s = SampleStatistics.of(emptyList())
        assertEquals(0, s.count); assertNull(s.mean); assertNull(s.median); assertNull(s.min); assertNull(s.max)
    }
    @Test fun oneSampleStillHasNumbersWithoutClaimingStability() {
        val s = SampleStatistics.of(listOf(3628))
        assertEquals(3628.0, s.mean!!, 0.0); assertEquals(s.min, s.max)
    }
    @Test fun actualZeroAndLargeDurationsArePreserved() {
        assertEquals(0.0, SampleStatistics.of(listOf(0)).mean!!, 0.0)
        assertTrue(SampleStatistics.of(listOf(Long.MAX_VALUE, Long.MAX_VALUE)).mean!! > 0)
    }
    @Test fun meanComparisonUsesMeanForBothDeltaAndPercent() {
        val result = ComparisonAssessment.assess(listOf(1000, 2000, 9000), listOf(1000, 1000, 1000), true, true, statistic = SummaryStatistic.MEAN)
        assertEquals(4000.0, result.baseline!!, 0.0); assertEquals(-3000.0, result.delta!!, 0.0); assertEquals(-75.0, result.percent!!, 0.0)
        assertEquals(ComparisonVerdict.INCONCLUSIVE, result.verdict) // Numbers remain visible with fewer than five samples.
    }
    @Test fun extremeOutlierDoesNotProduceConfidentMeanOptimizationAdvice() {
        val result = ComparisonAssessment.assess(listOf(100, 100, 100, 100, 10000), List(5) { 100L }, true, true, statistic = SummaryStatistic.MEAN)
        assertEquals(ComparisonVerdict.INCONCLUSIVE, result.verdict)
    }
}
