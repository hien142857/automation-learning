package vn.startupbench

import org.json.JSONObject

/** Collects Android timing without making Accessibility/ad/content readiness a prerequisite. */
object StartupSample {
    fun run(remote: IMeasureService, component: String, mode: String, userId: Int): GuidedSample {
        if (mode != "COLD") {
            val opening = remote.launchOnly(component, "HOT", userId)
            // Preparation may itself be cold; only the measured launch must match the requested mode.
            if (!opening.lineSequence().any { it.trim() == "Status: ok" } || opening.contains("BenchError:"))
                return GuidedSample(opening, JSONObject().put("status", "preparation_failed"), "next")
        }
        val prepared = remote.prepareLaunch(component, mode, userId)
        if (prepared != "Prepared: ok")
            return GuidedSample(prepared, JSONObject().put("status", "preparation_failed"), "next")
        val raw = remote.launchOnly(component, mode, userId)
        return GuidedSample(raw, JSONObject().put("status", "android_only")
            .put("source", "activity-manager-am-start-W").put("uiObserved", false), "next")
    }
}
