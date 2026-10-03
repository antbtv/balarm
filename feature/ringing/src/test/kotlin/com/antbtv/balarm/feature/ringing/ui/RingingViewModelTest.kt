package com.antbtv.balarm.feature.ringing.ui

import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.feature.ringing.FakeRingingController
import com.antbtv.balarm.feature.ringing.ringing
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RingingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-10-03T06:30:30Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribedViewModel(controller: FakeRingingController): RingingViewModel {
        val viewModel = RingingViewModel(controller, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `ringing state shows label, current minute and snooze counter`() = runTest(dispatcher) {
        val viewModel = subscribedViewModel(FakeRingingController(ringing(label = "Gym", snoozesLeft = 2)))

        val state = viewModel.uiState.value
        assertThat(state.phase).isEqualTo(RingingPhase.RINGING)
        assertThat(state.label).isEqualTo("Gym")
        assertThat(state.snooze).isEqualTo(SnoozeUi.Limited(left = 2))
        assertThat(state.now).isEqualTo(LocalDateTime.of(2026, 10, 3, 6, 30))
    }

    @Test
    fun `initial value already reflects a ringing controller - no waiting flash`() = runTest(dispatcher) {
        val viewModel = RingingViewModel(FakeRingingController(ringing()), clock)

        assertThat(viewModel.uiState.value.phase).isEqualTo(RingingPhase.RINGING)
    }

    @Test
    fun `snooze is hidden when not allowed and has no counter when unlimited`() {
        assertThat(ringing(canSnooze = false, snoozesLeft = 3).toSnoozeUi()).isEqualTo(SnoozeUi.Hidden)
        assertThat(ringing(canSnooze = false, snoozesLeft = null).toSnoozeUi()).isEqualTo(SnoozeUi.Hidden)
        assertThat(ringing(canSnooze = true, snoozesLeft = 0).toSnoozeUi()).isEqualTo(SnoozeUi.Hidden)
        assertThat(ringing(canSnooze = true, snoozesLeft = null).toSnoozeUi()).isEqualTo(SnoozeUi.Unlimited)
        assertThat(ringing(canSnooze = true, snoozesLeft = 1).toSnoozeUi()).isEqualTo(SnoozeUi.Limited(1))
    }

    @Test
    fun `clock ticks on minute boundaries`() = runTest(dispatcher) {
        val viewModel = subscribedViewModel(FakeRingingController(ringing()))

        clock.advance(Duration.ofSeconds(30))
        advanceTimeBy(30.seconds + 1.milliseconds)

        assertThat(viewModel.uiState.value.now).isEqualTo(LocalDateTime.of(2026, 10, 3, 6, 31))
    }

    @Test
    fun `time zone change is picked up on the next minute`() = runTest(dispatcher) {
        val viewModel = subscribedViewModel(FakeRingingController(ringing()))

        clock.zoneId = ZoneId.of("Europe/Berlin")
        clock.advance(Duration.ofSeconds(30))
        advanceTimeBy(30.seconds + 1.milliseconds)

        assertThat(viewModel.uiState.value.now).isEqualTo(LocalDateTime.of(2026, 10, 3, 8, 31))
    }

    @Test
    fun `dismiss and snooze go to the controller`() = runTest(dispatcher) {
        val controller = FakeRingingController(ringing())
        val viewModel = subscribedViewModel(controller)

        viewModel.onEvent(RingingEvent.Snooze)
        viewModel.onEvent(RingingEvent.Dismiss)

        assertThat(controller.snoozeCalls).isEqualTo(1)
        assertThat(controller.dismissCalls).isEqualTo(1)
    }

    @Test
    fun `snooze is not sent when it is not allowed`() = runTest(dispatcher) {
        val controller = FakeRingingController(ringing(canSnooze = false))
        val viewModel = subscribedViewModel(controller)

        viewModel.onEvent(RingingEvent.Snooze)

        assertThat(controller.snoozeCalls).isEqualTo(0)
    }

    @Test
    fun `commands are ignored when nothing rings`() = runTest(dispatcher) {
        val controller = FakeRingingController(RingingState.Idle)
        val viewModel = subscribedViewModel(controller)

        viewModel.onEvent(RingingEvent.Dismiss)
        viewModel.onEvent(RingingEvent.Snooze)

        assertThat(controller.dismissCalls).isEqualTo(0)
        assertThat(controller.snoozeCalls).isEqualTo(0)
    }

    @Test
    fun `idle after ringing finishes the screen at once`() = runTest(dispatcher) {
        val controller = FakeRingingController(ringing())
        val viewModel = subscribedViewModel(controller)

        controller.state.value = RingingState.Idle
        runCurrent()

        assertThat(viewModel.uiState.value.phase).isEqualTo(RingingPhase.FINISHED)
    }

    @Test
    fun `idle before the first ringing waits for the service, then finishes`() = runTest(dispatcher) {
        val viewModel = subscribedViewModel(FakeRingingController(RingingState.Idle))

        assertThat(viewModel.uiState.value.phase).isEqualTo(RingingPhase.WAITING)
        advanceTimeBy(RingingViewModel.WAIT_FOR_RINGING - 1.milliseconds)
        assertThat(viewModel.uiState.value.phase).isEqualTo(RingingPhase.WAITING)
        advanceTimeBy(2.milliseconds)
        assertThat(viewModel.uiState.value.phase).isEqualTo(RingingPhase.FINISHED)
    }

    @Test
    fun `ringing published during the wait shows the alarm`() = runTest(dispatcher) {
        val controller = FakeRingingController(RingingState.Idle)
        val viewModel = subscribedViewModel(controller)

        advanceTimeBy(2.seconds)
        controller.state.value = ringing()
        advanceTimeBy(RingingViewModel.WAIT_FOR_RINGING)

        assertThat(viewModel.uiState.value.phase).isEqualTo(RingingPhase.RINGING)
    }
}
