package com.antbtv.balarm.feature.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.antbtv.balarm.core.domain.settings.SettingsPolicy
import com.antbtv.balarm.core.domain.settings.SettingsRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.domain.testing.FakeSettingsRepository
import com.antbtv.balarm.core.domain.testing.FakeSetupStateRepository
import com.antbtv.balarm.core.domain.testing.HEALTHY_SNAPSHOT
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.AutoStop
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

    private fun viewModel(
        customSounds: Boolean = false,
        general: Boolean = false,
        settings: SettingsRepository = FakeSettingsRepository(),
    ) = SettingsViewModel(
        checker,
        FakeSetupStateRepository(),
        FakeAlarmRepository(),
        flags(customSounds, general),
        settings,
    )

    private fun flags(customSounds: Boolean, general: Boolean = false) = FeatureFlagProvider {
        when (it) {
            Feature.CUSTOM_SOUNDS -> customSounds
            Feature.GLOBAL_SETTINGS -> general
            else -> it.defaultEnabled
        }
    }

    /** Ждёт состояние, удовлетворяющее [predicate] (промежуточные состояния комбинирования пропускаются). */
    private suspend fun ReceiveTurbine<SettingsUiState>.awaitState(
        predicate: (SettingsUiState) -> Boolean,
    ): SettingsUiState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

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

        SettingsViewModel(
            checker,
            FakeSetupStateRepository(),
            repository,
            flags(false),
            FakeSettingsRepository(),
        ).uiState.test {
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

    @Test
    fun `ringtones row follows the custom sounds flag from the first state`() = runTest(dispatcher) {
        viewModel(customSounds = true).uiState.test {
            assertThat(awaitItem().soundsVisible).isTrue()
            assertThat(awaitItem().soundsVisible).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        viewModel(customSounds = false).uiState.test {
            assertThat(awaitItem().soundsVisible).isFalse()
            assertThat(awaitItem().soundsVisible).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `general section is absent and settings are not read when the flag is off`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository().apply { hang = true }
        val vm = viewModel(general = false, settings = settings)
        vm.uiState.test {
            val state = awaitState { !it.loading }
            assertThat(state.generalVisible).isFalse()
            assertThat(state.general).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `events do not write settings when the flag is off`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(general = false, settings = settings)

        vm.onEvent(SettingsEvent.AutoStopChanged(AutoStop.NEVER))
        advanceUntilIdle()

        assertThat(settings.settings.first()).isEqualTo(SettingsPolicy.DEFAULTS)
    }

    @Test
    fun `general section is visible from the first state and shows stored values`() = runTest(dispatcher) {
        val stored = AppSettings(
            autoStop = AutoStop.MIN_10,
            missionIdleTimeout = Duration.ofSeconds(40),
            missionDuckPercent = 20,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )
        viewModel(general = true, settings = FakeSettingsRepository(stored)).uiState.test {
            val first = awaitItem()
            assertThat(first.generalVisible).isTrue()
            assertThat(first.general).isNull()

            val loaded = awaitState { it.general != null }
            assertThat(loaded.general).isEqualTo(
                GeneralSettingsUi(
                    autoStop = AutoStop.MIN_10,
                    missionIdleSeconds = 40,
                    missionDuckPercent = 20,
                    firstDayOfWeek = DayOfWeek.SUNDAY,
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each choice is saved to the repository and shown`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(general = true, settings = settings)
        vm.uiState.test {
            awaitState { it.general != null }

            vm.onEvent(SettingsEvent.AutoStopChanged(AutoStop.NEVER))
            assertThat(awaitState { it.general?.autoStop == AutoStop.NEVER }.general?.autoStop)
                .isEqualTo(AutoStop.NEVER)

            vm.onEvent(SettingsEvent.MissionIdleChanged(seconds = 60))
            awaitState { it.general?.missionIdleSeconds == 60L }

            vm.onEvent(SettingsEvent.MissionDuckChanged(percent = 50))
            awaitState { it.general?.missionDuckPercent == 50 }

            vm.onEvent(SettingsEvent.FirstDayOfWeekChanged(DayOfWeek.SATURDAY))
            awaitState { it.general?.firstDayOfWeek == DayOfWeek.SATURDAY }

            vm.onEvent(SettingsEvent.FirstDayOfWeekChanged(null))
            awaitState { it.general != null && it.general?.firstDayOfWeek == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(settings.settings.first()).isEqualTo(
            AppSettings(
                autoStop = AutoStop.NEVER,
                missionIdleTimeout = Duration.ofSeconds(60),
                missionDuckPercent = 50,
                firstDayOfWeek = null,
            ),
        )
    }

    @Test
    fun `failed save shows an error once and keeps the stored value`() = runTest(dispatcher) {
        val failing = object : SettingsRepository {
            override val settings = flowOf(SettingsPolicy.DEFAULTS)
            override suspend fun update(transform: (AppSettings) -> AppSettings) = throw IOException("disk full")
        }
        val vm = viewModel(general = true, settings = failing)
        vm.uiState.test {
            awaitState { it.general != null }

            vm.onEvent(SettingsEvent.AutoStopChanged(AutoStop.MIN_5))
            val failed = awaitState { it.saveFailed }
            assertThat(failed.general?.autoStop).isEqualTo(SettingsPolicy.DEFAULTS.autoStop)

            vm.onEvent(SettingsEvent.SaveFailedShown)
            assertThat(awaitState { !it.saveFailed }.saveFailed).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
