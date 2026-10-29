package com.spaco.app.feature.decks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.core.domain.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeckDetailUiState(
    val deckName: String = "",
    val items: List<PracticeItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class DeckDetailViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val itemRepository: ItemRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle[DECK_ID_ARG]) {
        "DeckDetailViewModel requires a $DECK_ID_ARG argument"
    }

    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DeckDetailUiState> = combine(
        deckRepository.observeAll(),
        itemRepository.observeForDeck(deckId),
        errorMessage,
    ) { decks, items, error ->
        DeckDetailUiState(
            deckName = decks.firstOrNull { it.id == deckId }?.name.orEmpty(),
            items = items,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckDetailUiState())

    fun addItem(
        prompt: String,
        expectedAnswer: String,
        explanation: String?,
        isFreeRecall: Boolean = false,
    ) {
        if (prompt.isBlank() || expectedAnswer.isBlank()) {
            errorMessage.value = "A prompt and an expected answer are both required."
            return
        }
        viewModelScope.launch {
            errorMessage.value = null
            itemRepository.add(
                deckId,
                prompt.trim(),
                expectedAnswer.trim(),
                explanation?.trim()?.ifEmpty { null },
                isFreeRecall,
            )
        }
    }

    fun updateItem(
        id: String,
        prompt: String,
        expectedAnswer: String,
        explanation: String?,
        isFreeRecall: Boolean = false,
    ) {
        if (prompt.isBlank() || expectedAnswer.isBlank()) {
            errorMessage.value = "A prompt and an expected answer are both required."
            return
        }
        viewModelScope.launch {
            errorMessage.value = null
            itemRepository.update(
                id,
                prompt.trim(),
                expectedAnswer.trim(),
                explanation?.trim()?.ifEmpty { null },
                isFreeRecall,
            )
        }
    }

    fun archiveItem(id: String) {
        viewModelScope.launch { itemRepository.archive(id) }
    }

    fun restoreItem(id: String) {
        viewModelScope.launch { itemRepository.restore(id) }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    companion object {
        const val DECK_ID_ARG = "deckId"
    }
}
