package vn.startupbench

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class StartupSampleTest {
    private class Remote(val failPreparation: Boolean = false) : IMeasureService.Stub() {
        val calls = mutableListOf<String>()
        override fun prepareLaunch(component: String, mode: String, userId: Int): String {
            calls.add("prepare:$mode"); return if (failPreparation) "BenchError: denied" else "Prepared: ok"
        }
        override fun launchOnly(component: String, mode: String, userId: Int): String {
            calls.add("launch:$mode"); return "Status: ok\nLaunchState: $mode\nTotalTime: 6001\n"
        }
        override fun destroy() {}
    }
    @Test fun coldNeedsNoAccessibilityAndPreservesSlowTiming() {
        val remote = Remote()
        val result = StartupSample.run(remote, "com.example/.Main", "COLD", 0)
        assertEquals(listOf("prepare:COLD", "launch:COLD"), remote.calls)
        assertEquals(6001L, LaunchResult.parse("COLD", result.raw).totalMs)
        assertEquals("android_only", result.evidence.getString("status"))
    }
    @Test fun warmAndHotPrepareBeforeMeasuringAndKeepFailureVisible() {
        for (mode in listOf("WARM", "HOT")) {
            val remote = Remote()
            StartupSample.run(remote, "com.example/.Main", mode, 0)
            assertEquals(listOf("launch:HOT", "prepare:$mode", "launch:$mode"), remote.calls)
        }
        val remote = Remote(true)
        val result = StartupSample.run(remote, "com.example/.Main", "COLD", 0)
        assertFalse(LaunchResult.parse("COLD", result.raw).valid)
        assertEquals(listOf("prepare:COLD"), remote.calls)
    }
}
