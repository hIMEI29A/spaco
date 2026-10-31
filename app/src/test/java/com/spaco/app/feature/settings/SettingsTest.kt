package com.spaco.app.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.data.work.ReviewNotifier
import com.spaco.app.core.domain.coach.CoachConnectionProbe
import com.spaco.app.core.domain.coach.CoachConnectionResult
import com.spaco.app.core.domain.model.CoachConfiguration
import com.spaco.app.core.domain.repository.CoachConfigurationStore
import com.spaco.app.core.domain.repository.ReminderPreferences
import com.spaco.app.feature.session.FakeReviewScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferences: FakeReminderPreferences
    private lateinit var scheduler: FakeReviewScheduler
    private lateinit var notifier: FakeNotifier
    private lateinit var store: FakeConfigurationStore
    private lateinit var probe: FakeConnectionProbe

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = FakeReminderPreferences()
        scheduler = FakeReviewScheduler()
        notifier = FakeNotifier()
        store = FakeConfigurationStore()
        probe = FakeConnectionProbe()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(preferences, scheduler, notifier, store, probe)

    @Test
    fun `turning reminders off cancels notifications and scheduling`() =
        runTest(testDispatcher.scheduler) {
            val vm = viewModel()

            vm.setRemindersEnabled(false)

            assertThat(scheduler.cancelled).isTrue()
            assertThat(notifier.cancelled).isTrue()
        }

    @Test
    fun `turning reminders back on restores scheduling`() = runTest(testDispatcher.scheduler) {
        val vm = viewModel()
        vm.setRemindersEnabled(false)

        vm.setRemindersEnabled(true)

        assertThat(scheduler.periodicScheduled).isTrue()
    }

    @Test
    fun `reminders enabled with permission denied are reported as blocked`() =
        runTest(testDispatcher.scheduler) {
            notifier.permissionGranted = false

            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            val state = vm.uiState.value

            assertThat(state.blockedBySystem).isTrue()
        }

    @Test
    fun `the settings screen shows the blocked-by-system state`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(remindersEnabled = true, permissionGranted = false, loaded = true),
            )
        }

        composeRule.onNodeWithTag(SettingsTags.BLOCKED).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTags.REQUEST_PERMISSION).assertIsDisplayed()
    }

    // --- coach configuration states --------------------------------------

    @Test
    fun `an endpoint and model saved without a credential is a configuration`() =
        runTest(testDispatcher.scheduler) {
            store.save(CoachConfiguration(endpoint = "http://127.0.0.1:11435", model = "local"))

            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            val state = vm.uiState.value

            assertThat(state.coachConfigured).isTrue()
            assertThat(state.coachCredentialSet).isFalse()
        }

    @Test
    fun `a saved credential is marked set but its value never enters the form`() =
        runTest(testDispatcher.scheduler) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            vm.onCoachEndpointChanged("https://api.example.test")
            vm.onCoachModelChanged("gpt-4o-mini")
            vm.onCoachCredentialChanged(CREDENTIAL)

            vm.saveCoachConfiguration()

            val state = vm.uiState.value
            assertThat(state.coachConfigured).isTrue()
            assertThat(state.coachCredentialSet).isTrue()
            assertThat(state.coachDraft.credential).isEmpty()
            assertThat(state.toString()).doesNotContain(CREDENTIAL)
        }

    @Test
    fun `clearing the configuration returns the app to the offline coach`() =
        runTest(testDispatcher.scheduler) {
            store.save(CoachConfiguration(endpoint = "https://api.example.test", model = "m"))
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.clearCoachConfiguration()

            val state = vm.uiState.value
            assertThat(state.coachConfigured).isFalse()
            assertThat(state.coachDraft).isEqualTo(CoachDraft())
        }

    @Test
    fun `saving without a credential deletes the stored credential`() =
        runTest(testDispatcher.scheduler) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            vm.onCoachEndpointChanged("https://api.example.test")
            vm.onCoachModelChanged("gpt-4o-mini")
            vm.onCoachCredentialChanged(CREDENTIAL)
            vm.saveCoachConfiguration()

            // Replace, then delete: endpoint stays, credential goes.
            vm.onCoachCredentialChanged("")
            vm.saveCoachConfiguration()

            val state = vm.uiState.value
            assertThat(state.coachConfigured).isTrue()
            assertThat(state.coachCredentialSet).isFalse()
            assertThat(store.saved?.credential).isNull()
        }

    @Test
    fun `an incomplete configuration is not saved`() = runTest(testDispatcher.scheduler) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onCoachModelChanged("gpt-4o-mini")

        vm.saveCoachConfiguration()

        assertThat(store.saved).isNull()
        assertThat(vm.uiState.value.coachConfigured).isFalse()
    }

    @Test
    fun `a rejected credential is reported without the credential value`() =
        runTest(testDispatcher.scheduler) {
            store.save(CoachConfiguration(endpoint = "https://api.example.test", model = "m"))
            probe.result = CoachConnectionResult.AUTHENTICATION_FAILED
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.checkCoachConnection()

            val message = vm.uiState.value.coachMessage
            assertThat(message).contains("rejected the credential")
            assertThat(message).doesNotContain(CREDENTIAL)
        }

    @Test
    fun `checking the connection with nothing saved asks to save first`() =
        runTest(testDispatcher.scheduler) {
            val vm = viewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.checkCoachConnection()

            assertThat(vm.uiState.value.coachMessage).contains("Save a configuration")
            assertThat(probe.checks).isEqualTo(0)
        }

    @Test
    fun `the coach form never shows a saved credential`() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    loaded = true,
                    coachConfigured = true,
                    coachCredentialSet = true,
                    coachDraft = CoachDraft(endpoint = "https://api.example.test", model = "gpt-4o-mini"),
                ),
            )
        }

        composeRule.onNodeWithTag(SettingsTags.COACH_CREDENTIAL_SET).assertExists()
        composeRule.onNodeWithText(CREDENTIAL).assertDoesNotExist()
    }

    private class FakeReminderPreferences : ReminderPreferences {
        val enabled = MutableStateFlow(true)
        override val remindersEnabled: Flow<Boolean> = enabled
        override suspend fun setRemindersEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }
    }

    private class FakeNotifier : ReviewNotifier {
        var permissionGranted = true
        var cancelled = false
        var channelEnsured = false
        override fun isPermissionGranted(): Boolean = permissionGranted
        override fun ensureChannel() {
            channelEnsured = true
        }

        override fun notifyDue(count: Int, deckId: String?) = Unit
        override fun cancel() {
            cancelled = true
        }
    }

    private class FakeConfigurationStore : CoachConfigurationStore {
        private val state = MutableStateFlow<CoachConfiguration?>(null)
        var saved: CoachConfiguration? = null
            private set

        override val configuration: Flow<CoachConfiguration?> = state

        override suspend fun save(configuration: CoachConfiguration) {
            saved = configuration
            state.value = configuration
        }

        override suspend fun clear() {
            saved = null
            state.value = null
        }
    }

    private class FakeConnectionProbe : CoachConnectionProbe {
        var result: CoachConnectionResult = CoachConnectionResult.SUCCESS
        var checks: Int = 0
            private set

        override suspend fun check(configuration: CoachConfiguration): CoachConnectionResult {
            checks += 1
            return result
        }
    }

    private companion object {
        const val CREDENTIAL = "test-bearer-value"
    }
}
