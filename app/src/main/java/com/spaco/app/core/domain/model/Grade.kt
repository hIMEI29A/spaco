package com.spaco.app.core.domain.model

/**
 * How an answer was judged: by the app comparing the answer with the expected
 * one, or by the learner grading their own free-recall attempt.
 */
enum class GradedBy {
    SYSTEM,
    LEARNER,
}

/**
 * The result of grading one answer. This is the only input the scheduler needs
 * besides the item's prior state and the moment the answer was given.
 */
enum class Grade {
    CORRECT,
    INCORRECT,
}
