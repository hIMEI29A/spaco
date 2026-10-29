package com.spaco.app.core.domain.scheduling

import java.time.Duration

/**
 * Every tunable of the scheduling algorithm in one place. The values are inputs,
 * never ambient state, which is what keeps scheduling reproducible: the same
 * history and the same parameters always produce the same schedule.
 *
 * These defaults are deliberately conservative starting points and are expected
 * to be tuned from real usage.
 */
data class SchedulingParameters(
    /** Interval after the very first correct answer. */
    val initialInterval: Duration = Duration.ofHours(4),
    /** How much each successive correct answer multiplies the interval by. */
    val growthFactor: Double = 2.0,
    /** Hard ceiling for an item that is already in review. */
    val maximumInterval: Duration = Duration.ofDays(60),
    /** Tighter ceiling for an item that has not reached reviewing yet. */
    val newItemIntervalCap: Duration = Duration.ofHours(24),
    /** How soon a lapsed item comes back. Kept short so it reappears soon. */
    val lapseInterval: Duration = Duration.ofMinutes(10),
    /** Successes needed before an item counts as being in review. */
    val reviewingThreshold: Int = 3,
    /** Successes needed before an item counts as mastered. */
    val masteredThreshold: Int = 5,
)
