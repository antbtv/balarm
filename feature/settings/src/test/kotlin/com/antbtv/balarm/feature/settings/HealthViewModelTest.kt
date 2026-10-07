package com.antbtv.balarm.feature.settings

import app.cash.turbine.test
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.AlarmWithRuntime
import com.antbtv.balarm.core.domain.alarm.InMemoryTestAlarmStore
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.domain.testing.FakeSetupStateRepository
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:00:20").atZone(moscow).toInstant(), moscow)
    private val checker = FakePermissionHealthChecker()
    private val setup = FakeSetupStateRepository()
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val testRunner = TestAlarmRunner(scheduler, InMemoryTestAlarmStore(), clock, log)
    private val engine = AlarmEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log, testRunner)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HealthViewModel(checker, setup, repository, engine, testRunner, clock)

    private fun HealthUiState.status(item: HealthItem) = items.first { it.item == item }.status

    @Test
    fun `state shows every item in order`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uiState.test {
            skipItems(1) // initial loading
            val state = awaitItem()
            assertThat(state.loading).isFalse()
            assertThat(state.items.map { it.item }).containsExactlyElementsIn(HealthItem.entries).inOrder()
            assertThat(state.status(HealthItem.OEM_BACKGROUND)).isEqualTo(HealthStatus.UNCONFIRMED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume rereads the platform and the status follows`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            assertThat(awaitItem().status(HealthItem.OVERLAY)).isEqualTo(HealthStatus.OK)

            checker.current = HEALTHY_SNAPSHOT.copy(overlay = false)
            vm.onEvent(HealthEvent.Resumed)

            assertThat(awaitItem().status(HealthItem.OVERLAY)).isEqualTo(HealthStatus.PROBLEM)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fix of a permission item asks the screen to open the system settings`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(HealthEvent.Fix(HealthItem.FULL_SCREEN_INTENT))
            assertThat(awaitItem()).isEqualTo(HealthEffect.OpenFix(HealthItem.FULL_SCREEN_INTENT))
        }
    }

    @Test
    fun `fix of scheduling retries instead of opening settings`() = runTest(dispatcher) {
        scheduler.accept = false
        val id = engine.save(Alarm(time = LocalTime.of(6, 30))).id
        scheduler.accept = true
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value.unscheduledAlarms).isEqualTo(1)
        assertThat(vm.uiState.value.status(HealthItem.SCHEDULING)).isEqualTo(HealthStatus.PROBLEM)

        vm.onEvent(HealthEvent.Fix(HealthItem.SCHEDULING))
        runCurrent()

        assertThat(repository.runtimes[id]?.scheduleFailed).isFalse()
        assertThat(vm.uiState.value.unscheduledAlarms).isEqualTo(0)
        assertThat(vm.uiState.value.status(HealthItem.SCHEDULING)).isEqualTo(HealthStatus.OK)
        assertThat(vm.uiState.value.retrying).isFalse()
        job.cancel()
    }

    @Test
    fun `retry failure is reported and unlocks the button`() = runTest(dispatcher) {
        repository.failOnLoad = IOException("db")
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        vm.effects.test {
            vm.onEvent(HealthEvent.RetryScheduling)
            runCurrent()
            assertThat(awaitItem()).isEqualTo(HealthEffect.RetryFailed)
        }
        assertThat(vm.uiState.value.retrying).isFalse()
        job.cancel()
    }

    @Test
    fun `a second tap during retry does not start another pass`() = runTest(dispatcher) {
        engine.save(Alarm(time = LocalTime.of(6, 30)))
        val calls = scheduler.scheduleCalls
        val vm = viewModel()

        vm.onEvent(HealthEvent.RetryScheduling)
        vm.onEvent(HealthEvent.RetryScheduling)
        runCurrent()

        assertThat(scheduler.scheduleCalls - calls).isEqualTo(1)
    }

    @Test
    fun `storage failure of the alarm stream does not break the screen`() = runTest(dispatcher) {
        val broken = object : AlarmRepository by repository {
            override fun observeAlarmsWithRuntime(): Flow<List<AlarmWithRuntime>> = flow { throw IOException("db") }
        }
        val vm = HealthViewModel(checker, setup, broken, engine, testRunner, clock)

        vm.uiState.test {
            skipItems(1)
            assertThat(awaitItem().loading).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test alarm is scheduled one minute ahead and never stored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(HealthEvent.ScheduleTest)
            assertThat(awaitItem()).isEqualTo(HealthEffect.TestScheduled(clock.now + Duration.ofMinutes(1)))
        }
        assertThat(scheduler.scheduled).containsKey(AlarmId.TEST)
        assertThat(repository.loadAll()).isEmpty()
    }

    @Test
    fun `refused test alarm reports null`() = runTest(dispatcher) {
        scheduler.accept = false
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(HealthEvent.ScheduleTest)
            assertThat(awaitItem()).isEqualTo(HealthEffect.TestScheduled(null))
        }
    }

    @Test
    fun `oem confirmation is stored and shown`() = runTest(dispatcher) {
        val vm = viewModel()
        val job = launch { vm.uiState.collect {} }
        runCurrent()

        vm.onEvent(HealthEvent.SetOemConfirmed(true))
        runCurrent()

        assertThat(vm.uiState.value.status(HealthItem.OEM_BACKGROUND)).isEqualTo(HealthStatus.OK)
        job.cancel()
    }

    @Test
    fun `storage failure on oem confirmation does not crash`() = runTest(dispatcher) {
        setup.failOnWrite = IOException("disk")
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(HealthEvent.SetOemConfirmed(true))
            assertThat(awaitItem()).isEqualTo(HealthEffect.SaveFailed)
        }
    }

    @Test
    fun `an alarm that failed to schedule counts as unscheduled`() = runTest(dispatcher) {
        scheduler.accept = false
        engine.save(Alarm(time = LocalTime.of(6, 30)))
        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            assertThat(awaitItem().unscheduledAlarms).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.runtimes.values.map(AlarmRuntimeState::scheduleFailed)).containsExactly(true)
        assertThat(log.events.map { it.name }).contains("SCHEDULE_FAILED")
    }
}
