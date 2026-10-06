package com.antbtv.balarm.feature.alarmedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.AlarmWithRuntime
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testAlarmRunner
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Редактор на реальном движке с фейками: что сохраняется, что уходит в `AlarmManager`, что видит экран. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(local("2026-09-28T05:20"), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private var snoozeFlag = true
    private val flags = FeatureFlagProvider { if (it == Feature.SNOOZE) snoozeFlag else it.defaultEnabled }
    private val engine: AlarmEngine = testEngine(repository, scheduler, clock, flags, log)
    private val testAlarms = testAlarmRunner(scheduler, clock, log)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun local(iso: String): Instant = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private fun TestScope.viewModel(
        alarmId: Long? = null,
        repo: AlarmRepository = repository,
        runner: TestAlarmRunner = testAlarms,
    ): AlarmEditViewModel = AlarmEditViewModel(alarmId, repo, engine, runner, clock, flags).also { runCurrent() }

    private suspend fun stored(alarm: Alarm = Alarm(time = LocalTime.of(7, 30), label = "Gym")): Long =
        engine.save(alarm).id.value

    private fun TestScope.send(viewModel: AlarmEditViewModel, vararg events: AlarmEditEvent) {
        events.forEach(viewModel::onEvent)
        runCurrent()
    }

    private fun alarmInDb(id: Long): Alarm? = runCurrentBlocking { repository.get(AlarmId(id)) }

    private fun <T> runCurrentBlocking(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

    @Test
    fun `a new alarm opens ready with the defaults - next whole hour, one-shot, default snooze`() =
        runTest(dispatcher) {
            val state = viewModel().uiState.value

            assertThat(state.loading).isFalse()
            assertThat(state.isNew).isTrue()
            assertThat(state.canDelete).isFalse()
            assertThat(state.draft.time).isEqualTo(LocalTime.of(6, 0))
            assertThat(state.draft.isOneShot).isTrue()
            assertThat(state.draft.snooze).isEqualTo(SnoozeSettings.DEFAULT)
            assertThat(state.isDirty).isFalse()
        }

    @Test
    fun `an existing alarm loads into the draft`() = runTest(dispatcher) {
        val id = stored()

        val viewModel = AlarmEditViewModel(id, repository, engine, testAlarms, clock, flags)
        assertThat(viewModel.uiState.value.loading).isTrue()
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.isNew).isFalse()
        assertThat(state.canDelete).isTrue()
        assertThat(state.draft.label).isEqualTo("Gym")
        assertThat(state.isDirty).isFalse()
    }

    @Test
    fun `an alarm that is gone closes the editor`() = runTest(dispatcher) {
        val viewModel = AlarmEditViewModel(999L, repository, engine, testAlarms, clock, flags)

        viewModel.effects.test {
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
        }
    }

    @Test
    fun `save creates the alarm enabled with a trimmed label and announces the time`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(
                viewModel,
                AlarmEditEvent.TimeChanged(LocalTime.of(6, 30)),
                AlarmEditEvent.LabelChanged("  Work  "),
                AlarmEditEvent.Save,
            )

            val saved = awaitItem() as AlarmEditEffect.Saved
            assertThat(saved.result.scheduled).isTrue()
            assertThat(saved.until).isEqualTo(TimeUntil(0, 1, 10))
            val alarm = alarmInDb(saved.result.id.value)!!
            assertThat(alarm.label).isEqualTo("Work")
            assertThat(alarm.time).isEqualTo(LocalTime.of(6, 30))
            assertThat(alarm.enabled).isTrue()
            assertThat(scheduler.scheduled).containsKey(saved.result.id)
        }
    }

    @Test
    fun `save of an unchanged new alarm still creates it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Save)

            val saved = awaitItem() as AlarmEditEffect.Saved
            assertThat(alarmInDb(saved.result.id.value)).isNotNull()
        }
    }

    @Test
    fun `save switches a disabled alarm on`() = runTest(dispatcher) {
        val id = stored(Alarm(time = LocalTime.of(7, 30), enabled = false))
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Save)

            awaitItem()
            assertThat(alarmInDb(id)!!.enabled).isTrue()
            assertThat(scheduler.scheduled).containsKey(AlarmId(id))
        }
    }

    @Test
    fun `saving a snoozed alarm keeps the pending snooze`() = runTest(dispatcher) {
        val id = AlarmId(stored(Alarm(time = LocalTime.of(5, 30))))
        clock.now = local("2026-09-28T05:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        engine.snooze(id)
        clock.now = local("2026-09-28T05:31")
        val snoozeAt = scheduler.scheduled.getValue(id).triggerAt
        val viewModel = viewModel(id.value)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.LabelChanged("Renamed"), AlarmEditEvent.Save)

            awaitItem()
            assertThat(scheduler.scheduled.getValue(id).triggerAt).isEqualTo(snoozeAt)
            assertThat(alarmInDb(id.value)!!.label).isEqualTo("Renamed")
        }
    }

    @Test
    fun `a second save tap while saving is ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Save, AlarmEditEvent.Save, AlarmEditEvent.Save)

            awaitItem()
            expectNoEvents()
            assertThat(scheduler.scheduleCalls).isEqualTo(1)
            assertThat(viewModel.uiState.value.saving).isTrue()
        }
    }

    @Test
    fun `save waiting for a busy engine survives leaving the screen`() = runTest(dispatcher) {
        // Движок занят (rescheduleAll держит мьютекс): «Сохранить» ждёт очереди, а экран закрывают.
        val gate = CompletableDeferred<Unit>()
        val busy = object : AlarmRepository by repository {
            override suspend fun loadAll(): List<AlarmWithRuntime> {
                gate.await()
                return repository.loadAll()
            }
        }
        val busyEngine = testEngine(busy, scheduler, clock, flags, log)
        launch { busyEngine.rescheduleAll(RescheduleReason.APP_LAUNCH) }
        runCurrent()
        val store = ViewModelStore()
        val created = AlarmEditViewModel(null, busy, busyEngine, testAlarms, clock, flags)
        val provided = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
            },
        )[AlarmEditViewModel::class.java]

        send(provided, AlarmEditEvent.Save)
        store.clear() // экран закрыт, пока сохранение стоит в очереди
        gate.complete(Unit)
        runCurrent()

        assertThat(runCurrentBlocking { repository.loadAll() }.map(AlarmWithRuntime::alarm)).hasSize(1)
        assertThat(scheduler.scheduled).isNotEmpty()
    }

    @Test
    fun `an engine failure on save is reported and the next tap can retry`() = runTest(dispatcher) {
        val id = stored()
        repository.failRuntimeFor += AlarmId(id)
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Save)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.SaveFailed)
            assertThat(viewModel.uiState.value.saving).isFalse()

            repository.failRuntimeFor.clear()
            send(viewModel, AlarmEditEvent.Save)
            assertThat(awaitItem()).isInstanceOf(AlarmEditEffect.Saved::class.java)
        }
    }

    @Test
    fun `the system refusing the alarm is reported with no time`() = runTest(dispatcher) {
        scheduler.accept = false
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Save)

            val saved = awaitItem() as AlarmEditEffect.Saved
            assertThat(saved.result.scheduled).isFalse()
            assertThat(saved.until).isNull()
        }
    }

    @Test
    fun `with the snooze flag off the section is hidden and the stored snooze is not changed`() = runTest(dispatcher) {
        snoozeFlag = false
        val custom = SnoozeSettings(Duration.ofMinutes(15), maxCount = 5)
        val id = stored(Alarm(time = LocalTime.of(7, 30), snooze = custom))
        val viewModel = viewModel(id)
        assertThat(viewModel.uiState.value.snoozeVisible).isFalse()

        viewModel.effects.test {
            send(
                viewModel,
                AlarmEditEvent.SnoozeIntervalSelected(null),
                AlarmEditEvent.LabelChanged("Edited"),
                AlarmEditEvent.Save,
            )

            awaitItem()
            val alarm = alarmInDb(id)!!
            assertThat(alarm.snooze).isEqualTo(custom)
            assertThat(alarm.label).isEqualTo("Edited")
        }
    }

    @Test
    fun `test rings the unsaved draft in five seconds without touching the database`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.LabelChanged("Draft"), AlarmEditEvent.Test)

            val effect = awaitItem() as AlarmEditEffect.TestScheduled
            assertThat(effect.at).isEqualTo(clock.instant() + Duration.ofSeconds(5))
            assertThat(scheduler.scheduled.getValue(AlarmId.TEST).triggerAt).isEqualTo(effect.at)
            assertThat(runCurrentBlocking { repository.loadAll() }).isEmpty()
            val rung = testAlarms.decision().alarm
            assertThat(rung.id).isEqualTo(AlarmId.TEST)
            assertThat(rung.label).isEqualTo("Draft")
        }
    }

    @Test
    fun `test rings with the saved form of the draft - enabled and trimmed`() = runTest(dispatcher) {
        val id = stored(Alarm(time = LocalTime.of(7, 30), enabled = false))
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.LabelChanged("  Padded  "), AlarmEditEvent.Test)

            awaitItem()
            val rung = testAlarms.decision().alarm
            assertThat(rung.enabled).isTrue()
            assertThat(rung.label).isEqualTo("Padded")
        }
    }

    @Test
    fun `test refused by the system reports it`() = runTest(dispatcher) {
        scheduler.accept = false
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Test)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.TestScheduled(at = null))
        }
    }

    @Test
    fun `delete asks first, then removes the alarm and closes`() = runTest(dispatcher) {
        val id = stored()
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Delete)
            assertThat(viewModel.uiState.value.dialog).isEqualTo(EditDialog.ConfirmDelete)
            assertThat(alarmInDb(id)).isNotNull()

            send(viewModel, AlarmEditEvent.ConfirmDelete)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
            assertThat(alarmInDb(id)).isNull()
            assertThat(scheduler.scheduled).doesNotContainKey(AlarmId(id))
            assertThat(viewModel.uiState.value.dialog).isNull()
        }
    }

    @Test
    fun `cancelling the delete confirmation keeps the alarm`() = runTest(dispatcher) {
        val id = stored()
        val viewModel = viewModel(id)

        send(viewModel, AlarmEditEvent.Delete, AlarmEditEvent.DialogDismissed)

        assertThat(viewModel.uiState.value.dialog).isNull()
        assertThat(alarmInDb(id)).isNotNull()
    }

    @Test
    fun `a new alarm cannot be deleted`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Delete, AlarmEditEvent.ConfirmDelete)

            assertThat(viewModel.uiState.value.dialog).isNull()
            expectNoEvents()
        }
    }

    @Test
    fun `a failed delete is reported and the alarm stays`() = runTest(dispatcher) {
        val id = stored()
        repository.failOnDelete = IllegalStateException("disk")
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Delete, AlarmEditEvent.ConfirmDelete)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.DeleteFailed)
            assertThat(alarmInDb(id)).isNotNull()
            assertThat(viewModel.uiState.value.saving).isFalse()
        }
    }

    @Test
    fun `back without changes closes at once`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Back)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
            assertThat(viewModel.uiState.value.dialog).isNull()
        }
    }

    @Test
    fun `back with changes asks and discard closes without saving`() = runTest(dispatcher) {
        val id = stored()
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.DayToggled(DayOfWeek.MONDAY), AlarmEditEvent.Back)
            assertThat(viewModel.uiState.value.dialog).isEqualTo(EditDialog.ConfirmDiscard)
            expectNoEvents()

            send(viewModel, AlarmEditEvent.DiscardConfirmed)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
            assertThat(alarmInDb(id)!!.repeatDays).isEmpty()
        }
    }

    @Test
    fun `a change reverted by hand lets back close without asking`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(
                viewModel,
                AlarmEditEvent.DayToggled(DayOfWeek.MONDAY),
                AlarmEditEvent.DayToggled(DayOfWeek.MONDAY),
                AlarmEditEvent.Back,
            )

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
        }
    }

    @Test
    fun `a double tap on confirm delete deletes once`() = runTest(dispatcher) {
        val id = stored()
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Delete, AlarmEditEvent.ConfirmDelete, AlarmEditEvent.ConfirmDelete)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
            expectNoEvents()
            assertThat(scheduler.cancelled.count { it == AlarmId(id) }).isEqualTo(1)
        }
    }

    @Test
    fun `delete is not offered while saving`() = runTest(dispatcher) {
        val id = stored()
        val viewModel = viewModel(id)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Save, AlarmEditEvent.Delete, AlarmEditEvent.ConfirmDelete)

            assertThat(awaitItem()).isInstanceOf(AlarmEditEffect.Saved::class.java)
            expectNoEvents()
            assertThat(alarmInDb(id)).isNotNull()
        }
    }

    @Test
    fun `back during saving does not close before the result is delivered`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onEvent(AlarmEditEvent.DayToggled(DayOfWeek.MONDAY))

        viewModel.effects.test {
            // Save и Back приходят до того, как сохранение выполнится.
            viewModel.onEvent(AlarmEditEvent.Save)
            viewModel.onEvent(AlarmEditEvent.Back)
            viewModel.onEvent(AlarmEditEvent.DiscardConfirmed)
            runCurrent()

            assertThat(awaitItem()).isInstanceOf(AlarmEditEffect.Saved::class.java)
            expectNoEvents()
            assertThat(viewModel.uiState.value.dialog).isNull()
        }
    }

    @Test
    fun `a double back closes once`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Back, AlarmEditEvent.Back)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
            expectNoEvents()
        }
    }

    @Test
    fun `events after the screen is closing are ignored`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Back, AlarmEditEvent.Save)

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.Close)
            expectNoEvents()
            assertThat(runCurrentBlocking { repository.loadAll() }).isEmpty()
        }
    }

    @Test
    fun `a failed load is reported instead of crashing`() = runTest(dispatcher) {
        val id = stored()
        val broken = object : AlarmRepository by repository {
            override suspend fun get(id: AlarmId): Alarm? = throw IllegalStateException("corrupt")
        }

        viewModel(id, repo = broken).effects.test {
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AlarmEditEffect.LoadFailed)
        }
    }

    @Test
    fun `saving an enabled alarm without changes keeps its pending snooze`() = runTest(dispatcher) {
        val id = AlarmId(stored(Alarm(time = LocalTime.of(5, 30), repeatDays = DayOfWeek.entries.toSet())))
        clock.now = local("2026-09-28T05:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        engine.snooze(id)
        clock.now = local("2026-09-28T05:31")
        val snoozeAt = scheduler.scheduled.getValue(id).triggerAt
        val viewModel = viewModel(id.value)

        viewModel.effects.test {
            send(viewModel, AlarmEditEvent.Save)

            awaitItem()
            assertThat(scheduler.scheduled.getValue(id).triggerAt).isEqualTo(snoozeAt)
        }
    }
}
