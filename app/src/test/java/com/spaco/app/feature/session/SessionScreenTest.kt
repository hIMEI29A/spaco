package com.spaco.app.feature.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.ItemSchedule
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-06-01T09:00:00Z")

    private fun item() = PracticeItem(
        id = "item-1",
        deckId = "deck-1",
        prompt = "What is 2+2?",
        expectedAnswer = "4",
        explanation = "Basic arithmetic.",
        createdAt = now,
        schedule = ItemSchedule(now, LearningState.NEW, 0, 0),
    )

    private fun runningState(grade: Grade? = null, awaitingSelfGrade: Boolean = false) =
        SessionUiState(
            status = SessionPhase.RUNNING,
            deckName = "Deck",
            currentItem = item(),
            presentedPrompt = "What is 2+2?",
            presentedExpectedAnswer = "4",
            presentedExplanation = "Basic arithmetic.",
            plannedCount = 3,
            answeredCount = 0,
            grade = grade,
            awaitingSelfGrade = awaitingSelfGrade,
        )

    @Test
    fun `the submit control is enabled while a question is shown`() {
        composeRule.setContent { SessionScreen(state = runningState()) }

        composeRule.onNodeWithTag(SessionTags.SUBMIT).assertIsEnabled()
        composeRule.onNodeWithTag(SessionTags.PROMPT).assertIsDisplayed()
    }

    @Test
    fun `submitting an answer reports the typed text`() {
        var submitted: String? = null
        composeRule.setContent {
            SessionScreen(state = runningState(), onSubmitAnswer = { submitted = it })
        }

        composeRule.onNodeWithTag(SessionTags.ANSWER_FIELD).performTextInput("4")
        composeRule.onNodeWithTag(SessionTags.SUBMIT).performClick()

        assertThat(submitted).isEqualTo("4")
    }

    @Test
    fun `feedback shows the expected answer and keeps continue enabled`() {
        composeRule.setContent {
            SessionScreen(state = runningState(grade = Grade.INCORRECT))
        }

        composeRule.onNodeWithTag(SessionTags.FEEDBACK).assertIsDisplayed()
        composeRule.onNodeWithTag(SessionTags.EXPECTED_ANSWER).assertIsDisplayed()
        // The continue control is usable immediately, not gated on the reveal animation.
        composeRule.onNodeWithTag(SessionTags.CONTINUE).assertIsEnabled()
    }

    @Test
    fun `a free-recall item offers self-grading controls`() {
        var selfGrade: Boolean? = null
        composeRule.setContent {
            SessionScreen(
                state = runningState(awaitingSelfGrade = true),
                onSelfGrade = { selfGrade = it },
            )
        }

        composeRule.onNodeWithTag(SessionTags.SELF_GRADE_CORRECT).performClick()

        assertThat(selfGrade).isTrue()
    }

    @Test
    fun `the summary reports answered and correct counts`() {
        composeRule.setContent {
            SessionScreen(
                state = SessionUiState(
                    status = SessionPhase.SUMMARY,
                    answeredCount = 4,
                    correctCount = 3,
                ),
            )
        }

        composeRule.onNodeWithTag(SessionTags.SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(SessionTags.SUMMARY_ANSWERED).assertIsDisplayed()
        composeRule.onNodeWithTag(SessionTags.SUMMARY_CORRECT).assertIsDisplayed()
    }

    @Test
    fun `nothing-due offers to practice ahead`() {
        var practiceAhead = false
        composeRule.setContent {
            SessionScreen(
                state = SessionUiState(status = SessionPhase.NOTHING_DUE),
                onPracticeAhead = { practiceAhead = true },
            )
        }

        composeRule.onNodeWithTag(SessionTags.NOTHING_DUE).assertIsDisplayed()
        composeRule.onNodeWithTag(SessionTags.PRACTICE_AHEAD).performClick()

        assertThat(practiceAhead).isTrue()
    }
}
