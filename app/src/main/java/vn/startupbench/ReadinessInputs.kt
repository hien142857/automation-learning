package vn.startupbench

import android.content.Context

/** Suppresses saves while switching all fields to another app's stored criterion. */
class ReadinessInputs {
    var loading = false
        private set
    fun load(block: () -> Unit) {
        loading = true
        try { block() } finally { loading = false }
    }
    fun save(context: Context, criterion: ReadyCriterion) {
        if (!loading) ReadyCriterion.save(context, criterion)
    }
}
