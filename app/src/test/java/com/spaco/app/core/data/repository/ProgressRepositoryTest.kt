package com.spaco.app.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.db.SpaCoDatabase
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.scheduling.Scheduler
import com.spaco.app.core.domain.scheduling.SchedulingParameters
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: SpaCoDatabase
    private lateinit var items: ItemRepositoryImpl
    private lateinit var reviews: ReviewRepositoryImpl
    private lateinit var decks: DeckRepositoryImpl
    private lateinit var skills: SkillRepositoryImpl
    private lateinit var progress: ProgressRepositoryImpl

    private val parameters = SchedulingParameters()
    private val now = Instant.parse("2026-06-10T12:00:00Z")
    private var counter = 0

    private val ids = object : IdGenerator {
        override fun newId(): String {
            counter += 1
            return "id-$counter"
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SpaCoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        skills = SkillRepositoryImpl(database.skillDao(), clock, ids)
        decks = DeckRepositoryImpl(database.deckDao(), clock, ids)
        items = ItemRepositoryImpl(database.itemDao(), clock, ids)
        reviews = ReviewRepositoryImpl(database, database.reviewDao(), database.itemDao(), parameters, ids)
        progress = ProgressRepositoryImpl(items, reviews, decks, ZoneOffset.UTC)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun deck(): String {
        val skill = skills.create("Skill")
        return decks.create(skill.id, "Deck").id
    }

    private suspend fun item(deckId: String, answer: String = "a") =
        items.add(deckId, prompt = "prompt-$answer", expectedAnswer = answer)

    private suspend fun review(itemId: String, correct: Boolean, at: Instant) =
        reviews.recordReview(itemId, correct, GradedBy.SYSTEM, at)

    @Test
    fun `due count matches the scheduler's due set`() = runTest {
        val deckId = deck()
        val a = item(deckId)
        val b = item(deckId)
        item(deckId)
        // Push one item into the future; two remain due.
        database.itemDao().updateSchedule(
            id = b.id,
            nextDueAt = now.plusSeconds(86_400).toEpochMilli(),
            learningState = LearningState.LEARNING,
            intervalIndex = 1,
            correctStreak = 1,
        )

        val expectedDue = Scheduler.dueOrder(items.getForDeck(deckId), now).size
        val deckProgress = progress.observeDeckProgress(deckId, now).first()!!

        assertThat(deckProgress.dueCount).isEqualTo(expectedDue)
        assertThat(deckProgress.dueCount).isEqualTo(2)
        assertThat(deckProgress.totalItems).isEqualTo(3)
        assertThat(a.id).isNotEmpty()
    }

    @Test
    fun `state counts reflect the scheduling state of each item`() = runTest {
        val deckId = deck()
        val a = item(deckId)
        val b = item(deckId)
        review(a.id, correct = true, at = now)
        database.itemDao().updateSchedule(
            id = b.id,
            nextDueAt = now.toEpochMilli(),
            learningState = LearningState.LAPSED,
            intervalIndex = 0,
            correctStreak = 0,
        )

        val deckProgress = progress.observeDeckProgress(deckId, now).first()!!

        assertThat(deckProgress.countIn(LearningState.LEARNING)).isEqualTo(1)
        assertThat(deckProgress.countIn(LearningState.LAPSED)).isEqualTo(1)
    }

    @Test
    fun `accuracy is computed over the recent window`() = runTest {
        val deckId = deck()
        val a = item(deckId)
        review(a.id, correct = true, at = now.minusSeconds(3_600))
        review(a.id, correct = true, at = now.minusSeconds(7_200))
        review(a.id, correct = false, at = now.minusSeconds(10_800))
        // Outside the 7-day window: must be ignored.
        review(a.id, correct = true, at = now.minusSeconds(20 * 86_400))

        val accuracy = progress.observeDeckProgress(deckId, now).first()!!.accuracy

        assertThat(accuracy.total).isEqualTo(3)
        assertThat(accuracy.correct).isEqualTo(2)
        assertThat(accuracy.percent).isEqualTo(67)
        assertThat(accuracy.windowDays).isEqualTo(7)
        assertThat(accuracy.isLimited).isTrue()
    }

    @Test
    fun `accuracy is unavailable with no reviews`() = runTest {
        val deckId = deck()
        item(deckId)

        val accuracy = progress.observeDeckProgress(deckId, now).first()!!.accuracy

        assertThat(accuracy.hasHistory).isFalse()
        assertThat(accuracy.percent).isNull()
    }

    @Test
    fun `the streak counts consecutive practice days`() = runTest {
        val deckId = deck()
        val a = item(deckId)
        review(a.id, correct = true, at = Instant.parse("2026-06-08T09:00:00Z"))
        review(a.id, correct = true, at = Instant.parse("2026-06-09T09:00:00Z"))
        review(a.id, correct = true, at = Instant.parse("2026-06-10T09:00:00Z"))

        val streak = progress.observeDeckProgress(deckId, now).first()!!.streakDays

        assertThat(streak).isEqualTo(3)
    }

    @Test
    fun `a gap resets the streak`() = runTest {
        val deckId = deck()
        val a = item(deckId)
        review(a.id, correct = true, at = Instant.parse("2026-06-05T09:00:00Z"))
        // Gap on 06-06..06-07, then practice again.
        review(a.id, correct = true, at = Instant.parse("2026-06-08T09:00:00Z"))
        review(a.id, correct = true, at = Instant.parse("2026-06-09T09:00:00Z"))
        review(a.id, correct = true, at = Instant.parse("2026-06-10T09:00:00Z"))

        val streak = progress.observeDeckProgress(deckId, now).first()!!.streakDays

        assertThat(streak).isEqualTo(3)
    }

    @Test
    fun `archived decks are excluded from the overall totals`() = runTest {
        val activeDeck = deck()
        val archivedDeck = deck()
        item(activeDeck)
        item(archivedDeck)
        decks.archive(archivedDeck)

        val overall = progress.observeOverallProgress(now).first()

        assertThat(overall.totalItems).isEqualTo(1)
        assertThat(overall.dueCount).isEqualTo(1)
    }

    @Test
    fun `day bucketing stays correct across a daylight-saving transition`() = runTest {
        val newYork = ZoneId.of("America/New_York")
        val dstProgress = ProgressRepositoryImpl(items, reviews, decks, newYork)
        val deckId = deck()
        val a = item(deckId)

        // US DST springs forward on 2026-03-08. Practice on 07, 08 and 09 local.
        review(a.id, correct = true, at = Instant.parse("2026-03-07T18:00:00Z"))
        review(a.id, correct = true, at = Instant.parse("2026-03-08T18:00:00Z"))
        review(a.id, correct = true, at = Instant.parse("2026-03-09T18:00:00Z"))

        val afterTransition = Instant.parse("2026-03-09T20:00:00Z")
        val streak = dstProgress.observeDeckProgress(deckId, afterTransition).first()!!.streakDays

        assertThat(streak).isEqualTo(3)
    }

    @Test
    fun `an unknown deck yields no progress`() = runTest {
        assertThat(progress.observeDeckProgress("missing", now).first()).isNull()
    }
}
