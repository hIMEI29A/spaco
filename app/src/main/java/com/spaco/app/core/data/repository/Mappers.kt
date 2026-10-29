package com.spaco.app.core.data.repository

import com.spaco.app.core.data.db.DeckEntity
import com.spaco.app.core.data.db.PracticeItemEntity
import com.spaco.app.core.data.db.ReviewEventEntity
import com.spaco.app.core.data.db.SessionStateEntity
import com.spaco.app.core.data.db.SkillEntity
import com.spaco.app.core.domain.model.Deck
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ReviewEvent
import com.spaco.app.core.domain.model.SessionState
import com.spaco.app.core.domain.model.Skill
import java.time.Instant

/**
 * Entity <-> domain mapping. Entities are a storage detail, so the conversion
 * lives here rather than leaking Room annotations into `core/domain`.
 */

fun SkillEntity.toDomain(): Skill = Skill(
    id = id,
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt),
    archivedAt = archivedAt?.let(Instant::ofEpochMilli),
)

fun Skill.toEntity(): SkillEntity = SkillEntity(
    id = id,
    name = name,
    createdAt = createdAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
)

fun DeckEntity.toDomain(): Deck = Deck(
    id = id,
    skillId = skillId,
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt),
    archivedAt = archivedAt?.let(Instant::ofEpochMilli),
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    skillId = skillId,
    name = name,
    createdAt = createdAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
)

fun PracticeItemEntity.toDomain(): PracticeItem = PracticeItem(
    id = id,
    deckId = deckId,
    prompt = prompt,
    expectedAnswer = expectedAnswer,
    explanation = explanation,
    createdAt = Instant.ofEpochMilli(createdAt),
    archivedAt = archivedAt?.let(Instant::ofEpochMilli),
    isFreeRecall = isFreeRecall,
    schedule = ItemSchedule(
        nextDueAt = Instant.ofEpochMilli(nextDueAt),
        state = learningState,
        intervalIndex = intervalIndex,
        correctStreak = correctStreak,
    ),
)

fun PracticeItem.toEntity(): PracticeItemEntity = PracticeItemEntity(
    id = id,
    deckId = deckId,
    prompt = prompt,
    expectedAnswer = expectedAnswer,
    explanation = explanation,
    createdAt = createdAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
    isFreeRecall = isFreeRecall,
    nextDueAt = schedule.nextDueAt.toEpochMilli(),
    learningState = schedule.state,
    intervalIndex = schedule.intervalIndex,
    correctStreak = schedule.correctStreak,
)

fun ReviewEventEntity.toDomain(): ReviewEvent = ReviewEvent(
    id = id,
    itemId = itemId,
    answeredAt = Instant.ofEpochMilli(answeredAt),
    correct = correct,
    gradedBy = gradedBy,
    latencyMs = latencyMs,
)

fun ReviewEvent.toEntity(): ReviewEventEntity = ReviewEventEntity(
    id = id,
    itemId = itemId,
    answeredAt = answeredAt.toEpochMilli(),
    correct = correct,
    gradedBy = gradedBy,
    latencyMs = latencyMs,
)

fun SessionStateEntity.toDomain(): SessionState = SessionState(
    id = id,
    deckId = deckId,
    startedAt = Instant.ofEpochMilli(startedAt),
    itemBudget = itemBudget,
    plannedItemIds = plannedItemIds,
    answeredItemIds = answeredItemIds,
    correctCount = correctCount,
    endedAt = endedAt?.let(Instant::ofEpochMilli),
    status = status,
)

fun SessionState.toEntity(): SessionStateEntity = SessionStateEntity(
    id = id,
    deckId = deckId,
    startedAt = startedAt.toEpochMilli(),
    itemBudget = itemBudget,
    plannedItemIds = plannedItemIds,
    answeredItemIds = answeredItemIds,
    correctCount = correctCount,
    endedAt = endedAt?.toEpochMilli(),
    status = status,
)
