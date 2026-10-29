package com.spaco.app.feature.decks

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.ItemSchedule
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-05-01T10:00:00Z")

    private fun item(id: String, prompt: String = "prompt-$id", answer: String = "answer-$id") =
        PracticeItem(
            id = id,
            deckId = "deck-1",
            prompt = prompt,
            expectedAnswer = answer,
            createdAt = now,
            schedule = ItemSchedule(now, LearningState.NEW, 0, 0),
        )

    @Test
    fun `saving a valid item submits its fields`() {
        var added: Triple<String, String, String?>? = null
        composeRule.setContent {
            DeckDetailScreen(
                state = DeckDetailUiState(deckName = "Algebra"),
                onBack = {},
                onAddItem = { prompt, answer, explanation, _ -> added = Triple(prompt, answer, explanation) },
                onUpdateItem = { _, _, _, _, _ -> },
            )
        }

        composeRule.onNodeWithTag(DeckDetailTags.ADD_ITEM).performClick()
        composeRule.onNodeWithTag(DeckDetailTags.PROMPT_FIELD).performTextInput("2+2")
        composeRule.onNodeWithTag(DeckDetailTags.ANSWER_FIELD).performTextInput("4")
        composeRule.onNodeWithTag(DeckDetailTags.SAVE).performClick()

        assertThat(added).isEqualTo(Triple("2+2", "4", null))
    }

    @Test
    fun `a missing prompt and expected answer are rejected with field-level messages`() {
        var added: Any? = null
        composeRule.setContent {
            DeckDetailScreen(
                state = DeckDetailUiState(deckName = "Algebra"),
                onBack = {},
                onAddItem = { _, _, _, _ -> added = "called" },
                onUpdateItem = { _, _, _, _, _ -> },
            )
        }

        composeRule.onNodeWithTag(DeckDetailTags.ADD_ITEM).performClick()
        composeRule.onNodeWithTag(DeckDetailTags.SAVE).performClick()

        composeRule.onNodeWithTag(DeckDetailTags.PROMPT_ERROR).assertExists()
        composeRule.onNodeWithTag(DeckDetailTags.ANSWER_ERROR).assertExists()
        assertThat(added).isNull()
    }

    @Test
    fun `editing an existing item keeps its identity`() {
        val existing = item("i1", prompt = "old prompt", answer = "old answer")
        var updated: Pair<String, String>? = null
        composeRule.setContent {
            DeckDetailScreen(
                state = DeckDetailUiState(deckName = "Algebra", items = listOf(existing)),
                onBack = {},
                onAddItem = { _, _, _, _ -> },
                onUpdateItem = { id, prompt, _, _, _ -> updated = id to prompt },
            )
        }

        composeRule.onNodeWithTag(DeckDetailTags.itemRow("i1")).performClick()
        composeRule.onNodeWithTag(DeckDetailTags.PROMPT_FIELD).performTextReplacement("new prompt")
        composeRule.onNodeWithTag(DeckDetailTags.SAVE).performClick()

        assertThat(updated).isEqualTo("i1" to "new prompt")
    }
}
