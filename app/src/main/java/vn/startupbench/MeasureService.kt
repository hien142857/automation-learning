package vn.startupbench

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs as shell inside a Shizuku UserService, never as an exported Android service. */
class MeasureService : IMeasureService.Stub() {
    private class CommandFailure(val output: String) : Exception("Lệnh Android thất bại")
    private fun command(vararg args: String): String {
        val process = ProcessBuilder(args.toList()).redirectErrorStream(true).start()
        val reader = Executors.newSingleThreadExecutor()
        try {
            val output = reader.submit<String> { process.inputStream.bufferedReader().use { it.readText() } }
            if (!process.waitFor(35, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("Lệnh Android quá hạn 35 giây")
            }
            val text = output.get(3, TimeUnit.SECONDS)
            if (process.exitValue() != 0) throw CommandFailure(text)
            return text
        } finally { reader.shutdownNow() }
    }

    @Synchronized override fun prepareLaunch(component: String, mode: String, userId: Int): String = try {
        LaunchCommand.start(component, mode, userId)
        if (mode == "COLD") command("am", "force-stop", "--user", userId.toString(), component.substringBefore('/'))
        command("input", "keyevent", "KEYCODE_HOME")
        Thread.sleep(1500)
        "Prepared: ok"
    } catch (e: Exception) { "BenchError: ${MeasurementFailure.short(e.message.orEmpty())}" }

    @Synchronized override fun launchOnly(component: String, mode: String, userId: Int): String = try {
        val args = LaunchCommand.start(component, mode, userId)
        val clock = android.os.SystemClock.uptimeMillis()
        command(*args.toTypedArray()) + "\nBenchLaunchUptimeMs: $clock\n"
    } catch (e: CommandFailure) { "BenchError: ${MeasurementFailure.short(e.output)}\n${e.output.take(32000)}" }
    catch (e: Exception) { "BenchError: ${MeasurementFailure.short(e.message.orEmpty())}\n${e.stackTraceToString().take(8000)}" }
    override fun destroy() { kotlin.system.exitProcess(0) }
}
