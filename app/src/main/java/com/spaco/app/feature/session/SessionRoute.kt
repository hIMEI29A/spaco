package com.spaco.app.feature.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SessionRoute(
    onFinished: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SessionScreen(
        state = state,
        onPracticeAhead = viewModel::practiceAhead,
        onResume = viewModel::resume,
        onDiscard = viewModel::discard,
        onSubmitAnswer = viewModel::submitAnswer,
        onSelfGrade = viewModel::selfGrade,
        onAdvance = viewModel::advance,
        onEndEarly = viewModel::endEarly,
        onFinished = onFinished,
    )
}
