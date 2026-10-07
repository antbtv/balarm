package com.antbtv.balarm.feature.settings

import app.cash.turbine.test
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.domain.testing.FakeSetupStateRepository
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val checker = FakePermissionHealthChecker()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(checker, FakeSetupStateRepository(), FakeAlarmRepository())

    @Test
    fun `healthy device has no problems, unconfirmed oem is not a problem`() = runTest(dispatcher) {
        viewModel().uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertThat(state.loading).isFalse()
            assertThat(state.problems).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unscheduled alarm is a problem`() = runTest(dispatcher) {
        val repository = FakeAlarmRepository()
        val id = repository.save(Alarm(time = LocalTime.of(6, 30)))
        repository.updateRuntime(AlarmRuntimeState(id, scheduleFailed = true))

        SettingsViewModel(checker, FakeSetupStateRepository(), repository).uiState.test {
            skipItems(1)
            assertThat(awaitItem().problems).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `problems are counted after resume`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            assertThat(awaitItem().problems).isEqualTo(0)

            checker.current = HEALTHY_SNAPSHOT.copy(overlay = false, notificationsEnabled = false)
            vm.onResumed()

            assertThat(awaitItem().problems).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
