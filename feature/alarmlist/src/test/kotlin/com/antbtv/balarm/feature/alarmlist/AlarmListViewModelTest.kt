package com.antbtv.balarm.feature.alarmlist

import app.cash.turbine.test
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.TriggerKind
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
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

/** Список на реальном движке с фейками: что видит пользователь и что уходит в `AlarmManager`. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(local("2026-09-28T05:00"), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val engine: AlarmEngine =
        testEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, RecordingEventLog())

    private val oneShot = Alarm(time = LocalTime.of(6, 30))
    private val daily = Alarm(time = LocalTime.of(7, 0), repeatDays = DayOfWeek.entries.toSet())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun local(iso: String): Instant = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private fun viewModel() = AlarmListViewModel(repository, engine, clock)

    /** Подписка на состояние, как у экрана на переднем плане. */
    private fun TestScope.subscribed(viewModel: AlarmListViewModel): AlarmListViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun AlarmListViewModel.item(id: AlarmId): AlarmItemUi = uiState.value.alarms.single { it.id == id }

    @Test
    fun `before the first load the list is loading and not empty`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertThat(state.loading).isTrue()
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `no alarms - empty list and no next alarm`() = runTest(dispatcher) {
        val state = subscribed(viewModel()).uiState.value

        assertThat(state.loading).isFalse()
        assertThat(state.isEmpty).isTrue()
        assertThat(state.nextIn).isNull()
    }

    @Test
    fun `cards carry the alarm data and the header counts to the earliest trigger`() = runTest(dispatcher) {
        engine.save(daily)
        engine.save(oneShot.copy(label = "Gym"))

        val state = subscribed(viewModel()).uiState.value

        assertThat(state.alarms.map { it.time to it.label })
            .containsExactly(LocalTime.of(6, 30) to "Gym", LocalTime.of(7, 0) to "")
        assertThat(state.nextIn).isEqualTo(TimeUntil(days = 0, hours = 1, minutes = 30))
    }

    @Test
    fun `subtitle says today or tomorrow, nothing further away`() = runTest(dispatcher) {
        val today = engine.save(oneShot).id
        val tomorrow = engine.save(Alarm(time = LocalTime.of(4, 0))).id
        val later = engine.save(Alarm(time = LocalTime.of(8, 0), repeatDays = setOf(DayOfWeek.FRIDAY))).id

        val viewModel = subscribed(viewModel())

        assertThat(viewModel.item(today).subtitle).isEqualTo(AlarmSubtitle.Today)
        assertThat(viewModel.item(tomorrow).subtitle).isEqualTo(AlarmSubtitle.Tomorrow)
        assertThat(viewModel.item(later).subtitle).isNull()
    }

    @Test
    fun `disabled alarms are inactive and leave the header empty`() = runTest(dispatcher) {
        val id = engine.save(oneShot.copy(enabled = false)).id

        val viewModel = subscribed(viewModel())

        assertThat(viewModel.item(id).active).isFalse()
        assertThat(viewModel.item(id).subtitle).isNull()
        assertThat(viewModel.uiState.value.nextIn).isNull()
    }

    @Test
    fun `the header follows the clock minute by minute while subscribed`() = runTest(dispatcher) {
        engine.save(oneShot)
        val viewModel = subscribed(viewModel())
        assertThat(viewModel.uiState.value.nextIn).isEqualTo(TimeUntil(0, 1, 30))

        clock.advance(Duration.ofMinutes(10))
        testScheduler.advanceTimeBy(1.minutes)
        runCurrent()

        assertThat(viewModel.uiState.value.nextIn).isEqualTo(TimeUntil(0, 1, 20))
    }

    @Test
    fun `a pending snooze keeps a one-shot active and shows the snoozed time`() = runTest(dispatcher) {
        val id = engine.save(oneShot).id
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        engine.snooze(id)
        clock.now = local("2026-09-28T06:31")

        val viewModel = subscribed(viewModel())

        assertThat(viewModel.item(id).active).isTrue()
        assertThat(viewModel.item(id).subtitle).isEqualTo(AlarmSubtitle.SnoozedUntil(LocalTime.of(6, 35)))
        assertThat(viewModel.uiState.value.nextIn).isEqualTo(TimeUntil(0, 0, 4))
    }

    @Test
    fun `switching off cancels the system alarm and the switch follows the database`() = runTest(dispatcher) {
        val id = engine.save(oneShot).id
        val viewModel = subscribed(viewModel())
        assertThat(scheduler.scheduled).containsKey(id)

        viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = false))
        runCurrent()

        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(viewModel.item(id).active).isFalse()
        assertThat(viewModel.uiState.value.nextIn).isNull()
    }

    @Test
    fun `switching off a one-shot waiting for snooze cancels the snooze`() = runTest(dispatcher) {
        val id = engine.save(oneShot).id
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        engine.snooze(id)
        clock.now = local("2026-09-28T06:31")
        val viewModel = subscribed(viewModel())
        assertThat(viewModel.item(id).active).isTrue()

        viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = !viewModel.item(id).active))
        runCurrent()

        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(viewModel.item(id).active).isFalse()
    }

    @Test
    fun `switching on schedules the alarm and announces the time until it rings`() = runTest(dispatcher) {
        val id = engine.save(oneShot.copy(enabled = false)).id
        val viewModel = subscribed(viewModel())

        viewModel.effects.test {
            viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = true))
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AlarmListEffect.RingsIn(TimeUntil(0, 1, 30)))
        }
        assertThat(scheduler.scheduled).containsKey(id)
        assertThat(viewModel.item(id).active).isTrue()
    }

    @Test
    fun `the system refusing the alarm is reported instead of the time`() = runTest(dispatcher) {
        val id = engine.save(oneShot.copy(enabled = false)).id
        scheduler.accept = false
        val viewModel = subscribed(viewModel())

        viewModel.effects.test {
            viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = true))
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AlarmListEffect.ScheduleFailed)
        }
    }

    @Test
    fun `switching off shows nothing`() = runTest(dispatcher) {
        val id = engine.save(oneShot).id
        val viewModel = subscribed(viewModel())

        viewModel.effects.test {
            viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = false))
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `toggling an alarm that is gone shows nothing`() = runTest(dispatcher) {
        val viewModel = subscribed(viewModel())

        viewModel.effects.test {
            viewModel.onEvent(AlarmListEvent.Toggle(AlarmId(999), enabled = true))
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `an engine failure while switching on is reported and does not crash`() = runTest(dispatcher) {
        val id = engine.save(oneShot.copy(enabled = false)).id
        scheduler.throwFor += id
        val viewModel = subscribed(viewModel())

        viewModel.effects.test {
            viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = true))
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AlarmListEffect.ScheduleFailed)
        }
    }

    @Test
    fun `a failure while switching off says so and not that scheduling failed`() = runTest(dispatcher) {
        val id = engine.save(oneShot).id
        repository.failRuntimeFor += id
        val viewModel = subscribed(viewModel())

        viewModel.effects.test {
            viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = false))
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AlarmListEffect.DisableFailed)
        }
    }

    @Test
    fun `delete removes the card and the system alarm`() = runTest(dispatcher) {
        val id = engine.save(oneShot).id
        val viewModel = subscribed(viewModel())

        viewModel.onEvent(AlarmListEvent.Delete(id))
        runCurrent()

        assertThat(viewModel.uiState.value.alarms).isEmpty()
        assertThat(viewModel.uiState.value.isEmpty).isTrue()
        assertThat(scheduler.scheduled).doesNotContainKey(id)
    }

    @Test
    fun `a failed delete keeps the card and is reported`() = runTest(dispatcher) {
        val id = engine.save(oneShot).id
        repository.failOnDelete = IllegalStateException("disk")
        val viewModel = subscribed(viewModel())

        viewModel.effects.test {
            viewModel.onEvent(AlarmListEvent.Delete(id))
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AlarmListEffect.DeleteFailed)
        }
        assertThat(viewModel.uiState.value.alarms).hasSize(1)
    }

    @Test
    fun `the header counts from the exact moment, not from the truncated minute`() = runTest(dispatcher) {
        val id = engine.save(oneShot.copy(enabled = false)).id
        clock.now = local("2026-09-28T06:31:10")
        repository.runtimes[id] = AlarmRuntimeState(
            alarmId = id,
            nextTriggerAt = local("2026-09-28T06:35:05"),
            nextTriggerKind = TriggerKind.SNOOZE,
        )

        val viewModel = subscribed(viewModel())

        // 3 мин 55 с → 4 мин; от усечённых 06:31:00 вышло бы 4 мин 5 с → 5 мин.
        assertThat(viewModel.uiState.value.nextIn).isEqualTo(TimeUntil(0, 0, 4))
    }

    @Test
    fun `the subtitle turns from tomorrow to today at midnight`() = runTest(dispatcher) {
        clock.now = local("2026-09-28T23:59")
        val id = engine.save(Alarm(time = LocalTime.of(0, 30))).id
        val viewModel = subscribed(viewModel())
        assertThat(viewModel.item(id).subtitle).isEqualTo(AlarmSubtitle.Tomorrow)

        clock.advance(Duration.ofMinutes(1))
        testScheduler.advanceTimeBy(1.minutes)
        runCurrent()

        assertThat(viewModel.item(id).subtitle).isEqualTo(AlarmSubtitle.Today)
    }

    @Test
    fun `a time zone change is picked up on the next tick`() = runTest(dispatcher) {
        val id = engine.save(Alarm(time = LocalTime.of(22, 0))).id
        val viewModel = subscribed(viewModel())
        assertThat(viewModel.item(id).subtitle).isEqualTo(AlarmSubtitle.Today)

        // 22:00 по Москве — уже 05:00 следующего дня во Владивостоке.
        clock.zoneId = ZoneId.of("Asia/Vladivostok")
        testScheduler.advanceTimeBy(1.minutes)
        runCurrent()

        assertThat(viewModel.item(id).subtitle).isEqualTo(AlarmSubtitle.Tomorrow)
    }

    @Test
    fun `nothing is read from the clock while nobody is subscribed`() = runTest(dispatcher) {
        engine.save(oneShot)
        val counting = CountingClock(clock)
        val viewModel = AlarmListViewModel(repository, engine, counting)
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()
        subscription.cancel()
        testScheduler.advanceTimeBy(STOP_TIMEOUT)
        runCurrent()
        val reads = counting.reads

        testScheduler.advanceTimeBy(10.minutes)
        runCurrent()

        assertThat(counting.reads).isEqualTo(reads)
    }

    @Test
    fun `effects raised while nobody listens are delivered later`() = runTest(dispatcher) {
        val id = engine.save(oneShot.copy(enabled = false)).id
        val viewModel = subscribed(viewModel())

        viewModel.onEvent(AlarmListEvent.Toggle(id, enabled = true))
        runCurrent()

        viewModel.effects.test {
            assertThat(awaitItem()).isInstanceOf(AlarmListEffect.RingsIn::class.java)
        }
    }
}

private val STOP_TIMEOUT = 5.seconds + 1.seconds

/** Часы со счётчиком обращений: тик, который не остановили, продолжает их читать. */
private class CountingClock(private val delegate: MutableClock) : Clock() {
    var reads = 0
        private set

    override fun getZone(): ZoneId = delegate.zone

    override fun withZone(zone: ZoneId): Clock = delegate.withZone(zone)

    override fun instant(): Instant {
        reads++
        return delegate.instant()
    }
}
