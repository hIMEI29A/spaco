package com.spaco.app.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private lateinit var database: SpaCoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpaCoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun skill(id: String = "skill-1") = SkillEntity(
        id = id,
        name = "Skill $id",
        createdAt = 1_000L,
    )

    private fun deck(id: String = "deck-1", skillId: String = "skill-1") = DeckEntity(
        id = id,
        skillId = skillId,
        name = "Deck $id",
        createdAt = 2_000L,
    )

    private fun item(
        id: String,
        deckId: String = "deck-1",
        nextDueAt: Long,
        archivedAt: Long? = null,
    ) = PracticeItemEntity(
        id = id,
        deckId = deckId,
        prompt = "prompt-$id",
        expectedAnswer = "answer-$id",
        createdAt = 3_000L,
        archivedAt = archivedAt,
        nextDueAt = nextDueAt,
        learningState = LearningState.NEW,
        intervalIndex = 0,
        correctStreak = 0,
    )

    private fun event(
        id: String,
        itemId: String = "item-1",
        answeredAt: Long,
    ) = ReviewEventEntity(
        id = id,
        itemId = itemId,
        answeredAt = answeredAt,
        correct = true,
        gradedBy = GradedBy.SYSTEM,
    )

    @Test
    fun `inserted skill, deck and item can be read back`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("item-1", nextDueAt = 5_000L))

        assertThat(database.skillDao().getById("skill-1")?.name).isEqualTo("Skill skill-1")
        assertThat(database.deckDao().getById("deck-1")?.skillId).isEqualTo("skill-1")
        val stored = database.itemDao().getById("item-1")
        assertThat(stored?.prompt).isEqualTo("prompt-item-1")
        assertThat(stored?.nextDueAt).isEqualTo(5_000L)
    }

    @Test
    fun `review history is append-only and retains every event`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("item-1", nextDueAt = 5_000L))

        database.reviewDao().insert(event("e2", answeredAt = 300L))
        database.reviewDao().insert(event("e1", answeredAt = 100L))
        database.reviewDao().insert(event("e3", answeredAt = 200L))

        val history = database.reviewDao().getForItem("item-1")
        assertThat(history.map { it.id }).containsExactly("e1", "e3", "e2").inOrder()
        assertThat(database.reviewDao().count()).isEqualTo(3)
    }

    @Test
    fun `due items are returned most overdue first with a stable id tiebreaker`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("recent", nextDueAt = 9_000L))
        database.itemDao().insert(item("oldest", nextDueAt = 1_000L))
        database.itemDao().insert(item("tie-b", nextDueAt = 5_000L))
        database.itemDao().insert(item("tie-a", nextDueAt = 5_000L))

        val due = database.itemDao().getDueItems("deck-1", now = 10_000L)

        assertThat(due.map { it.id })
            .containsExactly("oldest", "tie-a", "tie-b", "recent")
            .inOrder()
    }

    @Test
    fun `items due in the future are excluded from the due set`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("due", nextDueAt = 1_000L))
        database.itemDao().insert(item("future", nextDueAt = 20_000L))

        val due = database.itemDao().getDueItems("deck-1", now = 10_000L)

        assertThat(due.map { it.id }).containsExactly("due")
    }

    @Test
    fun `archived items are excluded and reactivated on restore`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("item-1", nextDueAt = 1_000L, archivedAt = 500L))

        assertThat(database.itemDao().getDueItems("deck-1", now = 10_000L)).isEmpty()

        database.itemDao().setArchivedAt("item-1", null)
        assertThat(database.itemDao().getDueItems("deck-1", now = 10_000L).map { it.id })
            .containsExactly("item-1")
    }

    @Test
    fun `observing due items emits the deterministic order`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("later", nextDueAt = 8_000L))
        database.itemDao().insert(item("sooner", nextDueAt = 2_000L))

        val first = database.itemDao().observeDueItems("deck-1", now = 10_000L).first()

        assertThat(first.map { it.id }).containsExactly("sooner", "later").inOrder()
    }

    @Test
    fun `updating the derived cache does not touch review history`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("item-1", nextDueAt = 1_000L))
        database.reviewDao().insert(event("e1", answeredAt = 100L))

        database.itemDao().updateSchedule(
            id = "item-1",
            nextDueAt = 50_000L,
            learningState = LearningState.REVIEWING,
            intervalIndex = 3,
            correctStreak = 3,
        )

        val stored = database.itemDao().getById("item-1")!!
        assertThat(stored.nextDueAt).isEqualTo(50_000L)
        assertThat(stored.learningState).isEqualTo(LearningState.REVIEWING)
        assertThat(database.reviewDao().count()).isEqualTo(1)
    }

    @Test
    fun `the earliest next due time ignores archived items`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        database.itemDao().insert(item("archived", nextDueAt = 100L, archivedAt = 50L))
        database.itemDao().insert(item("active", nextDueAt = 1_000L))

        assertThat(database.itemDao().getEarliestNextDueAt()).isEqualTo(1_000L)
    }

    @Test
    fun `an active session round-trips with its planned and answered ids`() = runTest {
        database.skillDao().upsert(skill())
        database.deckDao().upsert(deck())
        val session = SessionStateEntity(
            id = "session-1",
            deckId = "deck-1",
            startedAt = 1_000L,
            itemBudget = 5,
            plannedItemIds = listOf("item-1", "item-2", "item-3"),
            answeredItemIds = listOf("item-1"),
            correctCount = 1,
            endedAt = null,
            status = SessionStatus.ACTIVE,
        )
        database.sessionDao().upsert(session)

        val stored = database.sessionDao().getActiveForDeck("deck-1", SessionStatus.ACTIVE)!!
        assertThat(stored.plannedItemIds).containsExactly("item-1", "item-2", "item-3").inOrder()
        assertThat(stored.answeredItemIds).containsExactly("item-1")
        assertThat(stored.status).isEqualTo(SessionStatus.ACTIVE)

        database.sessionDao().upsert(session.copy(status = SessionStatus.ENDED))
        assertThat(
            database.sessionDao().getActiveForDeck("deck-1", SessionStatus.ACTIVE),
        ).isNull()
    }

    @Test
    fun `reminder state round-trips`() = runTest {
        database.reminderDao().upsert(
            ReminderStateEntity(
                id = "reminders",
                lastNotifiedDueSignature = "sig-1",
                lastNotifiedAt = 4_000L,
            ),
        )

        val stored = database.reminderDao().getById("reminders")!!
        assertThat(stored.lastNotifiedDueSignature).isEqualTo("sig-1")
        assertThat(stored.lastNotifiedAt).isEqualTo(4_000L)
    }
}
