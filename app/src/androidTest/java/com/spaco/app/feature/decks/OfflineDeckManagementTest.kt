package com.spaco.app.feature.decks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import com.spaco.app.MainActivity
import com.spaco.app.core.data.db.SpaCoDatabase
import com.spaco.app.core.domain.repository.DeckRepository
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
 * End-to-end deck management with the device offline.
 *
 * The whole flow is driven through the real UI against the real Room database:
 * create a deck, add items, rename, archive, restore, then restart the activity
 * and confirm nothing was lost. Airplane mode is enabled for the duration so a
 * hidden network dependency would fail the test.
 */
@HiltAndroidTest
class OfflineDeckManagementTest {

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
    fun `deck_management_works_offline_and_survives_a_restart`() {
        val skill = "Skill-$suffix"
        val deck = "Deck-$suffix"
        val renamed = "Renamed-$suffix"

        // Create a deck.
        composeRule.onNodeWithTag(DeckListTags.ADD).performClick()
        composeRule.onNodeWithTag(DeckListTags.SKILL_FIELD).performTextInput(skill)
        composeRule.onNodeWithTag(DeckListTags.DECK_FIELD).performTextInput(deck)
        composeRule.onNodeWithTag(DeckListTags.CONFIRM_CREATE).performClick()
        awaitNoTag(DeckListTags.SKILL_FIELD)
        awaitText(deck)
        val deckId = awaitDeckId(deck)

        // Open the deck and add two items through the item editor. Clicking the
        // merged row (whose centre lands on a chip) does nothing, so target the
        // name text itself via the unmerged tree.
        composeRule.onNodeWithText(deck, useUnmergedTree = true).performClick()
        awaitTag(DeckDetailTags.ADD_ITEM)
        addItem("q1-$suffix", "a1-$suffix")
        addItem("q2-$suffix", "a2-$suffix")
        awaitText("q2-$suffix")
        composeRule.onNodeWithTag(DeckDetailTags.BACK).performClick()
        awaitTag(DeckListTags.LIST)

        // Make it the active deck.
        composeRule.onNodeWithTag("deck-select-$deckId").performClick()
        awaitTag(DeckListTags.activeBadge(deckId))

        // Rename.
        composeRule.onNodeWithText("⋮").performClick()
        awaitText("Rename")
        composeRule.onNodeWithText("Rename").performClick()
        awaitTag(RENAME_FIELD)
        composeRule.onNodeWithTag(RENAME_FIELD).performTextClearance()
        composeRule.onNodeWithTag(RENAME_FIELD).performTextInput(renamed)
        composeRule.onNodeWithTag(RENAME_CONFIRM).performClick()
        awaitNoTag(RENAME_FIELD)
        awaitText(renamed)

        // Archive, then restore.
        composeRule.onNodeWithText("⋮").performClick()
        awaitTag(DeckListTags.archive(deckId))
        composeRule.onNodeWithTag(DeckListTags.archive(deckId)).performClick()
        awaitTag(DeckListTags.ARCHIVED_SECTION)
        awaitText(renamed)

        composeRule.onNodeWithTag(DeckListTags.restore(deckId)).performClick()
        awaitNoTag(DeckListTags.ARCHIVED_SECTION)
        awaitText(renamed)

        // Restart: recreate the activity and confirm the content is still there.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        awaitTag(DeckListTags.LIST)
        awaitText(renamed)

        composeRule.onNodeWithText(renamed, useUnmergedTree = true).performClick()
        awaitText("q1-$suffix")
        composeRule.onNodeWithText("q2-$suffix").assertIsDisplayed()
    }

    private fun addItem(prompt: String, answer: String) {
        composeRule.onNodeWithTag(DeckDetailTags.ADD_ITEM).performClick()
        composeRule.onNodeWithTag(DeckDetailTags.PROMPT_FIELD).performTextInput(prompt)
        composeRule.onNodeWithTag(DeckDetailTags.ANSWER_FIELD).performTextInput(answer)
        composeRule.onNodeWithTag(DeckDetailTags.SAVE).performClick()
        awaitNoTag(DeckDetailTags.PROMPT_FIELD)
        awaitText(prompt)
    }

    private fun awaitDeckId(name: String): String = runBlocking {
        withTimeout(TIMEOUT) {
            deckRepository.observeAll().first { decks -> decks.any { it.name == name } }
        }.first { it.name == name }.id
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
            composeRule.waitUntil(timeoutMillis = TIMEOUT) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: ComposeTimeoutException) {
            dumpTree("tag '$tag'")
            throw failure
        }
    }

    private fun awaitNoTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun setAirplaneMode(enabled: Boolean) {
        val command = "cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}"
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
            .close()
    }

    private fun dumpTree(expected: String) {
        println("SPACO_DUMP_BEGIN expected $expected")
        println(composeRule.onRoot().printToString(maxDepth = 20))
        println("SPACO_DUMP_END")
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val RENAME_FIELD = "deck-rename-field"
        const val RENAME_CONFIRM = "deck-rename-confirm"
    }
}
