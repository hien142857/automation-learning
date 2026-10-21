package vn.startupbench

import android.content.Context
import org.json.JSONObject

data class ReadyCriterion(val packageName: String, val resourceId: String, val text: String, val timeoutMs: Long = 60000) {
    val configured get() = resourceId.isNotBlank() || text.isNotBlank()
    fun json() = JSONObject().put("package", packageName).put("resourceId", resourceId).put("text", text).put("timeoutMs", timeoutMs)
    companion object {
        fun load(context: Context, pkg: String): ReadyCriterion {
            val prefs = context.getSharedPreferences("readiness", Context.MODE_PRIVATE)
            return ReadyCriterion(pkg, prefs.getString("$pkg:id", "").orEmpty(), prefs.getString("$pkg:text", "").orEmpty())
        }
        fun save(context: Context, criterion: ReadyCriterion) {
            context.getSharedPreferences("readiness", Context.MODE_PRIVATE).edit()
                .putString("${criterion.packageName}:id", criterion.resourceId).putString("${criterion.packageName}:text", criterion.text).apply()
        }
    }
}

object EvidencePolicy {
    fun accepted(evidence: JSONObject?): Boolean = checked(evidence, false)
    fun assisted(evidence: JSONObject?): Boolean = checked(evidence, true)
    fun launchAccepted(evidence: JSONObject?, raw: String): Boolean = accepted(evidence) && launchClock(raw) == evidence!!.optLong("launchUptimeMs", -1)
    fun launchAssisted(evidence: JSONObject?, raw: String): Boolean = assisted(evidence) && launchClock(raw) == evidence!!.optLong("launchUptimeMs", -1)
    private fun checked(evidence: JSONObject?, assisted: Boolean): Boolean = evidence != null &&
        evidence.optString("status") == (if (assisted) "assisted_ui" else "verified_ui") && evidence.optBoolean("testerConfirmed") &&
        evidence.optLong("launchUptimeMs", -1) >= 0 &&
        evidence.optLong("firstMatchUptimeMs", -1) >= evidence.optLong("launchUptimeMs") &&
        evidence.optLong("confirmedStableUptimeMs", -1) - evidence.optLong("firstMatchUptimeMs") >= 500 &&
        evidence.optLong("lastMissingUptimeMs", -1) >= 0 && evidence.optLong("lastMissingUptimeMs") <= evidence.optLong("firstMatchUptimeMs") &&
        evidence.optString("foregroundPackage") == evidence.optString("targetPackage") &&
        evidence.optString("targetPackage").isNotBlank() && evidence.optBoolean("assisted") == assisted && !evidence.optBoolean("interrupted") &&
        (assisted || (!evidence.optBoolean("recheckedAfterTesterAction") && (evidence.optJSONArray("rejectedCandidates")?.length() ?: 0) == 0)) &&
        evidence.optJSONObject("criterion")?.let { it.optString("package") == evidence.optString("targetPackage") &&
            (it.optString("resourceId").isNotBlank() || it.optString("text").isNotBlank()) } == true

    fun launchClock(raw: String): Long? = Regex("(?m)^BenchLaunchUptimeMs: ([0-9]+)$").find(raw)?.groupValues?.get(1)?.toLongOrNull()
}
