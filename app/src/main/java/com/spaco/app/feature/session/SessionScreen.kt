package com.spaco.app.feature.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.ui.animation.rememberAnswerRevealRotation
import com.spaco.app.core.ui.animation.rememberPulseScale

object SessionTags {
    const val PROMPT = "session-prompt"
    const val ANSWER_FIELD = "session-answer"
    const val SUBMIT = "session-submit"
    const val CONTINUE = "session-continue"
    const val FEEDBACK = "session-feedback"
    const val EXPECTED_ANSWER = "session-expected-answer"
    const val EXPLANATION = "session-explanation"
    const val SELF_GRADE_CORRECT = "session-self-correct"
    const val SELF_GRADE_INCORRECT = "session-self-incorrect"
    const val END_EARLY = "session-end"
    const val NOTHING_DUE = "session-nothing-due"
    const val PRACTICE_AHEAD = "session-practice-ahead"
    const val EMPTY_DECK = "session-empty-deck"
    const val RESUMABLE = "session-resumable"
    const val RESUME = "session-resume"
    const val DISCARD = "session-discard"
    const val SUMMARY = "session-summary"
    const val SUMMARY_ANSWERED = "session-summary-answered"
    const val SUMMARY_CORRECT = "session-summary-correct"
    const val DONE = "session-done"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onPracticeAhead: () -> Unit = {},
    onResume: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onSubmitAnswer: (String) -> Unit = {},
    onSelfGrade: (Boolean) -> Unit = {},
    onAdvance: () -> Unit = {},
    onEndEarly: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.deckName.ifBlank { "Practice" }) },
                actions = {
                    if (state.status == SessionPhase.RUNNING) {
                        TextButton(
                            modifier = Modifier.testTag(SessionTags.END_EARLY),
                            onClick = onEndEarly,
                        ) { Text("End") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when (state.status) {
                SessionPhase.LOADING -> Text("Loading…")
                SessionPhase.NOTHING_DUE -> Message(
                    tag = SessionTags.NOTHING_DUE,
                    text = "Nothing is due right now.",
                    actionLabel = "Practice ahead anyway",
                    actionTag = SessionTags.PRACTICE_AHEAD,
                    onAction = onPracticeAhead,
                )
                SessionPhase.EMPTY_DECK -> Message(
                    tag = SessionTags.EMPTY_DECK,
                    text = "This deck has no items yet.",
                    actionLabel = "Back to decks",
                    actionTag = SessionTags.DONE,
                    onAction = onFinished,
                )
                SessionPhase.RESUMABLE -> Message(
                    tag = SessionTags.RESUMABLE,
                    text = "You have an interrupted session.",
                    actionLabel = "Resume",
                    actionTag = SessionTags.RESUME,
                    onAction = onResume,
                    secondaryLabel = "Discard",
                    secondaryTag = SessionTags.DISCARD,
                    onSecondary = onDiscard,
                )
                SessionPhase.RUNNING -> RunningSession(
                    state = state,
                    onSubmitAnswer = onSubmitAnswer,
                    onSelfGrade = onSelfGrade,
                    onAdvance = onAdvance,
                )
                SessionPhase.SUMMARY -> Summary(
                    state = state,
                    onDone = onFinished,
                )
            }
        }
    }
}

@Suppress("UnusedContentLambdaTargetStateParameter")
@Composable
private fun RunningSession(
    state: SessionUiState,
    onSubmitAnswer: (String) -> Unit,
    onSelfGrade: (Boolean) -> Unit,
    onAdvance: () -> Unit,
) {
    Text(
        text = "Item ${state.position} of ${state.plannedCount}",
        style = MaterialTheme.typography.labelLarge,
    )

    // The item transition is presentation only: the controls below are driven by
    // state, never gated on this animation finishing.
    AnimatedContent(
        targetState = state.currentItem?.id,
        transitionSpec = {
            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { -it / 4 } + fadeOut())
        },
        label = "item-transition",
    ) { _ ->
        Column {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.presentedPrompt,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(SessionTags.PROMPT),
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    var answer by rememberSaveable(state.currentItem?.id) { mutableStateOf("") }
    val answered = state.isFeedbackVisible

    if (!answered) {
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Your answer") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SessionTags.ANSWER_FIELD),
        )
        state.submissionError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag(SessionTags.SUBMIT),
            onClick = { onSubmitAnswer(answer) },
        ) { Text("Submit") }
    }

    if (state.awaitingSelfGrade) {
        SelfGradeControls(expected = state.presentedExpectedAnswer, onSelfGrade = onSelfGrade)
    }

    if (state.grade != null) {
        FeedbackCard(state = state, onAdvance = onAdvance)
    }
}

@Composable
private fun SelfGradeControls(expected: String, onSelfGrade: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Expected answer")
            Text(
                text = expected,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(SessionTags.EXPECTED_ANSWER),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.testTag(SessionTags.SELF_GRADE_CORRECT),
                    onClick = { onSelfGrade(true) },
                ) { Text("I was right") }
                OutlinedButton(
                    modifier = Modifier.testTag(SessionTags.SELF_GRADE_INCORRECT),
                    onClick = { onSelfGrade(false) },
                ) { Text("I was wrong") }
            }
        }
    }
}

@Composable
private fun FeedbackCard(state: SessionUiState, onAdvance: () -> Unit) {
    val rotation = rememberAnswerRevealRotation(visible = true)
    val pulse = rememberPulseScale(key = state.grade)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .graphicsLayer { rotationY = rotation.value }
            .testTag(SessionTags.FEEDBACK),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (state.grade == Grade.CORRECT) "Correct" else "Not quite",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
            Spacer(Modifier.height(8.dp))
            Text("Expected answer")
            Text(
                text = state.presentedExpectedAnswer,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(SessionTags.EXPECTED_ANSWER),
            )
            state.presentedExplanation?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(SessionTags.EXPLANATION),
                )
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag(SessionTags.CONTINUE),
                onClick = onAdvance,
            ) { Text("Continue") }
        }
    }
}

@Composable
private fun Summary(state: SessionUiState, onDone: () -> Unit) {
    Column(modifier = Modifier.testTag(SessionTags.SUMMARY)) {
        Text("Session complete", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Answered: ${state.answeredCount}",
            modifier = Modifier.testTag(SessionTags.SUMMARY_ANSWERED),
        )
        Text(
            text = "Correct: ${state.correctCount}",
            modifier = Modifier.testTag(SessionTags.SUMMARY_CORRECT),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.testTag(SessionTags.DONE),
            onClick = onDone,
        ) { Text("Done") }
    }
}

@Composable
private fun Message(
    tag: String,
    text: String,
    actionLabel: String,
    actionTag: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    secondaryTag: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.testTag(tag)) {
        Text(text)
        Spacer(Modifier.height(12.dp))
        Button(modifier = Modifier.testTag(actionTag), onClick = onAction) { Text(actionLabel) }
        if (secondaryLabel != null && secondaryTag != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(modifier = Modifier.testTag(secondaryTag), onClick = onSecondary) {
                Text(secondaryLabel)
            }
        }
    }
}
