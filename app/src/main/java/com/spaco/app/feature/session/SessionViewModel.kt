package com.spaco.app.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.coach.CoachOutputValidator
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.GradedBy
import com.spaco.app.core.domain.model.PracticeItem
import com.spaco.app.core.domain.model.SessionState
import com.spaco.app.core.domain.model.SessionStatus
import com.spaco.app.core.domain.repository.DeckRepository
import com.spaco.app.core.domain.repository.ItemRepository
import com.spaco.app.core.domain.repository.ReviewRepository
import com.spaco.app.core.domain.repository.ReviewScheduler
import com.spaco.app.core.domain.repository.SessionRepository
import com.spaco.app.core.domain.scheduling.Scheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives one practice session: which item is shown, how an answer is graded and
 * recorded, and when the session ends.
 *
 * All state changes go through [mutate] under a mutex so a fast double-tap
 * cannot grade the same item twice, and every answer is recorded before the
 * next item is chosen — which is what makes an interrupted session resumable.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val reviewRepository: ReviewRepository,
    private val sessionRepository: SessionRepository,
    private val deckRepository: DeckRepository,
    private val coach: CoachAgent,
    private val reviewScheduler: ReviewScheduler,
    private val clock: Clock,
    private val ids: IdGenerator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle[DECK_ID_ARG]) {
        "SessionViewModel requires a $DECK_ID_ARG argument"
    }

    private val selector = SessionItemSelector(coach)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(SessionUiState(deckId = deckId))
    val uiState: StateFlow<SessionUiState> = _state.asStateFlow()

    private var planned: List<String> = emptyList()
    private var answered = mutableListOf<String>()
    private var correctCount = 0
    private var sessionId: String? = null
    private var startedAt = clock.instant()

    init {
        viewModelScope.launch { mutate { beginSession() } }
    }

    fun practiceAhead() {
        viewModelScope.launch {
            mutate {
                val items = itemRepository.getForDeck(deckId)
                val ordered = Scheduler.dueOrder(items, clock.instant())
                    .ifEmpty { items.sortedWith(compareBy({ it.schedule.nextDueAt }, { it.id })) }
                startNewSession(ordered.map { it.id })
            }
        }
    }

    fun resume() {
        viewModelScope.launch { mutate { presentNext() } }
    }

    fun discard() {
        viewModelScope.launch { mutate { finish(endedEarly = true) } }
    }

    fun submitAnswer(answer: String) {
        viewModelScope.launch {
            mutate {
                val item = _state.value.currentItem ?: return@mutate
                if (answer.isBlank()) {
                    _state.update { it.copy(submissionError = "Enter an answer before continuing.") }
                    return@mutate
                }
                if (item.isFreeRecall) {
                    // Free recall cannot be checked by the app: reveal the answer
                    // and let the learner grade their own attempt.
                    _state.update { it.copy(awaitingSelfGrade = true, submissionError = null) }
                    return@mutate
                }
                val correct = normalize(answer) == normalize(_state.value.presentedExpectedAnswer)
                recordGrade(correct, GradedBy.SYSTEM)
            }
        }
    }

    fun selfGrade(correct: Boolean) {
        viewModelScope.launch { mutate { recordGrade(correct, GradedBy.LEARNER) } }
    }

    fun advance() {
        viewModelScope.launch { mutate { presentNext() } }
    }

    fun endEarly() {
        viewModelScope.launch { mutate { finish(endedEarly = true) } }
    }

    // --- internals ----------------------------------------------------------

    private suspend fun mutate(block: suspend () -> Unit) = mutex.withLock { block() }

    private suspend fun beginSession() {
        val deckName = deckRepository.getById(deckId)?.name.orEmpty()
        val items = itemRepository.getForDeck(deckId)
        if (items.isEmpty()) {
            _state.update { it.copy(status = SessionPhase.EMPTY_DECK, deckName = deckName) }
            return
        }

        val existing = sessionRepository.getActiveForDeck(deckId)
        if (existing != null) {
            sessionId = existing.id
            planned = existing.plannedItemIds
            answered = existing.answeredItemIds.toMutableList()
            correctCount = existing.correctCount
            startedAt = existing.startedAt
            _state.update {
                it.copy(
                    status = SessionPhase.RESUMABLE,
                    deckName = deckName,
                    answeredCount = answered.size,
                    correctCount = correctCount,
                    plannedCount = planned.size,
                )
            }
            return
        }

        val due = Scheduler.dueOrder(items, clock.instant())
        if (due.isEmpty()) {
            _state.update { it.copy(status = SessionPhase.NOTHING_DUE, deckName = deckName) }
            return
        }

        _state.update { it.copy(deckName = deckName) }
        startNewSession(due.take(ITEM_BUDGET).map { it.id })
    }

    private suspend fun startNewSession(itemIds: List<String>) {
        if (itemIds.isEmpty()) {
            _state.update { it.copy(status = SessionPhase.EMPTY_DECK) }
            return
        }
        sessionId = ids.newId()
        startedAt = clock.instant()
        planned = itemIds.take(ITEM_BUDGET)
        answered = mutableListOf()
        correctCount = 0
        sessionRepository.save(currentSession(SessionStatus.ACTIVE))
        presentNext()
    }

    private suspend fun presentNext() {
        val items = itemRepository.getForDeck(deckId)
        val byId = items.associateBy { it.id }
        val remaining = planned.filterNot { it in answered }.mapNotNull { byId[it] }

        if (remaining.isEmpty()) {
            finish(endedEarly = false)
            return
        }

        val now = clock.instant()
        val recent = reviewRepository.getInWindow(deckId, now.minus(RECENT_WINDOW))
        val next = selector.selectNext(deckId, remaining, recent, now) ?: remaining.first()
        val exercise = validatedExercise(next, items)

        _state.update {
            it.copy(
                status = SessionPhase.RUNNING,
                currentItem = next,
                presentedPrompt = exercise?.prompt ?: next.prompt,
                presentedExpectedAnswer = exercise?.expectedAnswer ?: next.expectedAnswer,
                presentedExplanation = exercise?.explanation ?: next.explanation,
                grade = null,
                awaitingSelfGrade = false,
                submissionError = null,
                plannedCount = planned.size,
            )
        }
    }

    private suspend fun recordGrade(correct: Boolean, gradedBy: GradedBy) {
        val item = _state.value.currentItem ?: return
        val grade = if (correct) Grade.CORRECT else Grade.INCORRECT

        // Record first: the answer is durable before anything else happens.
        reviewRepository.recordReview(item.id, correct, gradedBy, clock.instant())
        if (item.id !in answered) {
            answered += item.id
            if (correct) correctCount += 1
        }
        sessionId?.let { sessionRepository.save(currentSession(SessionStatus.ACTIVE)) }

        val explanation = validatedExplanation(item, grade)

        _state.update {
            it.copy(
                grade = grade,
                awaitingSelfGrade = false,
                submissionError = null,
                answeredCount = answered.size,
                correctCount = correctCount,
                presentedExplanation = explanation ?: item.explanation,
            )
        }
    }

    private suspend fun finish(endedEarly: Boolean) {
        sessionId?.let { sessionRepository.save(currentSession(SessionStatus.ENDED)) }
        // The learner just changed what is due: re-register the background
        // checks so the next reminder is accurate.
        reviewScheduler.schedulePeriodic()
        reviewScheduler.scheduleNextDueCheck(
            itemRepository.getEarliestNextDueAt()?.takeIf { it.isAfter(clock.instant()) },
        )
        _state.update {
            it.copy(
                status = SessionPhase.SUMMARY,
                endedEarly = endedEarly,
                currentItem = null,
                grade = null,
                awaitingSelfGrade = false,
                answeredCount = answered.size,
                correctCount = correctCount,
            )
        }
    }

    private fun currentSession(status: SessionStatus) = SessionState(
        id = sessionId ?: ids.newId(),
        deckId = deckId,
        startedAt = startedAt,
        itemBudget = planned.size,
        plannedItemIds = planned,
        answeredItemIds = answered.toList(),
        correctCount = correctCount,
        endedAt = if (status == SessionStatus.ENDED) clock.instant() else null,
        status = status,
    )

    private suspend fun validatedExercise(item: PracticeItem, siblings: List<PracticeItem>) = runCatching {
        val request = ExerciseRequest(
            item = item,
            siblings = siblings,
            seed = item.id.hashCode().toLong() * 31 + answered.size,
        )
        CoachOutputValidator.exercise(coach.generateExercise(request), request)
    }.getOrNull()

    private suspend fun validatedExplanation(item: PracticeItem, grade: Grade): String? = runCatching {
        val request = ExplanationRequest(item = item, grade = grade)
        CoachOutputValidator.explanation(coach.generateExplanation(request), request)?.text
    }.getOrNull()

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(WHITESPACE, " ")

    companion object {
        const val DECK_ID_ARG = "deckId"
        const val ITEM_BUDGET = 10
        private val RECENT_WINDOW: Duration = Duration.ofDays(7)
        private val WHITESPACE = Regex("\\s+")
    }
}
