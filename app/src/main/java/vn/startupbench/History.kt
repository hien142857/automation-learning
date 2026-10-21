package vn.startupbench

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class History(context: Context) : SQLiteOpenHelper(context, "history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, data TEXT NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun save(session: JSONObject) {
        val row = writableDatabase.insertWithOnConflict("sessions", null, ContentValues().apply {
            put("id", session.getString("id")); put("data", session.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        check(row != -1L) { "Không lưu được lịch sử trên thiết bị" }
    }
    @Synchronized fun all(): List<JSONObject> = readableDatabase.rawQuery("SELECT data FROM sessions ORDER BY id DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(JSONObject(c.getString(0))) }
    }
}

fun JSONObject.androidResults(): List<LaunchResult> {
    val samples = optJSONArray("samples") ?: JSONArray()
    return (0 until samples.length()).map { i ->
        val sample = samples.getJSONObject(i)
        LaunchResult.parse(sample.getString("mode"), sample.getString("raw"))
    }
}

fun JSONObject.results(): List<LaunchResult> {
    val samples = optJSONArray("samples") ?: JSONArray()
    return androidResults().mapIndexed { index, result ->
        val proof = samples.getJSONObject(index).optJSONObject("evidence")
        if (!result.stateVerified) result
        else if (EvidencePolicy.launchAccepted(proof, result.raw)) result.copy(error = null, uiVerified = true)
        else result.copy(error = when (proof?.optString("status")) {
            "assisted_ui" -> "Có ads/thao tác: lưu riêng, không dùng trong nhóm tự động."
            "timeout" -> proof.optString("failureReason", ObservationFailure.explain(proof))
            "rejected_by_tester" -> "Tester từ chối xác nhận màn hình đích."
            else -> proof?.optString("failureReason")?.takeIf { it.isNotBlank() } ?: "Chưa có bằng chứng mốc UI và xác nhận hợp lệ."
        })
    }
}

fun JSONObject.readyValues(mode: String): List<Long> {
    val samples = optJSONArray("samples") ?: JSONArray()
    return androidResults().mapIndexedNotNull { index, result ->
        val proof = samples.getJSONObject(index).optJSONObject("evidence")
        if (result.mode == mode && result.stateVerified && EvidencePolicy.launchAccepted(proof, result.raw))
            proof!!.getLong("firstMatchUptimeMs") - proof.getLong("launchUptimeMs") else null
    }
}

fun JSONObject.assistedValues(mode: String): List<Long> {
    val samples = optJSONArray("samples") ?: JSONArray()
    return androidResults().mapIndexedNotNull { index, result ->
        val proof = samples.getJSONObject(index).optJSONObject("evidence")
        if (result.mode == mode && result.stateVerified && EvidencePolicy.launchAssisted(proof, result.raw))
            proof!!.getLong("firstMatchUptimeMs") - proof.getLong("launchUptimeMs") else null
    }
}

fun JSONObject.cohortValues(mode: String, cohort: MeasurementCohort): List<Long> =
    if (cohort == MeasurementCohort.AUTOMATIC) readyValues(mode) else assistedValues(mode)

fun JSONObject.cohortAndroidValues(mode: String, cohort: MeasurementCohort): List<Long> {
    val samples = optJSONArray("samples") ?: JSONArray()
    return androidResults().mapIndexedNotNull { index, result ->
        val proof = samples.getJSONObject(index).optJSONObject("evidence")
        val accepted = if (cohort == MeasurementCohort.AUTOMATIC) EvidencePolicy.launchAccepted(proof, result.raw) else EvidencePolicy.launchAssisted(proof, result.raw)
        result.totalMs?.takeIf { result.mode == mode && result.stateVerified && accepted }
    }
}

fun JSONObject.observationIntervals(mode: String, cohort: MeasurementCohort = MeasurementCohort.AUTOMATIC): List<Long> {
    val samples = optJSONArray("samples") ?: JSONArray()
    return androidResults().mapIndexedNotNull { index, result ->
        val proof = samples.getJSONObject(index).optJSONObject("evidence")
        val accepted = if (cohort == MeasurementCohort.AUTOMATIC) EvidencePolicy.launchAccepted(proof, result.raw) else EvidencePolicy.launchAssisted(proof, result.raw)
        if (result.mode == mode && result.stateVerified && accepted)
            proof!!.getLong("firstMatchUptimeMs") - maxOf(proof.getLong("launchUptimeMs"), proof.getLong("lastMissingUptimeMs")) else null
    }
}

fun JSONObject.values(mode: String): List<Long> = results().filter { it.mode == mode && it.valid }.mapNotNull { it.totalMs }

fun JSONObject.discoveredSignatures(): Set<String> {
    val samples = optJSONArray("samples") ?: return emptySet()
    return (0 until samples.length()).mapNotNull { i ->
        val sample = samples.getJSONObject(i)
        val e = sample.optJSONObject("evidence") ?: return@mapNotNull null
        if (EvidencePolicy.launchAccepted(e, sample.optString("raw")) || EvidencePolicy.launchAssisted(e, sample.optString("raw")))
            e.optJSONObject("autoScreen")?.optString("signature")?.takeIf { it.isNotBlank() } else null
    }.toSet()
}
