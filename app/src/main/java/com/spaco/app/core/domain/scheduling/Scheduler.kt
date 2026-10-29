package com.spaco.app.core.domain.scheduling

import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import java.time.Duration
import java.time.Instant

/**
 * The scheduling core, expressed as pure functions.
 *
 * Nothing here reads a clock, a database, or a framework: `answeredAt` and the
 * reference `now` are always explicit inputs. That is what lets the derived
 * cache on an item be recomputed from review history and asserted equal to it.
 */
object Scheduler {

    /** The schedule of an item that has never been reviewed. */
    fun newItemSchedule(createdAt: Instant): ItemSchedule = ItemSchedule.newItem(createdAt)

    /**
     * Applies one graded answer to an item's schedule.
     *
     * A correct answer always pushes the item further into the future, by a
     * strictly growing interval, up to the applicable ceiling. An incorrect
     * answer lowers the item's state and brings it back quickly: a brand-new
     * item stays due, and a known item lapses and returns after
     * [SchedulingParameters.lapseInterval].
     */
    fun applyReview(
        schedule: ItemSchedule,
        parameters: SchedulingParameters,
        grade: Grade,
        answeredAt: Instant,
    ): ItemSchedule = when (grade) {
        Grade.CORRECT -> onCorrect(schedule, parameters, answeredAt)
        Grade.INCORRECT -> onIncorrect(schedule, parameters, answeredAt)
    }

    /**
     * Rebuilds an item's schedule from its review history alone.
     *
     * This is the definition of the derived cache: the stored schedule must
     * always equal this result. Events are ordered by answer time with the event
     * id as a stable tiebreaker so the rebuild is deterministic.
     */
    fun recompute(
        history: List<ReviewEvent>,
        item: PracticeItem,
        parameters: SchedulingParameters,
    ): ItemSchedule {
        val ordered = history
            .filter { it.itemId == item.id }
            .sortedWith(compareBy({ it.answeredAt }, { it.id }))

        return ordered.fold(ItemSchedule.newItem(item.createdAt)) { schedule, event ->
            applyReview(schedule, parameters, event.grade, event.answeredAt)
        }
    }

    /**
     * The items a session may present, most overdue first, with a stable
     * tiebreaker so equal due times keep the same order on every read.
     */
    fun dueOrder(items: List<PracticeItem>, now: Instant): List<PracticeItem> =
        items
            .filter { !it.isArchived && it.isDue(now) }
            .sortedWith(compareBy({ it.schedule.nextDueAt }, { it.id }))

    /** The moment the next item becomes due, or `null` when nothing is pending. */
    fun earliestDueAt(items: List<PracticeItem>): Instant? =
        items.filterNot { it.isArchived }
            .minOfOrNull { it.schedule.nextDueAt }

    private fun onCorrect(
        schedule: ItemSchedule,
        parameters: SchedulingParameters,
        answeredAt: Instant,
    ): ItemSchedule {
        val intervalIndex = schedule.intervalIndex + 1
        val correctStreak = schedule.correctStreak + 1
        val state = when {
            correctStreak >= parameters.masteredThreshold -> LearningState.MASTERED
            intervalIndex >= parameters.reviewingThreshold -> LearningState.REVIEWING
            else -> LearningState.LEARNING
        }
        val ceiling = if (state == LearningState.LEARNING) {
            parameters.newItemIntervalCap
        } else {
            parameters.maximumInterval
        }

        return ItemSchedule(
            nextDueAt = answeredAt.plus(intervalFor(parameters, intervalIndex, ceiling)),
            state = state,
            intervalIndex = intervalIndex,
            correctStreak = correctStreak,
        )
    }

    private fun onIncorrect(
        schedule: ItemSchedule,
        parameters: SchedulingParameters,
        answeredAt: Instant,
    ): ItemSchedule {
        // A brand-new item has nothing to lapse from: keep it due so it comes
        // straight back instead of being pushed to a long interval.
        if (schedule.state == LearningState.NEW) {
            return ItemSchedule(
                nextDueAt = answeredAt,
                state = LearningState.NEW,
                intervalIndex = 0,
                correctStreak = 0,
            )
        }

        return ItemSchedule(
            nextDueAt = answeredAt.plus(parameters.lapseInterval),
            state = LearningState.LAPSED,
            intervalIndex = (schedule.intervalIndex - 1).coerceAtLeast(0),
            correctStreak = 0,
        )
    }

    /**
     * `initialInterval * growthFactor^(intervalIndex - 1)`, clamped to
     * [ceiling] without ever overflowing on large indices.
     */
    private fun intervalFor(
        parameters: SchedulingParameters,
        intervalIndex: Int,
        ceiling: Duration,
    ): Duration {
        val ceilingMillis = ceiling.toMillis().toDouble()
        var millis = parameters.initialInterval.toMillis().toDouble()

        var step = 1
        while (step < intervalIndex) {
            millis *= parameters.growthFactor
            if (millis >= ceilingMillis) return ceiling
            step++
        }

        return Duration.ofMillis(millis.coerceAtMost(ceilingMillis).toLong())
    }
}
