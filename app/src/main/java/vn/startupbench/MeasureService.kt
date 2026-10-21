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

    @Synchronized override fun measure(component: String, mode: String, userId: Int): String {
        return try {
            val launch = LaunchCommand.start(component, mode, userId)
            val pkg = component.substringBefore('/')
            val user = userId.toString()
            if (mode == "COLD") {
                command("am", "force-stop", "--user", user, pkg)
            } else {
                val prep = command("am", "start", "--user", user, "-W", "-n", component)
                if (!prep.contains("Status: ok")) throw CommandFailure(prep)
                command("input", "keyevent", "KEYCODE_HOME")
            }
            Thread.sleep(1500)
            command(*launch.toTypedArray())
        } catch (e: CommandFailure) {
            "BenchError: ${MeasurementFailure.short(e.output)}\nBenchErrorType: COMMAND\n${e.output.take(32000)}"
        } catch (e: Exception) {
            "BenchError: ${MeasurementFailure.short(e.message ?: e.javaClass.simpleName)}\nBenchErrorType: COMMAND\n${e.stackTraceToString().take(8000)}"
        }
    }
    override fun destroy() { kotlin.system.exitProcess(0) }
}
