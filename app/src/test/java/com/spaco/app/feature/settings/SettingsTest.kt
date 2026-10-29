package com.spaco.app.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import com.spaco.app.core.data.work.ReviewNotifier
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = FakeReminderPreferences()
        scheduler = FakeReviewScheduler()
        notifier = FakeNotifier()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(preferences, scheduler, notifier)

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
}
