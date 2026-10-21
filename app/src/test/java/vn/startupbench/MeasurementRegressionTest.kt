package vn.startupbench

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class MeasurementRegressionTest {
    private val component = "com.example/.LauncherActivity"
    private val deviceError = """
        Exception occurred while executing 'start':
        java.lang.IllegalArgumentException: Unknown option: --activity-new-task
            at android.content.Intent.parseCommandArgs(Intent.java:9908)
            at com.android.server.am.ActivityManagerShellCommand.makeIntent(ActivityManagerShellCommand.java:1)
    """.trimIndent()

    @Test fun warmUsesNewTaskAndClearTaskFlagsSupportedByIntentParser() {
        val command = LaunchCommand.start(component, "WARM", 10)
        assertFalse(command.contains("--activity-new-task"))
        val flags = Integer.decode(command[command.indexOf("-f") + 1])
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, flags)
        assertEquals("10", command[command.indexOf("--user") + 1])
        assertEquals(component, command.last())
    }
    @Test fun hotDoesNotClearTheTaskBeingResumed() {
        val command = LaunchCommand.start(component, "HOT", 0)
        assertFalse(command.contains("-f")); assertFalse(command.contains("--activity-clear-task"))
    }
    @Test fun screenshotExceptionProducesShortMessage() {
        val summary = MeasurementFailure.short(deviceError)
        assertTrue(summary.contains("--activity-new-task"))
        assertFalse(summary.contains("Intent.java")); assertFalse(summary.contains("\n"))
        assertTrue(summary.length <= 150)
    }
    @Test fun historicalFlattenedTraceAlsoStaysBounded() {
        val summary = MeasurementFailure.short("BenchError: " + deviceError.replace('\n', ' '))
        assertFalse(summary.contains("ActivityManagerShellCommand")); assertTrue(summary.length <= 150)
        assertTrue(MeasurementFailure.short("x".repeat(5000)).length <= 150)
    }
    @Test fun commandErrorDoesNotAbortHotAfterWarm() {
        assertFalse(MeasurementFailure.stopsSession("BenchError: Unsupported option\nBenchErrorType: COMMAND\n$deviceError", true))
        assertTrue(MeasurementFailure.stopsSession("BenchErrorType: CONNECTION", true))
        assertTrue(MeasurementFailure.stopsSession("BenchErrorType: SCREEN", true))
        assertTrue(MeasurementFailure.stopsSession("BenchErrorType: COMMAND", false))
        assertTrue(MeasurementFailure.stopsSession("java.lang.SecurityException: Permission denied", true))
    }
    @Test fun rawTraceIsPreservedAndNeverCountedAsValidTiming() {
        val raw = "BenchError: Lệnh thất bại\nBenchErrorType: COMMAND\n$deviceError"
        val result = LaunchResult.parse("WARM", raw)
        assertFalse(result.valid); assertEquals(raw, result.raw); assertNull(result.totalMs)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidTargetBeforeExecutingCommands() {
        LaunchCommand.start("com.example/.Main;reboot", "COLD", 0)
    }
}
