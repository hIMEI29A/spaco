package com.spaco.app.core.data.work

/** Unique work names, so scheduling is idempotent and testable. */
object WorkNames {
    const val PERIODIC_DUE_SWEEP = "spaco-due-review-periodic"
    const val NEXT_DUE_CHECK = "spaco-due-review-next"
}
