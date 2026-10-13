package com.antbtv.balarm.feature.alarmedit

import app.cash.turbine.test
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.FakeSettingsRepository
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testAlarmRunner
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Строка «Миссия» и её подэкран (M5-T05, FR-EDIT-6, FR-MIS-5, FR-FLAG-3): флаг, выбор, нормализация, превью. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditMissionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:20").atZone(moscow).toInstant(), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val flags = FeatureFlagProvider { it.defaultEnabled }
    private val engine: AlarmEngine = testEngine(repository, scheduler, clock, flags, log)
    private val preview = RecordingSoundPreview()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(alarmId: Long? = null, missionFlag: Boolean = true) = AlarmEditViewModel(
        alarmId,
        repository,
        engine,
        testAlarmRunner(scheduler, clock, log),
        clock,
        flags,
        FakeSoundRepository(),
        preview,
        missionRegistry(enabled = missionFlag),
        FakeSettingsRepository(),
    ).also { runCurrent() }

    private fun stored(alarm: Alarm): Long = runBlocking { engine.save(alarm).id.value }

    private fun alarmInDb(id: Long): Alarm? = runBlocking { repository.get(AlarmId(id)) }

    private fun TestScope.send(viewModel: AlarmEditViewModel, vararg events: AlarmEditEvent) {
        events.forEach(viewModel::onEvent)
        runCurrent()
    }

    private val math = MissionTypeId.MATH

    @Test
    fun `flag off - no section, mission events are ignored and the stored mission is saved as is`() =
        runTest(dispatcher) {
            val spec = MissionSpec(math, "4")
            val id = stored(Alarm(time = LocalTime.of(7, 30), mission = spec))
            val viewModel = viewModel(id, missionFlag = false)

            assertThat(viewModel.uiState.value.missionVisible).isFalse()
            send(
                viewModel,
                AlarmEditEvent.OpenMissionSettings,
                AlarmEditEvent.MissionSelected(null),
                AlarmEditEvent.MissionConfigChanged("5"),
            )
            val state = viewModel.uiState.value
            assertThat(state.missionSettingsOpen).isFalse()
            assertThat(state.draft.mission).isEqualTo(spec)

            viewModel.effects.test {
                send(viewModel, AlarmEditEvent.Save)
                assertThat(awaitItem()).isInstanceOf(AlarmEditEffect.Saved::class.java)
            }
            assertThat(alarmInDb(id)!!.mission).isEqualTo(spec)
        }

    @Test
    fun `flag on - choosing the mission sets a normalized default config, none clears it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.missionVisible).isTrue()
        assertThat(viewModel.uiState.value.selectedMission).isNull()

        send(viewModel, AlarmEditEvent.OpenMissionSettings, AlarmEditEvent.MissionSelected(math))

        val chosen = viewModel.uiState.value
        assertThat(chosen.missionSettingsOpen).isTrue()
        assertThat(chosen.draft.mission).isEqualTo(MissionSpec(math, FakeMission.DEFAULT))
        assertThat(chosen.selectedMission).isSameInstanceAs(FakeMission)
        assertThat(chosen.isDirty).isTrue()

        send(viewModel, AlarmEditEvent.MissionSelected(null))
        assertThat(viewModel.uiState.value.draft.mission).isNull()
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun `config changes are normalized`() = runTest(dispatcher) {
        val viewModel = viewModel()
        send(viewModel, AlarmEditEvent.MissionSelected(math), AlarmEditEvent.MissionConfigChanged("4"))
        assertThat(viewModel.uiState.value.draft.mission).isEqualTo(MissionSpec(math, "4"))

        send(viewModel, AlarmEditEvent.MissionConfigChanged("99"))
        assertThat(viewModel.uiState.value.draft.mission).isEqualTo(MissionSpec(math, FakeMission.DEFAULT))
    }

    @Test
    fun `config without a chosen mission and unknown types change nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        send(
            viewModel,
            AlarmEditEvent.MissionConfigChanged("4"),
            AlarmEditEvent.MissionSelected(MissionTypeId("memory")),
        )

        assertThat(viewModel.uiState.value.draft.mission).isNull()
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun `stored config is kept on reselecting the same mission, a broken one is normalized`() = runTest(dispatcher) {
        val keep = viewModel(stored(Alarm(time = LocalTime.of(7, 30), mission = MissionSpec(math, "5"))))
        send(keep, AlarmEditEvent.MissionSelected(math))
        assertThat(keep.uiState.value.draft.mission).isEqualTo(MissionSpec(math, "5"))

        val broken = viewModel(stored(Alarm(time = LocalTime.of(8, 0), mission = MissionSpec(math, "{oops"))))
        send(broken, AlarmEditEvent.MissionSelected(math))
        assertThat(broken.uiState.value.draft.mission).isEqualTo(MissionSpec(math, FakeMission.DEFAULT))
    }

    @Test
    fun `the chosen mission is saved`() = runTest(dispatcher) {
        val id = stored(Alarm(time = LocalTime.of(7, 30)))
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(
                viewModel,
                AlarmEditEvent.MissionSelected(math),
                AlarmEditEvent.MissionConfigChanged("3"),
                AlarmEditEvent.Save,
            )
            assertThat(awaitItem()).isInstanceOf(AlarmEditEffect.Saved::class.java)
        }
        assertThat(alarmInDb(id)!!.mission).isEqualTo(MissionSpec(math, "3"))
    }

    @Test
    fun `back on the mission settings returns to the form, not to the discard dialog`() = runTest(dispatcher) {
        val viewModel = viewModel()
        send(viewModel, AlarmEditEvent.OpenMissionSettings, AlarmEditEvent.MissionSelected(math))

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Back)
            expectNoEvents()
        }
        val state = viewModel.uiState.value
        assertThat(state.missionSettingsOpen).isFalse()
        assertThat(state.dialog).isNull()
        assertThat(state.draft.mission).isEqualTo(MissionSpec(math, FakeMission.DEFAULT))

        // Второй Back — уже обычный: правки есть → «Отменить изменения?».
        send(viewModel, AlarmEditEvent.Back)
        assertThat(viewModel.uiState.value.dialog).isEqualTo(EditDialog.ConfirmDiscard)
    }

    @Test
    fun `done closes the mission settings`() = runTest(dispatcher) {
        val viewModel = viewModel()
        send(viewModel, AlarmEditEvent.OpenMissionSettings, AlarmEditEvent.CloseMissionSettings)
        assertThat(viewModel.uiState.value.missionSettingsOpen).isFalse()
    }

    @Test
    fun `preview asks to open the chosen mission and silences the ringtone preview`() = runTest(dispatcher) {
        val viewModel = viewModel()
        send(viewModel, AlarmEditEvent.MissionSelected(math), AlarmEditEvent.MissionConfigChanged("4"))
        val stopsBefore = preview.stops

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.PreviewMission)
            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.OpenMissionPreview(MissionSpec(math, "4")))
        }
        assertThat(preview.stops).isGreaterThan(stopsBefore)
    }

    @Test
    fun `no preview without a mission`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.PreviewMission)
            expectNoEvents()
        }
    }
}
