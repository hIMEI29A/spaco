package com.spaco.app.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.SessionStatus

@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val archivedAt: Long? = null,
)

@Entity(
    tableName = "decks",
    foreignKeys = [
        ForeignKey(
            entity = SkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("skillId")],
)
data class DeckEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val name: String,
    val createdAt: Long,
    val archivedAt: Long? = null,
)

/**
 * A practice item.
 *
 * `nextDueAt`, `learningState`, `intervalIndex` and `correctStreak` are a
 * *derived cache* of the item's review history — see `Scheduler.recompute`.
 * They exist so due queries and background work are cheap; they are never
 * edited directly except by the single writer that recomputes them.
 */
@Entity(
    tableName = "practice_items",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId"), Index(value = ["deckId", "nextDueAt"])],
)
data class PracticeItemEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val prompt: String,
    val expectedAnswer: String,
    val explanation: String? = null,
    val createdAt: Long,
    val archivedAt: Long? = null,
    val isFreeRecall: Boolean = false,
    val nextDueAt: Long,
    val learningState: LearningState,
    val intervalIndex: Int,
    val correctStreak: Int,
)

/**
 * One graded answer. Append-only: rows are inserted and never updated or
 * deleted, which is what makes scheduling reproducible.
 */
@Entity(
    tableName = "review_events",
    foreignKeys = [
        ForeignKey(
            entity = PracticeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["itemId", "answeredAt"])],
)
data class ReviewEventEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val answeredAt: Long,
    val correct: Boolean,
    val gradedBy: GradedBy,
    val latencyMs: Long? = null,
)

/**
 * A practice session, persisted while active so an interrupted session can be
 * resumed. `answeredItemIds` is stored rather than an answered count so the
 * count cannot drift away from the ids it is derived from.
 */
@Entity(tableName = "sessions")
data class SessionStateEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val startedAt: Long,
    val itemBudget: Int,
    val plannedItemIds: List<String>,
    val answeredItemIds: List<String>,
    val correctCount: Int,
    val endedAt: Long? = null,
    val status: SessionStatus,
)

/**
 * The last due set the learner was notified about, so the same due set is not
 * announced twice.
 */
@Entity(tableName = "reminder_state")
data class ReminderStateEntity(
    @PrimaryKey val id: String,
    val lastNotifiedDueSignature: String? = null,
    val lastNotifiedAt: Long? = null,
)
