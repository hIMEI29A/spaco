package com.spaco.app.feature.session

import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.PracticeItem

enum class SessionPhase {
    /** Loading the deck and deciding how the session begins. */
    LOADING,

    /** Every item is scheduled in the future; the learner may practise ahead. */
    NOTHING_DUE,

    /** The deck has no items at all. */
    EMPTY_DECK,

    /** An interrupted session exists and can be resumed or discarded. */
    RESUMABLE,

    /** An item is on screen and can be answered. */
    RUNNING,

    /** The session has ended and its results are shown. */
    SUMMARY,
}

/**
 * The session's presentation state. Feedback is derived from [grade]: `null`
 * means the item is still a question; a non-null value means feedback is shown
 * and the learner may continue.
 */
data class SessionUiState(
    val status: SessionPhase = SessionPhase.LOADING,
    val deckId: String = "",
    val deckName: String = "",
    val currentItem: PracticeItem? = null,
    val presentedPrompt: String = "",
    val presentedExpectedAnswer: String = "",
    val presentedExplanation: String? = null,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val plannedCount: Int = 0,
    val grade: Grade? = null,
    /** True once a free-recall item's answer has been revealed for self-grading. */
    val awaitingSelfGrade: Boolean = false,
    val submissionError: String? = null,
    val endedEarly: Boolean = false,
) {
    val isFeedbackVisible: Boolean get() = grade != null || awaitingSelfGrade

    /** 1-based position of the current item within the planned budget. */
    val position: Int get() = (answeredCount + if (currentItem != null && grade == null) 1 else 0)
}
