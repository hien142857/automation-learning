package vn.startupbench

import org.junit.Assert.*
import org.junit.Test

class LaunchResultTest {
    private fun output(state: String = "COLD", total: String = "237") = """
        Status: ok
        LaunchState: $state
        Activity: com.example/.MainActivity
        TotalTime: $total
        WaitTime: 250
        Complete
    """.trimIndent()

    @Test fun validColdPreservesAndroidTiming() {
        val result = LaunchResult.parse("COLD", output())
        assertTrue(result.valid); assertEquals(237L, result.totalMs); assertEquals(250L, result.waitMs)
    }
    @Test fun warmThatBecomesColdIsRejected() {
        val result = LaunchResult.parse("WARM", output())
        assertFalse(result.valid); assertTrue(result.error!!.contains("Android báo COLD"))
    }
    @Test fun hotWithoutTotalTimeCannotUseWaitTime() {
        val result = LaunchResult.parse("HOT", "Status: ok\nLaunchState: HOT\nWaitTime: 20\n")
        assertFalse(result.valid); assertNull(result.totalMs)
    }
    @Test fun actualZeroIsAcceptedForHot() { assertTrue(LaunchResult.parse("HOT", output("HOT", "0")).valid) }
    @Test fun negativeAndMissingStateAreRejected() {
        assertFalse(LaunchResult.parse("HOT", output("HOT", "-1")).valid)
        assertFalse(LaunchResult.parse("COLD", "Status: ok\nTotalTime: 120\n").valid)
    }
    @Test fun serviceAndAndroidErrorsCannotPass() {
        assertFalse(LaunchResult.parse("COLD", "BenchError: Permission denied").valid)
        assertFalse(LaunchResult.parse("COLD", output().replace("Status: ok", "Status: timeout")).valid)
    }
    @Test fun windowsLineEndingsAndExtraOutputWork() {
        assertTrue(LaunchResult.parse("COLD", "Starting: Intent\r\n" + output().replace("\n", "\r\n")).valid)
    }
    @Test fun medianHandlesEvenOddEmptyAndLargeValues() {
        assertNull(LaunchResult.median(emptyList()))
        assertEquals(20.0, LaunchResult.median(listOf(100, 10, 20))!!, 0.0)
        assertEquals(15.5, LaunchResult.median(listOf(11, 20))!!, 0.0)
        assertTrue(LaunchResult.median(listOf(Long.MAX_VALUE, Long.MAX_VALUE))!! > 0)
    }
}
