package com.spaco.app.feature.session

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.domain.coach.CoachAgent
import com.spaco.app.core.domain.coach.CoachSelection
import com.spaco.app.core.domain.coach.CoachSelectionRequest
import com.spaco.app.core.domain.coach.ExerciseRequest
import com.spaco.app.core.domain.coach.ExplanationRequest
import com.spaco.app.core.domain.coach.GeneratedExercise
import com.spaco.app.core.domain.coach.GeneratedExplanation
import com.spaco.app.core.domain.model.Deck
import com.spaco.app.core.domain.model.Grade
import com.spaco.app.core.domain.model.GradedBy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-06-01T09:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var items: FakeItemRepository
    private lateinit var reviews: FakeReviewRepository
    private lateinit var sessions: FakeSessionRepository
    private lateinit var decks: FakeDeckRepository
    private lateinit var scheduler: FakeReviewScheduler
    private val deckId = "deck-1"

    private val ids = object : IdGenerator {
        private var counter = 0
        override fun newId(): String {
            counter += 1
            return "id-$counter"
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        items = FakeItemRepository(clock = { clock.instant() }, ids = ids)
        reviews = FakeReviewRepository()
        sessions = FakeSessionRepository()
        scheduler = FakeReviewScheduler()
        decks = FakeDeckRepository().apply {
            put(Deck(id = deckId, skillId = "skill-1", name = "Deck", createdAt = now))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun addItem(
        prompt: String = "prompt",
        answer: String = "answer",
        isFreeRecall: Boolean = false,
    ) = items.add(deckId, prompt, answer, null, isFreeRecall)

    private fun viewModel(coach: CoachAgent = NullCoachAgent()): SessionViewModel =
        SessionViewModel(
            itemRepository = items,
            reviewRepository = reviews,
            sessionRepository = sessions,
            deckRepository = decks,
            coach = coach,
            reviewScheduler = scheduler,
            clock = clock,
            ids = ids,
            savedStateHandle = SavedStateHandle(mapOf(SessionViewModel.DECK_ID_ARG to deckId)),
        )

    @Test
    fun `an empty deck reports an empty deck and starts nothing`() = runTest(testDispatcher.scheduler) {
        val state = viewModel().uiState.value

        assertThat(state.status).isEqualTo(SessionPhase.EMPTY_DECK)
        assertThat(sessions.getActiveForDeck(deckId)).isNull()
    }

    @Test
    fun `a deck with nothing due reports nothing due and starts nothing`() =
        runTest(testDispatcher.scheduler) {
            val item = addItem()
            items.reschedule(item.id, now.plusSeconds(86_400))

            val state = viewModel().uiState.value

            assertThat(state.status).isEqualTo(SessionPhase.NOTHING_DUE)
            assertThat(sessions.getActiveForDeck(deckId)).isNull()
        }

    @Test
    fun `a deck with due items starts a running session showing the first item`() =
        runTest(testDispatcher.scheduler) {
            addItem(prompt = "what is 2+2", answer = "4")

            val state = viewModel().uiState.value

            assertThat(state.status).isEqualTo(SessionPhase.RUNNING)
            assertThat(state.currentItem).isNotNull()
            assertThat(state.presentedPrompt).isEqualTo("what is 2+2")
            assertThat(state.plannedCount).isEqualTo(1)
        }

    @Test
    fun `a correct answer is graded, recorded and counted`() = runTest(testDispatcher.scheduler) {
        val item = addItem(answer = "4")
        val vm = viewModel()

        vm.submitAnswer("4")

        val state = vm.uiState.value
        assertThat(state.grade).isEqualTo(Grade.CORRECT)
        assertThat(state.answeredCount).isEqualTo(1)
        assertThat(state.correctCount).isEqualTo(1)
        val history = reviews.getForItem(item.id)
        assertThat(history).hasSize(1)
        assertThat(history.first().correct).isTrue()
        assertThat(history.first().gradedBy).isEqualTo(GradedBy.SYSTEM)
    }

    @Test
    fun `an incorrect answer is graded and reveals the expected answer`() =
        runTest(testDispatcher.scheduler) {
            addItem(answer = "4")
            val vm = viewModel()

            vm.submitAnswer("5")

            val state = vm.uiState.value
            assertThat(state.grade).isEqualTo(Grade.INCORRECT)
            assertThat(state.isFeedbackVisible).isTrue()
            assertThat(state.presentedExpectedAnswer).isEqualTo("4")
            assertThat(state.correctCount).isEqualTo(0)
        }

    @Test
    fun `an empty submission is rejected and nothing is recorded`() =
        runTest(testDispatcher.scheduler) {
            val item = addItem()
            val vm = viewModel()

            vm.submitAnswer("   ")

            val state = vm.uiState.value
            assertThat(state.grade).isNull()
            assertThat(state.submissionError).isNotNull()
            assertThat(reviews.getForItem(item.id)).isEmpty()
        }

    @Test
    fun `an item is never presented twice in one session`() = runTest(testDispatcher.scheduler) {
        addItem(prompt = "a", answer = "a")
        addItem(prompt = "b", answer = "b")
        val vm = viewModel()

        val first = vm.uiState.value.currentItem!!
        vm.submitAnswer(first.expectedAnswer)
        vm.advance()

        val second = vm.uiState.value.currentItem!!
        assertThat(second.id).isNotEqualTo(first.id)
    }

    @Test
    fun `completing the planned items ends with a summary`() = runTest(testDispatcher.scheduler) {
        addItem(answer = "a")
        val vm = viewModel()

        vm.submitAnswer("a")
        vm.advance()

        val state = vm.uiState.value
        assertThat(state.status).isEqualTo(SessionPhase.SUMMARY)
        assertThat(state.answeredCount).isEqualTo(1)
        assertThat(state.correctCount).isEqualTo(1)
        assertThat(state.endedEarly).isFalse()
    }

    @Test
    fun `a free-recall answer is revealed and self-graded`() = runTest(testDispatcher.scheduler) {
        val item = addItem(answer = "essay answer", isFreeRecall = true)
        val vm = viewModel()

        vm.submitAnswer("my attempt")
        assertThat(vm.uiState.value.awaitingSelfGrade).isTrue()
        assertThat(vm.uiState.value.grade).isNull()

        vm.selfGrade(correct = true)

        val state = vm.uiState.value
        assertThat(state.grade).isEqualTo(Grade.CORRECT)
        assertThat(reviews.getForItem(item.id).first().gradedBy).isEqualTo(GradedBy.LEARNER)
    }

    @Test
    fun `ending early shows a summary of the answered items only`() =
        runTest(testDispatcher.scheduler) {
            addItem(answer = "a")
            addItem(answer = "b")
            val vm = viewModel()

            vm.submitAnswer(vm.uiState.value.presentedExpectedAnswer)
            vm.endEarly()

            val state = vm.uiState.value
            assertThat(state.status).isEqualTo(SessionPhase.SUMMARY)
            assertThat(state.endedEarly).isTrue()
            assertThat(state.answeredCount).isEqualTo(1)
            assertThat(sessions.getActiveForDeck(deckId)).isNull()
        }

    @Test
    fun `an interrupted session can be resumed with earlier answers kept`() =
        runTest(testDispatcher.scheduler) {
            addItem(prompt = "a", answer = "a")
            addItem(prompt = "b", answer = "b")

            val first = viewModel()
            val answeredId = first.uiState.value.currentItem!!.id
            first.submitAnswer(first.uiState.value.presentedExpectedAnswer)

            val second = viewModel()
            assertThat(second.uiState.value.status).isEqualTo(SessionPhase.RESUMABLE)

            second.resume()
            val resumed = second.uiState.value
            assertThat(resumed.status).isEqualTo(SessionPhase.RUNNING)
            assertThat(resumed.answeredCount).isEqualTo(1)
            assertThat(resumed.currentItem!!.id).isNotEqualTo(answeredId)
        }

    @Test
    fun `discarding an interrupted session keeps recorded answers and shows a summary`() =
        runTest(testDispatcher.scheduler) {
            addItem(answer = "a")
            addItem(answer = "b")

            val first = viewModel()
            first.submitAnswer(first.uiState.value.presentedExpectedAnswer)

            val second = viewModel()
            second.discard()

            val state = second.uiState.value
            assertThat(state.status).isEqualTo(SessionPhase.SUMMARY)
            assertThat(state.answeredCount).isEqualTo(1)
            assertThat(sessions.getActiveForDeck(deckId)).isNull()
        }

    @Test
    fun `when the coach is unavailable the session still presents stored content`() =
        runTest(testDispatcher.scheduler) {
            addItem(prompt = "stored prompt", answer = "stored answer")

            val vm = viewModel(ThrowingCoachAgent())

            val state = vm.uiState.value
            assertThat(state.status).isEqualTo(SessionPhase.RUNNING)
            assertThat(state.presentedPrompt).isEqualTo("stored prompt")
            assertThat(state.presentedExpectedAnswer).isEqualTo("stored answer")
        }

    @Test
    fun `finishing a session re-registers the reminder checks`() = runTest(testDispatcher.scheduler) {
        addItem(answer = "a")
        val vm = viewModel()

        vm.submitAnswer("a")
        vm.advance()

        assertThat(scheduler.periodicScheduled).isTrue()
        assertThat(scheduler.nextDueChecks).isNotEmpty()
    }

    @Test
    fun `the grade is published while the explanation is still being written`() =
        runTest(testDispatcher.scheduler) {
            val item = addItem(prompt = "stored prompt", answer = "4")
            val coach = SuspendingExplanationCoach()
            val vm = viewModel(coach)

            vm.submitAnswer("4")

            assertThat(coach.explanationStarted).isTrue()
            val pending = vm.uiState.value
            assertThat(pending.grade).isEqualTo(Grade.CORRECT)
            assertThat(pending.presentedExplanation).isNull()
            assertThat(reviews.getForItem(item.id)).hasSize(1)

            coach.explanationText.complete("resolved later")
        }

    @Test
    fun `the explanation is merged once the coach resolves it`() =
        runTest(testDispatcher.scheduler) {
            addItem(prompt = "stored prompt", answer = "4")
            val coach = SuspendingExplanationCoach()
            val vm = viewModel(coach)

            vm.submitAnswer("4")
            assertThat(vm.uiState.value.presentedExplanation).isNull()

            coach.explanationText.complete("Because four is two plus two.")
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(vm.uiState.value.presentedExplanation)
                .isEqualTo("Because four is two plus two.")
        }

    @Test
    fun `a late explanation is not merged after the learner advances`() =
        runTest(testDispatcher.scheduler) {
            addItem(prompt = "first", answer = "1")
            addItem(prompt = "second", answer = "2")
            val coach = SuspendingExplanationCoach()
            val vm = viewModel(coach)

            val first = vm.uiState.value.currentItem!!
            vm.submitAnswer(first.expectedAnswer)
            assertThat(coach.explanationStarted).isTrue()

            vm.advance()
            val second = vm.uiState.value.currentItem!!
            assertThat(second.id).isNotEqualTo(first.id)
            assertThat(vm.uiState.value.presentedExplanation).isNull()

            coach.explanationText.complete("late explanation for the first item")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(state.currentItem!!.id).isEqualTo(second.id)
            assertThat(state.presentedExplanation).isNull()
        }

    private class NullCoachAgent : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? = null
        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null
        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? = null
    }

    /** A coach whose explanation call stays pending until the test resolves it. */
    private class SuspendingExplanationCoach : CoachAgent {
        val explanationText = CompletableDeferred<String>()
        var explanationStarted = false
            private set

        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? = null

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? = null

        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation {
            explanationStarted = true
            return GeneratedExplanation(
                targetItemId = request.item.id,
                text = explanationText.await(),
            )
        }
    }

    private class ThrowingCoachAgent : CoachAgent {
        override suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection? =
            error("coach down")

        override suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise? =
            error("coach down")

        override suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation? =
            error("coach down")
    }
}
