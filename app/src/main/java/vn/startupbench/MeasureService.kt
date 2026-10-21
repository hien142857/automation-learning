package vn.startupbench

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs as shell inside a Shizuku UserService, never as an exported Android service. */
class MeasureService : IMeasureService.Stub() {
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
            check(process.exitValue() == 0) { text.take(1500) }
            return text
        } finally { reader.shutdownNow() }
    }

    @Synchronized override fun measure(component: String, mode: String, userId: Int): String {
        return try {
            require(Regex("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+").matches(component)) { "Component không hợp lệ" }
            require(mode in listOf("COLD", "WARM", "HOT") && userId >= 0)
            val pkg = component.substringBefore('/')
            require(pkg != "vn.startupbench" && pkg != "moe.shizuku.privileged.api") { "Không đo chính công cụ hoặc Shizuku" }
            val user = userId.toString()
            if (mode == "COLD") {
                command("am", "force-stop", "--user", user, pkg)
            } else {
                val prep = command("am", "start", "--user", user, "-W", "-n", component)
                check(prep.contains("Status: ok")) { "Không mở được ứng dụng để chuẩn bị: $prep" }
                command("input", "keyevent", "KEYCODE_HOME")
            }
            Thread.sleep(1500)
            val args = mutableListOf("am", "start", "--user", user, "-W")
            if (mode == "WARM") args.addAll(listOf("--activity-clear-task", "--activity-new-task"))
            args.addAll(listOf("-n", component))
            command(*args.toTypedArray())
        } catch (e: Exception) { "BenchError: ${e.message?.replace('\n', ' ') ?: e.javaClass.simpleName}" }
    }
    override fun destroy() { kotlin.system.exitProcess(0) }
}
