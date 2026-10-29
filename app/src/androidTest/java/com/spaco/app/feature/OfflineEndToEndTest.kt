package com.spaco.app.feature

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.spaco.app.MainActivity
import com.spaco.app.core.data.db.SpaCoDatabase
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.feature.decks.DeckDetailTags
import com.spaco.app.feature.decks.DeckListTags
import com.spaco.app.feature.progress.ProgressTags
import com.spaco.app.feature.session.SessionTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The whole core path with connectivity disabled: create a deck, add items, run
 * a complete session, and see progress update. Every step is local, so nothing
 * here may depend on the network.
 */
@HiltAndroidTest
class OfflineEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: SpaCoDatabase

    @Inject
    lateinit var deckRepository: DeckRepository

    private lateinit var suffix: String

    @Before
    fun setUp() {
        hiltRule.inject()
        database.clearAllTables()
        setAirplaneMode(enabled = true)
        suffix = System.currentTimeMillis().toString()
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        setAirplaneMode(enabled = false)
    }

    @Test
    fun `full_offline_path_creates_practices_and_updates_progress`() {
        val deck = "E2E-$suffix"
        createFreeRecallDeck(deck, listOf("e1-$suffix" to "a1-$suffix", "e2-$suffix" to "a2-$suffix"))

        val deckId = runBlockingDeckId(deck)
        composeRule.onNodeWithTag("deck-select-$deckId").performClick()
        awaitTag(DeckListTags.activeBadge(deckId))
        composeRule.onNodeWithTag(DeckListTags.PRACTICE).performClick()

        // Answer both items; the deck has exactly two.
        answerCurrentItem()
        composeRule.onNodeWithTag(SessionTags.CONTINUE).performClick()
        awaitTag(SessionTags.ANSWER_FIELD)
        answerCurrentItem()
        composeRule.onNodeWithTag(SessionTags.CONTINUE).performClick()

        awaitTag(SessionTags.SUMMARY)
        composeRule.onNodeWithTag(SessionTags.SUMMARY_ANSWERED).assertTextEquals("Answered: 2")
        composeRule.onNodeWithTag(SessionTags.DONE).performClick()
        awaitTag(DeckListTags.LIST)

        // Progress reflects the completed session: nothing is due any more,
        // accuracy is perfect, and today counts as a practice day.
        composeRule.onNodeWithTag(DeckListTags.PROGRESS).performClick()
        awaitTag(ProgressTags.RING)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProgressTags.DUE_COUNT).assertTextEquals("0")
        composeRule.onNodeWithTag(ProgressTags.STREAK).assertTextEquals("1")
        composeRule.onNodeWithTag(ProgressTags.ACCURACY).assertTextEquals("100%")
    }

    private fun answerCurrentItem() {
        awaitTag(SessionTags.ANSWER_FIELD)
        composeRule.onNodeWithTag(SessionTags.ANSWER_FIELD).performTextInput("attempt")
        composeRule.onNodeWithTag(SessionTags.SUBMIT).performClick()
        awaitTag(SessionTags.SELF_GRADE_CORRECT)
        composeRule.onNodeWithTag(SessionTags.SELF_GRADE_CORRECT).performClick()
        awaitTag(SessionTags.CONTINUE)
    }

    private fun createFreeRecallDeck(deck: String, items: List<Pair<String, String>>) {
        composeRule.onNodeWithTag(DeckListTags.ADD).performClick()
        composeRule.onNodeWithTag(DeckListTags.SKILL_FIELD).performTextInput("Skill-$suffix")
        composeRule.onNodeWithTag(DeckListTags.DECK_FIELD).performTextInput(deck)
        composeRule.onNodeWithTag(DeckListTags.CONFIRM_CREATE).performClick()
        awaitNoTag(DeckListTags.SKILL_FIELD)
        awaitText(deck)

        composeRule.onNodeWithText(deck, useUnmergedTree = true).performClick()
        awaitTag(DeckDetailTags.ADD_ITEM)
        items.forEach { (prompt, answer) ->
            composeRule.onNodeWithTag(DeckDetailTags.ADD_ITEM).performClick()
            composeRule.onNodeWithTag(DeckDetailTags.PROMPT_FIELD).performTextInput(prompt)
            composeRule.onNodeWithTag(DeckDetailTags.ANSWER_FIELD).performTextInput(answer)
            composeRule.onNodeWithTag(DeckDetailTags.FREE_RECALL).performClick()
            composeRule.onNodeWithTag(DeckDetailTags.SAVE).performClick()
            awaitNoTag(DeckDetailTags.PROMPT_FIELD)
            awaitText(prompt)
        }
        composeRule.onNodeWithTag(DeckDetailTags.BACK).performClick()
        awaitTag(DeckListTags.LIST)
    }

    private fun runBlockingDeckId(name: String): String = runBlocking {
        withTimeout(TIMEOUT) {
            deckRepository.observeAll().first { decks -> decks.any { it.name == name } }
        }.first { it.name == name }.id
    }

    private fun hasTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun awaitNoTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT) { !hasTag(tag) }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT) { hasTag(tag) }
    }

    private fun setAirplaneMode(enabled: Boolean) {
        val command = "cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}"
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .close()
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
