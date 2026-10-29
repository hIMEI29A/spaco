package com.spaco.app.core.domain.scheduling

import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import java.time.Duration
import java.time.Instant
import org.junit.Test

class RecomputeAndOrderTest {

    private val parameters = SchedulingParameters()
    private val t0 = Instant.parse("2026-01-01T09:00:00Z")

    private fun item(id: String = "item-1", dueAt: Instant = t0, createdAt: Instant = t0) = PracticeItem(
        id = id,
        deckId = "deck-1",
        prompt = "prompt-$id",
        expectedAnswer = "answer-$id",
        createdAt = createdAt,
        schedule = Scheduler.newItemSchedule(createdAt).copy(nextDueAt = dueAt),
    )

    private fun event(index: Int, answeredAt: Instant, correct: Boolean, itemId: String = "item-1") =
        ReviewEvent(
            id = "event-$index",
            itemId = itemId,
            answeredAt = answeredAt,
            correct = correct,
            gradedBy = GradedBy.SYSTEM,
        )

    @Test
    fun `recompute does not depend on the order events are supplied in`() {
        val target = item()
        val history = listOf(
            event(1, t0, correct = true),
            event(2, t0.plusSeconds(3_600), correct = true),
            event(3, t0.plusSeconds(7_200), correct = false),
            event(4, t0.plusSeconds(10_800), correct = true),
        )

        val forward = Scheduler.recompute(history, target, parameters)
        val reversed = Scheduler.recompute(history.reversed(), target, parameters)

        assertThat(reversed).isEqualTo(forward)
    }

    @Test
    fun `recompute ignores review events belonging to other items`() {
        val target = item()
        val history = listOf(
            event(1, t0, correct = true),
            event(2, t0.plusSeconds(60), correct = false, itemId = "someone-else"),
        )

        val withOtherItems = Scheduler.recompute(history, target, parameters)
        val withoutOtherItems = Scheduler.recompute(history.take(1), target, parameters)

        assertThat(withOtherItems).isEqualTo(withoutOtherItems)
    }

    @Test
    fun `an item with no history is due immediately`() {
        val schedule = Scheduler.recompute(emptyList(), item(), parameters)

        assertThat(schedule.state).isEqualTo(LearningState.NEW)
        assertThat(schedule.isDue(t0)).isTrue()
    }

    @Test
    fun `the stored cache equals a recompute from the same history`() {
        val target = item()
        val grades = listOf(true, false, true, true, false, true, true)
        val events = mutableListOf<ReviewEvent>()

        // Build the cache the way the app does: fold each graded answer in.
        var cache = Scheduler.newItemSchedule(target.createdAt)
        var at = t0
        grades.forEachIndexed { index, correct ->
            val event = event(index, at, correct)
            events += event
            cache = Scheduler.applyReview(cache, parameters, event.grade, event.answeredAt)
            at = at.plusSeconds(600)
        }
        val storedCache = cache

        assertThat(Scheduler.recompute(events, target, parameters)).isEqualTo(storedCache)
    }

    @Test
    fun `recomputing without new reviews changes nothing`() {
        val target = item()
        val history = listOf(event(1, t0, correct = true), event(2, t0.plusSeconds(60), correct = true))

        val first = Scheduler.recompute(history, target, parameters)
        val second = Scheduler.recompute(history, target, parameters)

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `the most overdue item comes first`() {
        val now = t0.plusSeconds(10_000)
        val items = listOf(
            item(id = "recent", dueAt = t0.plusSeconds(9_000)),
            item(id = "oldest", dueAt = t0),
            item(id = "middle", dueAt = t0.plusSeconds(5_000)),
        )

        val order = Scheduler.dueOrder(items, now).map { it.id }

        assertThat(order).containsExactly("oldest", "middle", "recent").inOrder()
    }

    @Test
    fun `items due in the future are excluded`() {
        val now = t0
        val items = listOf(
            item(id = "due", dueAt = t0.minusSeconds(1)),
            item(id = "future", dueAt = t0.plus(Duration.ofHours(1))),
        )

        val order = Scheduler.dueOrder(items, now).map { it.id }

        assertThat(order).containsExactly("due")
    }

    @Test
    fun `equal due times keep a stable order regardless of input order`() {
        val items = listOf(
            item(id = "c", dueAt = t0),
            item(id = "a", dueAt = t0),
            item(id = "b", dueAt = t0),
        )

        val expected = listOf("a", "b", "c")
        assertThat(Scheduler.dueOrder(items, t0).map { it.id }).isEqualTo(expected)
        assertThat(Scheduler.dueOrder(items.reversed(), t0).map { it.id }).isEqualTo(expected)
    }

    @Test
    fun `archived items are never offered for practice`() {
        val archived = item(id = "archived", dueAt = t0).copy(archivedAt = t0)

        assertThat(Scheduler.dueOrder(listOf(archived), t0)).isEmpty()
        assertThat(Scheduler.earliestDueAt(listOf(archived))).isNull()
    }

    @Test
    fun `earliest due time is reported for pending work`() {
        val items = listOf(
            item(id = "later", dueAt = t0.plusSeconds(60)),
            item(id = "sooner", dueAt = t0.plusSeconds(30)),
        )

        assertThat(Scheduler.earliestDueAt(items)).isEqualTo(t0.plusSeconds(30))
    }
}
