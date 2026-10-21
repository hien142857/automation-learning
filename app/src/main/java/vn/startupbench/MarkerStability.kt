package vn.startupbench

/** A previously ready marker must become unready when an ad/overlay covers it. */
class MarkerStability {
    var first = -1L
    var missing = 0L
    var stable = -1L
    fun reset(now: Long) { first = -1; stable = -1; missing = now }
    fun observe(present: Boolean, now: Long, holdMs: Long) {
        if (!present) { reset(now); return }
        if (first < 0) first = now
        if (stable < 0 && now - first >= holdMs) stable = now
    }
}
