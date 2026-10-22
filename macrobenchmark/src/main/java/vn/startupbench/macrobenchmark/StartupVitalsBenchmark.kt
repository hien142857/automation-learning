package vn.startupbench.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Measures TTID from Perfetto, independently of Accessibility, ads and content readiness. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalMacrobenchmarkApi::class)
class StartupVitalsBenchmark(private val mode: StartupMode) {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test fun startup() {
        val args = InstrumentationRegistry.getArguments()
        val target = requireNotNull(args.getString("targetPackage")) { "Cần -Pandroid.testInstrumentationRunnerArguments.targetPackage=com.example.app" }
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(target)) { "Package không hợp lệ" }
        require(target != "vn.startupbench" && target != "vn.startupbench.macrobenchmark") { "Chọn app đích, không đo công cụ" }
        val count = args.getString("iterations", "20").toInt()
        require(count in 5..1000) { "iterations cần từ 5 đến 1000" }
        benchmark.measureRepeated(
            packageName = target,
            metrics = listOf(StartupTimingMetric()),
            // Preserve target app data and installed compilation state; record this protocol.
            compilationMode = CompilationMode.Ignore(),
            startupMode = mode,
            iterations = count,
            setupBlock = { pressHome() },
            measureBlock = { startActivityAndWait() }
        )
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "mode={0}")
        fun modes(): List<Array<StartupMode>> = StartupMode.values().map { arrayOf(it) }
    }
}
