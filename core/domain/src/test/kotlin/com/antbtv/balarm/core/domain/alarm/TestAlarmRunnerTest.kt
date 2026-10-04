package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

/** Тестовый звонок (ADR-010): `AlarmId.TEST` + `setAlarmClock` + снимок в памяти, расписание не трогается. */
class TestAlarmRunnerTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:00:20").atZone(moscow).toInstant(), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val store = InMemoryTestAlarmStore()
    private val runner = TestAlarmRunner(scheduler, store, clock, log)
    private val engine = AlarmEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log, runner)

    private val draft = Alarm(time = LocalTime.of(7, 30), label = "Draft", snooze = SnoozeSettings.DEFAULT)
    private val daily = Alarm(time = LocalTime.of(6, 30), repeatDays = DayOfWeek.entries.toSet())

    @Test
    fun `schedule plans a regular alarm clock for the reserved id and logs it`() {
        val at = runner.schedule(draft, TestAlarmRunner.EDITOR_DELAY)

        assertThat(at).isEqualTo(clock.now + Duration.ofSeconds(5))
        assertThat(scheduler.scheduled[AlarmId.TEST]).isEqualTo(ScheduleRequest(AlarmId.TEST, at!!, FireKind.REGULAR))
        assertThat(log.events).contains(AlarmEvent.TestScheduled(at))
        assertThat(store.get()).isEqualTo(draft.copy(id = AlarmId.TEST))
    }

    @Test
    fun `a refusal of the system is reported and leaves no snapshot`() {
        scheduler.accept = false

        val at = runner.schedule(draft, TestAlarmRunner.EDITOR_DELAY)

        assertThat(at).isNull()
        assertThat(store.get()).isNull()
        assertThat(log.events.filterIsInstance<AlarmEvent.ScheduleFailed>().map { it.id }).containsExactly(AlarmId.TEST)
    }

    @Test
    fun `a refusal keeps the snapshot of the previous test that is still scheduled`() {
        runner.schedule(draft, Duration.ofSeconds(5))
        scheduler.accept = false

        val at = runner.schedule(draft.copy(label = "Second"), Duration.ofSeconds(30))

        assertThat(at).isNull()
        assertThat(store.get()?.label).isEqualTo("Draft")
    }

    @Test
    fun `scheduling again replaces the previous test`() {
        runner.schedule(draft, Duration.ofSeconds(5))
        val second = runner.schedule(draft.copy(label = "Second"), Duration.ofSeconds(60))

        assertThat(scheduler.scheduled.keys).containsExactly(AlarmId.TEST)
        assertThat(scheduler.scheduled[AlarmId.TEST]?.triggerAt).isEqualTo(second)
        assertThat(store.get()?.label).isEqualTo("Second")
    }

    @Test
    fun `decision rings the draft without snooze`() {
        runner.schedule(draft, Duration.ofSeconds(5))

        val decision = runner.decision()

        val expected = FireDecision.Ring(draft.copy(id = AlarmId.TEST), canSnooze = false, snoozesLeft = 0)
        assertThat(decision).isEqualTo(expected)
    }

    @Test
    fun `without a snapshot the test still rings with defaults`() {
        val decision = runner.decision()

        assertThat(decision.alarm.id).isEqualTo(AlarmId.TEST)
        assertThat(decision.alarm.time).isEqualTo(LocalTime.of(5, 0))
        assertThat(decision.canSnooze).isFalse()
        assertThat(decision.degraded).isFalse()
    }

    @Test
    fun `engine fires the test alarm without touching storage or waiting for the engine lock`() = runTest {
        runner.schedule(draft, Duration.ofSeconds(5))
        repository.failOnLoad = IllegalStateException("storage must not be read")
        repository.transactionGate = CompletableDeferred() // движок «занят» навсегда

        val decision = engine.onFired(AlarmId.TEST, clock.now + Duration.ofSeconds(5), FireKind.REGULAR)

        assertThat(decision).isEqualTo(runner.decision())
        assertThat(log.events.filterIsInstance<AlarmEvent.Fired>().map { it.id }).containsExactly(AlarmId.TEST)
        assertThat(log.events.filterIsInstance<AlarmEvent.FireDegraded>()).isEmpty()
    }

    @Test
    fun `dismissing the test keeps a newly scheduled test and leaves the user schedule alone`() = runTest {
        val id = engine.save(daily).id
        runner.schedule(draft, Duration.ofSeconds(5))
        runner.schedule(draft.copy(label = "Next"), Duration.ofSeconds(60)) // следующий тест уже поставлен
        val userRequest = scheduler.scheduled.getValue(id)

        engine.dismiss(AlarmId.TEST, DismissReason.USER)

        assertThat(store.get()?.label).isEqualTo("Next")
        assertThat(scheduler.scheduled[id]).isEqualTo(userRequest)
        assertThat(log.events).contains(AlarmEvent.Dismissed(AlarmId.TEST, DismissReason.USER))
    }

    @Test
    fun `the test alarm cannot be snoozed`() = runTest {
        runner.schedule(draft, Duration.ofSeconds(5))

        assertThat(engine.snooze(AlarmId.TEST)).isEqualTo(SnoozeResult.NotAllowed)
        assertThat(scheduler.scheduled[AlarmId.TEST]?.kind).isEqualTo(FireKind.REGULAR)
    }

    @Test
    fun `reschedule all does not see or cancel the test`() = runTest {
        val id = engine.save(daily).id
        runner.schedule(draft, Duration.ofSeconds(5))

        val count = engine.rescheduleAll(RescheduleReason.TIME_SET)

        assertThat(count).isEqualTo(1)
        assertThat(scheduler.scheduled.keys).containsExactly(id, AlarmId.TEST)
        assertThat(scheduler.cancelled).doesNotContain(AlarmId.TEST)
    }

    @Test
    fun `degraded record of the test is a no-op`() = runTest {
        repository.transactionGate = CompletableDeferred() // обращение к движку повисло бы

        withTimeout(1_000) { engine.recordDegraded(AlarmId.TEST, clock.now, FireKind.REGULAR) }

        assertThat(scheduler.scheduleCalls).isEqualTo(0)
        assertThat(log.events).isEmpty()
    }
}
