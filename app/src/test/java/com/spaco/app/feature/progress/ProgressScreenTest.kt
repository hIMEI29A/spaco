package com.spaco.app.feature.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.spaco.app.core.domain.model.Accuracy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.OverallProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun overall(due: Int, correct: Int, total: Int, streak: Int) = OverallProgress(
        totalItems = 10,
        dueCount = due,
        stateCounts = mapOf(LearningState.MASTERED to 4),
        accuracy = Accuracy(correct = correct, total = total, windowDays = 7),
        streakDays = streak,
    )

    @Test
    fun `the empty state is explicit rather than zeros`() {
        composeRule.setContent {
            ProgressScreen(
                ProgressUiState(
                    overall = OverallProgress(0, 0, emptyMap(), Accuracy(0, 0, 7), 0),
                    loaded = true,
                ),
            )
        }

        composeRule.onNodeWithTag(ProgressTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun `resting values equal the current true values`() {
        composeRule.setContent {
            ProgressScreen(ProgressUiState(overall = overall(3, 8, 10, 5), loaded = true))
        }

        composeRule.onNodeWithTag(ProgressTags.DUE_COUNT).assertTextEquals("3")
        composeRule.onNodeWithTag(ProgressTags.ACCURACY).assertTextEquals("80%")
        composeRule.onNodeWithTag(ProgressTags.STREAK).assertTextEquals("5")
        composeRule.onNodeWithTag(ProgressTags.RING).assertIsDisplayed()
    }

    @Test
    fun `accuracy with no history is shown as unavailable`() {
        composeRule.setContent {
            ProgressScreen(ProgressUiState(overall = overall(1, 0, 0, 0), loaded = true))
        }

        composeRule.onNodeWithTag(ProgressTags.ACCURACY).assertTextEquals("—")
    }

    @Test
    fun `the summary updates when the values change without a restart`() {
        var state by mutableStateOf(ProgressUiState(overall = overall(1, 1, 2, 1), loaded = true))
        composeRule.setContent { ProgressScreen(state) }

        composeRule.onNodeWithTag(ProgressTags.DUE_COUNT).assertTextEquals("1")

        state = ProgressUiState(overall = overall(4, 3, 4, 2), loaded = true)

        composeRule.onNodeWithTag(ProgressTags.DUE_COUNT).assertTextEquals("4")
        composeRule.onNodeWithTag(ProgressTags.STREAK).assertTextEquals("2")
    }
}
