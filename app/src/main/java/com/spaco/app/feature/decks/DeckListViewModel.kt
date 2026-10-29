package com.spaco.app.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaco.app.core.domain.model.Deck
import com.spaco.app.core.domain.repository.ActiveDeckStore
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.core.domain.repository.SkillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeckGroup(
    val skillId: String,
    val skillName: String,
    val decks: List<Deck>,
)

data class ArchivedDeck(
    val deck: Deck,
    val skillName: String,
)

data class DeckListUiState(
    val groups: List<DeckGroup> = emptyList(),
    val archivedDecks: List<ArchivedDeck> = emptyList(),
    val activeDeckId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DeckListViewModel @Inject constructor(
    skillRepository: SkillRepository,
    deckRepository: DeckRepository,
    private val activeDeckStore: ActiveDeckStore,
) : ViewModel() {

    private val repository = deckRepository
    private val skills = skillRepository

    private val errorMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val uiState: StateFlow<DeckListUiState> = combine(
        skillRepository.observeAll(),
        deckRepository.observeAll(),
        activeDeckStore.activeDeckId,
        errorMessage,
    ) { skills, decks, activeId, error ->
        val activeByName = skills.associate { it.id to it.name }
        val groups = decks
            .filterNot { it.isArchived }
            .groupBy { it.skillId }
            .mapNotNull { (skillId, groupDecks) ->
                val skillName = activeByName[skillId] ?: return@mapNotNull null
                DeckGroup(skillId = skillId, skillName = skillName, decks = groupDecks)
            }
            .sortedBy { it.skillName.lowercase() }

        val archived = decks
            .filter { it.isArchived }
            .mapNotNull { deck ->
                activeByName[deck.skillId]?.let { ArchivedDeck(deck = deck, skillName = it) }
            }

        DeckListUiState(
            groups = groups,
            archivedDecks = archived,
            activeDeckId = activeId,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckListUiState())

    fun createDeck(skillName: String, deckName: String) {
        val trimmedSkill = skillName.trim()
        val trimmedDeck = deckName.trim()
        if (trimmedSkill.isEmpty() || trimmedDeck.isEmpty()) {
            errorMessage.value = "A skill and a deck name are both required."
            return
        }

        viewModelScope.launch {
            errorMessage.value = null
            val skill = skills.getByName(trimmedSkill) ?: skills.create(trimmedSkill)
            val deck = repository.create(skill.id, trimmedDeck)
            activeDeckStore.setActiveDeck(deck.id)
        }
    }

    fun selectDeck(deckId: String) {
        viewModelScope.launch { activeDeckStore.setActiveDeck(deckId) }
    }

    fun renameDeck(deckId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            errorMessage.value = "A deck name is required."
            return
        }
        viewModelScope.launch {
            errorMessage.value = null
            repository.rename(deckId, trimmed)
        }
    }

    fun archiveDeck(deckId: String) {
        viewModelScope.launch { repository.archive(deckId) }
    }

    fun restoreDeck(deckId: String) {
        viewModelScope.launch { repository.restore(deckId) }
    }

    fun dismissError() {
        errorMessage.value = null
    }
}
