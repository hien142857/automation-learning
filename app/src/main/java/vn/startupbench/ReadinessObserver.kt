package vn.startupbench

import org.json.JSONObject

interface ReadinessObserver {
    fun arm(target: ReadyCriterion, message: String)
    fun evidence(launchAt: Long): JSONObject
    fun awaitReady(launchAt: Long, timeoutMs: Long): JSONObject
    fun ask(title: String, message: String, choices: List<String>): Int
    fun allowInteraction(message: String): Boolean
}
