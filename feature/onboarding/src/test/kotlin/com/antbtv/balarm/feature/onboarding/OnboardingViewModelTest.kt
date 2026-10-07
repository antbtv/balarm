package com.antbtv.balarm.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.domain.testing.FakeSetupStateRepository
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val checker = FakePermissionHealthChecker(
        HEALTHY_SNAPSHOT.copy(notificationsEnabled = false, overlay = false),
    )
    private val setup = FakeSetupStateRepository()
    private val repository = FakeAlarmRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        OnboardingViewModel(checker, setup, repository, saved)

    private fun TestScope.started(saved: SavedStateHandle = SavedStateHandle()): OnboardingViewModel =
        viewModel(saved).also { runCurrent() }

    @Test
    fun `starts at the first unmet step`() = runTest(dispatcher) {
        val state = started().uiState.value

        assertThat(state.loading).isFalse()
        assertThat(state.step).isEqualTo(OnboardingStep.NOTIFICATIONS)
        assertThat(state.fixItem).isEqualTo(HealthItem.NOTIFICATIONS)
        assertThat(state.stepNumber).isEqualTo(1)
        assertThat(state.canPostpone).isFalse()
    }

    @Test
    fun `critical step cannot be postponed before an attempt, afterwards it warns`() = runTest(dispatcher) {
        val vm = started()
        vm.onEvent(OnboardingEvent.Postpone)
        runCurrent()
        assertThat(vm.uiState.value.step).isEqualTo(OnboardingStep.NOTIFICATIONS)

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Primary)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.OpenFix(HealthItem.NOTIFICATIONS))
        }
        assertThat(vm.uiState.value.canPostpone).isTrue()
        assertThat(vm.uiState.value.warnOnSkip).isTrue()

        vm.onEvent(OnboardingEvent.Postpone)
        runCurrent()
        assertThat(vm.uiState.value.step).isEqualTo(OnboardingStep.OVERLAY)
        assertThat(vm.uiState.value.warnOnSkip).isFalse()
    }

    @Test
    fun `granting a permission in settings moves on after resume`() = runTest(dispatcher) {
        val vm = started()

        checker.current = checker.current.copy(notificationsEnabled = true)
        vm.onEvent(OnboardingEvent.Resumed)
        runCurrent()

        assertThat(vm.uiState.value.step).isEqualTo(OnboardingStep.OVERLAY)
    }

    @Test
    fun `progress survives process death - state is rebuilt from statuses and saved sets`() = runTest(dispatcher) {
        val saved =
            SavedStateHandle(mapOf("skipped" to listOf("NOTIFICATIONS"), "attempted" to listOf("NOTIFICATIONS")))

        val state = started(saved).uiState.value

        assertThat(state.step).isEqualTo(OnboardingStep.OVERLAY)
    }

    @Test
    fun `oem confirmation completes onboarding`() = runTest(dispatcher) {
        checker.current = HEALTHY_SNAPSHOT
        val vm = started()
        assertThat(vm.uiState.value.step).isEqualTo(OnboardingStep.OEM_BACKGROUND)

        vm.effects.test {
            vm.onEvent(OnboardingEvent.OemConfirmed(true))
            runCurrent()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
        assertThat(setup.state.first().onboardingCompleted).isTrue()
        assertThat(setup.state.first().oemBackgroundConfirmed).isTrue()
    }

    @Test
    fun `postponing the last step finishes onboarding`() = runTest(dispatcher) {
        checker.current = HEALTHY_SNAPSHOT
        val vm = started()

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Postpone)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
        assertThat(setup.state.first().onboardingCompleted).isTrue()
    }

    @Test
    fun `storage failure still lets the user in`() = runTest(dispatcher) {
        checker.current = HEALTHY_SNAPSHOT
        setup.failOnWrite = IOException("disk")
        val vm = started()

        vm.effects.test {
            vm.onEvent(OnboardingEvent.Postpone)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.SaveFailed)
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
        }
    }

    @Test
    fun `unknown names in the saved state are ignored`() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("skipped" to listOf("GONE", "OVERLAY"), "attempted" to listOf("???")))

        assertThat(started(saved).uiState.value.step).isEqualTo(OnboardingStep.NOTIFICATIONS)
    }

    @Test
    fun `without a grant the step stays after returning from settings`() = runTest(dispatcher) {
        val vm = started()
        vm.onEvent(OnboardingEvent.Primary)
        vm.onEvent(OnboardingEvent.Resumed)
        runCurrent()

        assertThat(vm.uiState.value.step).isEqualTo(OnboardingStep.NOTIFICATIONS)
        assertThat(vm.uiState.value.canPostpone).isTrue()
    }

    @Test
    fun `finish is reported exactly once`() = runTest(dispatcher) {
        checker.current = HEALTHY_SNAPSHOT
        val vm = started()

        vm.effects.test {
            vm.onEvent(OnboardingEvent.OemConfirmed(true))
            runCurrent()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.Finished)
            vm.onEvent(OnboardingEvent.Resumed)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `storage failure on oem confirmation is reported`() = runTest(dispatcher) {
        checker.current = HEALTHY_SNAPSHOT
        val vm = started()
        setup.failOnWrite = IOException("disk")

        vm.effects.test {
            vm.onEvent(OnboardingEvent.OemConfirmed(true))
            runCurrent()
            assertThat(awaitItem()).isEqualTo(OnboardingEffect.SaveFailed)
        }
    }

    @Test
    fun `restricted background mode shows the battery step as restricted`() = runTest(dispatcher) {
        checker.current = HEALTHY_SNAPSHOT.copy(backgroundRestricted = true)

        val state = started().uiState.value

        assertThat(state.step).isEqualTo(OnboardingStep.BATTERY)
        assertThat(state.batteryRestricted).isTrue()
        assertThat(state.fixItem).isEqualTo(HealthItem.BACKGROUND_RESTRICTION)
        assertThat(state.canPostpone).isFalse()
    }
}
