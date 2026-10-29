package com.spaco.app.core.domain.repository

import com.spaco.app.core.domain.model.DeckProgress
import com.spaco.app.core.domain.model.OverallProgress
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Read-only progress over scheduling state and review history. Everything is
 * `Flow`-backed so the views follow new answers without a restart.
 */
interface ProgressRepository {

    /** Progress for one deck, or `null` if the deck no longer exists. */
    fun observeDeckProgress(deckId: String, now: Instant): Flow<DeckProgress?>

    /** Progress across all non-archived decks. Archived decks are excluded. */
    fun observeOverallProgress(now: Instant): Flow<OverallProgress>
}
