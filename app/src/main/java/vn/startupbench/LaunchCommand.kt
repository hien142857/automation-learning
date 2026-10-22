package vn.startupbench

object LaunchCommand {
    fun start(component: String, mode: String, userId: Int): List<String> {
        require(Regex("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+").matches(component)) { "Component không hợp lệ" }
        require(mode in listOf("COLD", "WARM", "HOT") && userId >= 0)
        require(component.substringBefore('/') !in listOf("vn.startupbench", "moe.shizuku.privileged.api")) {
            "Không đo chính công cụ hoặc Shizuku"
        }
        return buildList {
            addAll(listOf("am", "start", "--user", userId.toString(), "-W"))
            addAll(listOf("-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"))
            // Intent.parseCommandArgs supports -f, but has no --activity-new-task option.
            // FLAG_ACTIVITY_NEW_TASK (0x10000000) | FLAG_ACTIVITY_CLEAR_TASK (0x00008000).
            if (mode == "WARM") addAll(listOf("-f", "0x10008000"))
            addAll(listOf("-n", component))
        }
    }
}
