package com.spaco.app.core.domain.scheduling

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import java.time.Duration
import java.time.Instant
import org.junit.Test

class SchedulerTest {

    private val parameters = SchedulingParameters()
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    private fun ItemSchedule.intervalFrom(instant: Instant): Duration =
        Duration.between(instant, nextDueAt)

    @Test
    fun `first correct answer schedules in the future and enters learning`() {
        val result = Scheduler.applyReview(ItemSchedule.newItem(t0), parameters, Grade.CORRECT, t0)

        assertThat(result.state).isEqualTo(LearningState.LEARNING)
        assertThat(result.nextDueAt).isGreaterThan(t0)
        assertThat(result.correctStreak).isEqualTo(1)
    }

    @Test
    fun `successive correct answers strictly grow the interval`() {
        var schedule = ItemSchedule.newItem(t0)
        var at = t0
        var previous = Duration.ZERO

        repeat(6) {
            schedule = Scheduler.applyReview(schedule, parameters, Grade.CORRECT, at)
            val interval = schedule.intervalFrom(at)
            assertThat(interval).isGreaterThan(previous)
            previous = interval
            at = schedule.nextDueAt
        }
    }

    @Test
    fun `the interval is capped at the maximum interval`() {
        val capped = parameters.copy(maximumInterval = Duration.ofHours(24))
        var schedule = ItemSchedule.newItem(t0)
        var at = t0
        var lastInterval = Duration.ZERO

        repeat(8) {
            val answeredAt = at
            schedule = Scheduler.applyReview(schedule, capped, Grade.CORRECT, answeredAt)
            lastInterval = schedule.intervalFrom(answeredAt)
            assertThat(lastInterval).isAtMost(capped.maximumInterval)
            at = schedule.nextDueAt
        }

        assertThat(lastInterval).isEqualTo(capped.maximumInterval)
    }

    @Test
    fun `a new item's first interval is capped by the new-item ceiling`() {
        val tight = parameters.copy(
            initialInterval = Duration.ofDays(3),
            newItemIntervalCap = Duration.ofDays(1),
        )

        val result = Scheduler.applyReview(ItemSchedule.newItem(t0), tight, Grade.CORRECT, t0)

        assertThat(result.intervalFrom(t0)).isEqualTo(Duration.ofDays(1))
    }

    @Test
    fun `an item becomes mastered after enough correct answers`() {
        var schedule = ItemSchedule.newItem(t0)
        var at = t0

        repeat(parameters.masteredThreshold) {
            schedule = Scheduler.applyReview(schedule, parameters, Grade.CORRECT, at)
            at = schedule.nextDueAt
        }

        assertThat(schedule.state).isEqualTo(LearningState.MASTERED)
        assertThat(schedule.correctStreak).isEqualTo(parameters.masteredThreshold)
    }

    @Test
    fun `incorrect answer on a reviewing item lapses it and brings it back soon`() {
        val reviewing = ItemSchedule(
            nextDueAt = t0,
            state = LearningState.REVIEWING,
            intervalIndex = 3,
            correctStreak = 3,
        )

        val result = Scheduler.applyReview(reviewing, parameters, Grade.INCORRECT, t0)

        assertThat(result.state).isEqualTo(LearningState.LAPSED)
        assertThat(result.correctStreak).isEqualTo(0)
        assertThat(result.intervalFrom(t0)).isEqualTo(parameters.lapseInterval)
        assertThat(result.intervalFrom(t0)).isAtMost(Duration.ofDays(1))
    }

    @Test
    fun `incorrect answer on a mastered item stops it counting as mastered`() {
        val mastered = ItemSchedule(
            nextDueAt = t0,
            state = LearningState.MASTERED,
            intervalIndex = 6,
            correctStreak = 6,
        )

        val result = Scheduler.applyReview(mastered, parameters, Grade.INCORRECT, t0)

        assertThat(result.state).isEqualTo(LearningState.LAPSED)
        assertThat(result.state).isNotEqualTo(LearningState.MASTERED)
    }

    @Test
    fun `incorrect answer on a new item keeps it due`() {
        val result = Scheduler.applyReview(ItemSchedule.newItem(t0), parameters, Grade.INCORRECT, t0)

        assertThat(result.isDue(t0)).isTrue()
        assertThat(result.state).isEqualTo(LearningState.NEW)
        assertThat(result.correctStreak).isEqualTo(0)
    }

    @Test
    fun `a lapsed item re-enters learning after a correct answer`() {
        val lapsed = ItemSchedule(t0, LearningState.LAPSED, 1, 0)

        val result = Scheduler.applyReview(lapsed, parameters, Grade.CORRECT, t0)

        assertThat(result.state).isEqualTo(LearningState.LEARNING)
        assertThat(result.correctStreak).isEqualTo(1)
        assertThat(result.nextDueAt).isGreaterThan(t0)
    }

    @Test
    fun `the lapse interval never goes below zero`() {
        val barelyStarted = ItemSchedule(t0, LearningState.LEARNING, 0, 0)

        val result = Scheduler.applyReview(barelyStarted, parameters, Grade.INCORRECT, t0)

        assertThat(result.intervalIndex).isEqualTo(0)
    }
}
