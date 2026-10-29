package com.spaco.app.feature.session

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.spaco.app.MainActivity
import com.spaco.app.core.data.db.SpaCoDatabase
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.core.domain.repository.ReviewRepository
import com.spaco.app.feature.decks.DeckDetailTags
import com.spaco.app.feature.decks.DeckListTags
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
 * An interrupted session must be resumable and must not lose the answers already
 * given.
 *
 * The interruption closes the activity and launches a fresh one, so the
 * session's in-memory state is destroyed: the resume path can only work by
 * reading the answers back from the database. The items are free-recall so the
 * learner grades their own attempt, which makes the recorded outcome
 * deterministic without exposing the expected answer.
 */
@HiltAndroidTest
class SessionResumeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var database: SpaCoDatabase

    @Inject
    lateinit var reviewRepository: ReviewRepository

    @Inject
    lateinit var deckRepository: DeckRepository

    private lateinit var suffix: String
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        database.clearAllTables()
        suffix = System.currentTimeMillis().toString()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun `interrupted_session_resumes_and_keeps_earlier_answers`() {
        val deck = "Resume-$suffix"
        launch()

        createFreeRecallDeck(deck, listOf("q1-$suffix" to "a1-$suffix", "q2-$suffix" to "a2-$suffix"))
        val deckId = awaitDeckId(deck)

        composeRule.onNodeWithTag("deck-select-$deckId").performClick()
        awaitTag(DeckListTags.activeBadge(deckId))
        composeRule.onNodeWithTag(DeckListTags.PRACTICE).performClick()

        answerCurrentItem()
        awaitTag(SessionTags.CONTINUE)
        composeRule.onNodeWithTag(SessionTags.CONTINUE).performClick()
        awaitTag(SessionTags.PROMPT)

        assertThat(reviews()).hasSize(1)

        // Interrupt the session: destroy the activity (and its ViewModels) and
        // start a fresh one. Only the persisted session can bring it back.
        scenario.close()
        launch()

        // Start the session again; the interrupted one is offered for resume.
        awaitTag(DeckListTags.LIST)
        composeRule.onNodeWithTag(DeckListTags.PRACTICE).performClick()
        awaitTag(SessionTags.RESUMABLE)

        // The earlier answer survived the interruption.
        assertThat(reviews()).hasSize(1)

        // Resuming does not re-ask the answered item; finishing answers the second.
        composeRule.onNodeWithTag(SessionTags.RESUME).performClick()
        awaitTag(SessionTags.PROMPT)
        answerCurrentItem()
        awaitTag(SessionTags.CONTINUE)
        composeRule.onNodeWithTag(SessionTags.CONTINUE).performClick()

        awaitTag(SessionTags.SUMMARY)
        composeRule.onNodeWithTag(SessionTags.SUMMARY_ANSWERED).assertTextEquals("Answered: 2")
        assertThat(reviews()).hasSize(2)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
    }

    private fun answerCurrentItem() {
        awaitTag(SessionTags.ANSWER_FIELD)
        composeRule.onNodeWithTag(SessionTags.ANSWER_FIELD).performTextInput("attempt")
        composeRule.onNodeWithTag(SessionTags.SUBMIT).performClick()
        awaitTag(SessionTags.SELF_GRADE_CORRECT)
        composeRule.onNodeWithTag(SessionTags.SELF_GRADE_CORRECT).performClick()
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

    private fun reviews() = runBlocking { reviewRepository.getAll() }

    private fun awaitDeckId(name: String): String = runBlocking {
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
        try {
            composeRule.waitUntil(timeoutMillis = TIMEOUT) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: ComposeTimeoutException) {
            dumpTree("text '$text'")
            throw failure
        }
    }

    private fun awaitTag(tag: String) {
        try {
            composeRule.waitUntil(timeoutMillis = TIMEOUT) { hasTag(tag) }
        } catch (failure: ComposeTimeoutException) {
            dumpTree("tag '$tag'")
            throw failure
        }
    }

    private fun dumpTree(expected: String) {
        println("SPACO_DUMP_BEGIN expected $expected")
        println(composeRule.onRoot().printToString(maxDepth = 20))
        println("SPACO_DUMP_END")
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
