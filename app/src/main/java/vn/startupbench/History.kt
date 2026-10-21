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

fun JSONObject.results(): List<LaunchResult> {
    val samples = optJSONArray("samples") ?: JSONArray()
    return (0 until samples.length()).map { i ->
        val sample = samples.getJSONObject(i)
        LaunchResult.parse(sample.getString("mode"), sample.getString("raw"))
    }
}

fun JSONObject.values(mode: String): List<Long> = results().filter { it.mode == mode && it.valid }.mapNotNull { it.totalMs }
