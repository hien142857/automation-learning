package vn.startupbench

enum class MeasurementCohort(val label: String) { AUTOMATIC("Không ads/thao tác"), ASSISTED("Có ads/thao tác") }
enum class SummaryStatistic(val label: String) {
    MEAN("Trung bình"), MEDIAN("Trung vị (median)");
    fun value(values: List<Long>): Double? = if (this == MEAN) SampleStatistics.of(values).mean else LaunchResult.median(values)
}

data class SampleStatistics(val count: Int, val mean: Double?, val median: Double?, val min: Long?, val max: Long?) {
    companion object {
        fun of(values: List<Long>): SampleStatistics {
            // Avoid overflowing a Long sum; preserve zero as a real observation.
            var mean = 0.0
            values.forEachIndexed { index, value -> mean += (value.toDouble() - mean) / (index + 1) }
            return SampleStatistics(values.size, mean.takeIf { values.isNotEmpty() }, LaunchResult.median(values), values.minOrNull(), values.maxOrNull())
        }
    }
}
