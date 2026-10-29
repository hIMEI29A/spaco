package com.spaco.app.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.db.SpaCoDatabase
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.scheduling.SchedulingParameters
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {

    private lateinit var context: Context
    private lateinit var database: SpaCoDatabase
    private lateinit var clock: Clock
    private lateinit var ids: IdGenerator
    private lateinit var databaseName: String

    private lateinit var skills: SkillRepositoryImpl
    private lateinit var decks: DeckRepositoryImpl
    private lateinit var items: ItemRepositoryImpl
    private lateinit var reviews: ReviewRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl

    private val parameters = SchedulingParameters()
    private val fixedNow = Instant.parse("2026-02-01T10:00:00Z")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
        ids = SequentialIdGenerator()
        databaseName = "spaco-test-${System.nanoTime()}.db"
        database = openDatabase()
        wire()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): SpaCoDatabase =
        Room.databaseBuilder(context, SpaCoDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

    private fun wire() {
        skills = SkillRepositoryImpl(database.skillDao(), clock, ids)
        decks = DeckRepositoryImpl(database.deckDao(), clock, ids)
        items = ItemRepositoryImpl(database.itemDao(), clock, ids)
        reviews = ReviewRepositoryImpl(
            database = database,
            reviewDao = database.reviewDao(),
            itemDao = database.itemDao(),
            parameters = parameters,
            ids = ids,
        )
        sessions = SessionRepositoryImpl(database.sessionDao())
    }

    @Test
    fun `a deck can be created, renamed, archived and restored`() = runTest {
        val skill = skills.create("Math")
        val deck = decks.create(skill.id, "Algebra")

        assertThat(decks.getById(deck.id)?.name).isEqualTo("Algebra")
        assertThat(decks.getById(deck.id)?.isArchived).isFalse()

        decks.rename(deck.id, "Linear Algebra")
        assertThat(decks.getById(deck.id)?.name).isEqualTo("Linear Algebra")

        decks.archive(deck.id)
        assertThat(decks.getById(deck.id)?.isArchived).isTrue()
        assertThat(decks.observeActive().first().map { it.id }).doesNotContain(deck.id)
        assertThat(decks.observeAll().first().map { it.id }).contains(deck.id)

        decks.restore(deck.id)
        assertThat(decks.getById(deck.id)?.isArchived).isFalse()
        assertThat(decks.observeActive().first().map { it.id }).contains(deck.id)
    }

    @Test
    fun `editing an item preserves its review history and schedule`() = runTest {
        val skill = skills.create("Math")
        val deck = decks.create(skill.id, "Algebra")
        val item = items.add(deck.id, prompt = "2+2", expectedAnswer = "4")

        reviews.recordReview(item.id, correct = true, gradedBy = GradedBy.SYSTEM, answeredAt = fixedNow)
        val before = items.getById(item.id)!!
        val historyBefore = reviews.getForItem(item.id)

        items.update(item.id, prompt = "2+3", expectedAnswer = "5", explanation = "basic")

        val after = items.getById(item.id)!!
        assertThat(after.prompt).isEqualTo("2+3")
        assertThat(after.expectedAnswer).isEqualTo("5")
        assertThat(after.explanation).isEqualTo("basic")
        // Content changed, but what the learner knows did not.
        assertThat(after.schedule).isEqualTo(before.schedule)
        assertThat(reviews.getForItem(item.id)).isEqualTo(historyBefore)
    }

    @Test
    fun `recording a review appends history and refreshes the cached schedule`() = runTest {
        val skill = skills.create("Math")
        val deck = decks.create(skill.id, "Algebra")
        val item = items.add(deck.id, prompt = "2+2", expectedAnswer = "4")

        reviews.recordReview(item.id, correct = true, gradedBy = GradedBy.SYSTEM, answeredAt = fixedNow)

        val stored = items.getById(item.id)!!
        assertThat(stored.schedule.state).isEqualTo(LearningState.LEARNING)
        assertThat(stored.schedule.nextDueAt).isGreaterThan(fixedNow)
        assertThat(reviews.getForItem(item.id)).hasSize(1)
    }

    @Test
    fun `content persists across a database reopen`() = runTest {
        val skill = skills.create("Math")
        val deck = decks.create(skill.id, "Algebra")
        val item = items.add(deck.id, prompt = "2+2", expectedAnswer = "4")
        reviews.recordReview(item.id, correct = true, gradedBy = GradedBy.SYSTEM, answeredAt = fixedNow)

        database.close()

        // Reopen the same on-disk database and rebuild the repositories.
        database = openDatabase()
        wire()

        assertThat(skills.getById(skill.id)?.name).isEqualTo("Math")
        assertThat(decks.getById(deck.id)?.name).isEqualTo("Algebra")
        assertThat(items.getById(item.id)?.prompt).isEqualTo("2+2")
        assertThat(reviews.getForItem(item.id)).hasSize(1)
    }

    @Test
    fun `a session is saved and read back as the active session`() = runTest {
        val skill = skills.create("Math")
        val deck = decks.create(skill.id, "Algebra")
        val item = items.add(deck.id, prompt = "2+2", expectedAnswer = "4")

        val session = com.spaco.app.core.domain.model.SessionState(
            id = "session-1",
            deckId = deck.id,
            startedAt = fixedNow,
            itemBudget = 1,
            plannedItemIds = listOf(item.id),
            answeredItemIds = listOf(item.id),
            correctCount = 1,
        )
        sessions.save(session)

        val active = sessions.getActiveForDeck(deck.id)
        assertThat(active?.plannedItemIds).containsExactly(item.id)
        assertThat(active?.answeredCount).isEqualTo(1)
    }

    @Test
    fun `the stored cache equals a recompute from history after mixed answers`() = runTest {
        val skill = skills.create("Math")
        val deck = decks.create(skill.id, "Algebra")
        val item = items.add(deck.id, prompt = "2+2", expectedAnswer = "4")

        // Interleave correct and incorrect answers across time.
        val grades = listOf(true, true, false, true, false, true, true, true)
        var at = fixedNow
        grades.forEach { correct ->
            reviews.recordReview(item.id, correct = correct, gradedBy = GradedBy.SYSTEM, answeredAt = at)
            at = at.plusSeconds(3_600)
        }

        val stored = items.getById(item.id)!!
        val history = reviews.getForItem(item.id)
        val expected = com.spaco.app.core.domain.scheduling.Scheduler
            .recompute(history, stored, parameters)

        assertThat(stored.schedule).isEqualTo(expected)
        assertThat(history).hasSize(grades.size)
    }

    private class SequentialIdGenerator : IdGenerator {
        private var counter = 0
        override fun newId(): String {
            counter += 1
            return "id-$counter"
        }
    }
}
