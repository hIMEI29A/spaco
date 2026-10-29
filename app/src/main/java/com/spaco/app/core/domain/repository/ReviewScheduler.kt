package com.spaco.app.core.domain.repository

import java.time.Instant

/**
 * Port for registering the background checks that remind the learner about due
 * reviews. A port rather than a concrete class so session logic does not depend
 * on WorkManager.
 */
interface ReviewScheduler {

    fun schedulePeriodic()

    fun scheduleNextDueCheck(earliestDueAt: Instant?)

    fun cancelAll()
}
