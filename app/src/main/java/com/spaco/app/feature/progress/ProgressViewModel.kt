package com.spaco.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaco.app.core.domain.model.DeckProgress
import com.spaco.app.core.domain.model.OverallProgress
import com.spaco.app.core.domain.repository.ActiveDeckStore
import com.spaco.app.core.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(
    val overall: OverallProgress? = null,
    val deck: DeckProgress? = null,
    val loaded: Boolean = false,
) {
    /** True when there is nothing to show yet, so an explicit empty state is used. */
    val isEmpty: Boolean get() = loaded && (overall?.totalItems ?: 0) == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    activeDeckStore: ActiveDeckStore,
    clock: Clock,
) : ViewModel() {

    // Captured once per screen entry: due counts are "as of now", and the flows
    // still push updates whenever items or reviews change.
    private val now = clock.instant()

    private val deckProgress = activeDeckStore.activeDeckId.flatMapLatest { deckId ->
        if (deckId == null) flowOf(null) else progressRepository.observeDeckProgress(deckId, now)
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        progressRepository.observeOverallProgress(now),
        deckProgress,
    ) { overall, deck ->
        ProgressUiState(overall = overall, deck = deck, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
