package com.spaco.app.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Remembers which deck the learner last selected, so the selection survives an
 * app restart.
 */
interface ActiveDeckStore {

    val activeDeckId: Flow<String?>

    suspend fun setActiveDeck(deckId: String)
}
