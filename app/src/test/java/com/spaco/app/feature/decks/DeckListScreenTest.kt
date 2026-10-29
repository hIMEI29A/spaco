package com.spaco.app.feature.decks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.Deck
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun deck(id: String, name: String, skillId: String = "skill-1", archived: Boolean = false) =
        Deck(
            id = id,
            skillId = skillId,
            name = name,
            createdAt = now,
            archivedAt = if (archived) now else null,
        )

    private fun render(
        state: DeckListUiState,
        onCreateDeck: (String, String) -> Unit = { _, _ -> },
        onSelectDeck: (String) -> Unit = {},
        onOpenDeck: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            DeckListScreen(
                state = state,
                onOpenDeck = onOpenDeck,
                onCreateDeck = onCreateDeck,
                onSelectDeck = onSelectDeck,
                onRenameDeck = { _, _ -> },
                onArchiveDeck = {},
                onRestoreDeck = {},
            )
        }
    }

    @Test
    fun `creating a deck submits the skill and deck names`() {
        var created: Pair<String, String>? = null
        render(DeckListUiState(), onCreateDeck = { skill, deck -> created = skill to deck })

        composeRule.onNodeWithTag(DeckListTags.ADD).performClick()
        composeRule.onNodeWithTag(DeckListTags.SKILL_FIELD).performTextInput("Math")
        composeRule.onNodeWithTag(DeckListTags.DECK_FIELD).performTextInput("Algebra")
        composeRule.onNodeWithTag(DeckListTags.CONFIRM_CREATE).performClick()

        assertThat(created).isEqualTo("Math" to "Algebra")
    }

    @Test
    fun `an empty deck name is rejected with a message and creates nothing`() {
        var created: Pair<String, String>? = null
        render(DeckListUiState(), onCreateDeck = { skill, deck -> created = skill to deck })

        composeRule.onNodeWithTag(DeckListTags.ADD).performClick()
        composeRule.onNodeWithTag(DeckListTags.SKILL_FIELD).performTextInput("Math")
        composeRule.onNodeWithTag(DeckListTags.CONFIRM_CREATE).performClick()

        composeRule.onNodeWithTag(DeckListTags.ERROR).assertIsDisplayed()
        assertThat(created).isNull()
    }

    @Test
    fun `selecting a deck marks it as the chosen one`() {
        var selected: String? = null
        val state = DeckListUiState(
            groups = listOf(DeckGroup("skill-1", "Math", listOf(deck("d1", "Algebra")))),
            activeDeckId = "d2",
        )
        render(state, onSelectDeck = { selected = it })

        composeRule.onNodeWithTag("deck-select-d1").performClick()

        assertThat(selected).isEqualTo("d1")
    }

    @Test
    fun `an archived deck is not shown among active decks but is restorable`() {
        val state = DeckListUiState(
            groups = listOf(DeckGroup("skill-1", "Math", listOf(deck("d1", "Algebra")))),
            archivedDecks = listOf(ArchivedDeck(deck("d9", "Retired"), "Math")),
        )
        render(state)

        // The archived deck is not offered as a practice-selection target...
        composeRule.onNodeWithTag("deck-select-d9").assertDoesNotExist()
        // ...but it is listed under the Archived section with a restore action.
        composeRule.onNodeWithTag(DeckListTags.ARCHIVED_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Retired").assertIsDisplayed()
        composeRule.onNodeWithTag(DeckListTags.restore("d9")).assertIsDisplayed()
    }
}
