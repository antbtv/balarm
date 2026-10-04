package com.antbtv.balarm.core.domain.alarm

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
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AlarmEngineScheduleTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(LocalDateTime.parse("2026-09-28T05:00").atZone(moscow).toInstant(), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val engine = testEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log)

    private fun local(iso: String) = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private val wakeUp = Alarm(time = LocalTime.of(6, 30))

    @Test
    fun `saving a new alarm assigns id and schedules the next regular trigger`() = runTest {
        val id = engine.save(wakeUp).id

        assertThat(id.isSaved).isTrue()
        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T06:30"), FireKind.REGULAR))
        assertThat(repository.runtimes[id]?.nextTriggerAt).isEqualTo(local("2026-09-28T06:30"))
        assertThat(log.events).contains(AlarmEvent.Scheduled(id, local("2026-09-28T06:30"), FireKind.REGULAR))
    }

    @Test
    fun `saving a disabled alarm cancels it and clears the planned trigger`() = runTest {
        val id = engine.save(wakeUp.copy(enabled = false)).id

        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(scheduler.cancelled).contains(id)
        assertThat(repository.runtimes[id]?.nextTriggerAt).isNull()
    }

    @Test
    fun `editing an alarm after its snooze was abandoned reschedules from a clean state`() = runTest {
        val id = engine.save(wakeUp).id
        repository.updateRuntime(
            AlarmRuntimeState(id, local("2026-09-28T04:00"), TriggerKind.SNOOZE, snoozeCount = 2),
        ) // snooze уже в прошлом (например, часы переведены вперёд)

        engine.save(wakeUp.copy(id = id, time = LocalTime.of(7, 0)))

        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-28T07:00"))
        assertThat(repository.runtimes[id]).isEqualTo(
            AlarmRuntimeState(id, local("2026-09-28T07:00"), TriggerKind.REGULAR, snoozeCount = 0),
        )
    }

    @Test
    fun `toggling an alarm cancels and restores the schedule`() = runTest {
        val id = engine.save(wakeUp).id

        engine.setEnabled(id, false)
        assertThat(scheduler.scheduled).doesNotContainKey(id)

        engine.setEnabled(id, true)
        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-28T06:30"))
    }

    @Test
    fun `deleting an alarm cancels it and removes runtime`() = runTest {
        val id = engine.save(wakeUp).id

        engine.delete(id)

        assertThat(scheduler.scheduled).isEmpty()
        assertThat(repository.get(id)).isNull()
        assertThat(repository.runtimes).doesNotContainKey(id)
        assertThat(log.events.last()).isEqualTo(AlarmEvent.Cancelled(id))
    }

    @Test
    fun `reschedule all is idempotent for locked boot followed by boot`() = runTest {
        val first = engine.save(wakeUp).id
        val second = engine.save(Alarm(time = LocalTime.of(8, 0), repeatDays = setOf(DayOfWeek.FRIDAY))).id
        engine.save(Alarm(time = LocalTime.of(9, 0), enabled = false))
        val before = scheduler.scheduled.toMap()

        assertThat(engine.rescheduleAll(RescheduleReason.LOCKED_BOOT)).isEqualTo(2)
        assertThat(engine.rescheduleAll(RescheduleReason.BOOT)).isEqualTo(2)

        assertThat(scheduler.scheduled).isEqualTo(before)
        assertThat(scheduler.scheduled.keys).containsExactly(first, second)
        assertThat(log.events).contains(AlarmEvent.RescheduledAll(RescheduleReason.BOOT, 2))
    }

    @Test
    fun `timezone change moves the absolute trigger to keep local time`() = runTest {
        val id = engine.save(wakeUp).id
        clock.zoneId = ZoneId.of("Asia/Tokyo")

        engine.rescheduleAll(RescheduleReason.TIMEZONE_CHANGED)

        val trigger = scheduler.scheduled.getValue(id).triggerAt
        assertThat(trigger.atZone(clock.zoneId).toLocalTime()).isEqualTo(LocalTime.of(6, 30))
    }

    @Test
    fun `missed trigger within grace is caught up shortly after boot`() = runTest {
        val id = engine.save(wakeUp).id
        clock.now = local("2026-09-28T06:35") // телефон перезагружался в 06:30

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled[id]).isEqualTo(
            ScheduleRequest(id, clock.now + AlarmEngine.CATCH_UP_DELAY, FireKind.CATCH_UP),
        )
        assertThat(repository.runtimes[id]?.nextTriggerKind).isEqualTo(TriggerKind.CATCH_UP)
        assertThat(log.events).contains(AlarmEvent.CatchUp(id, local("2026-09-28T06:30")))
    }

    @Test
    fun `catch up survives a second reschedule without moving`() = runTest {
        val id = engine.save(wakeUp).id
        clock.now = local("2026-09-28T06:35")
        engine.rescheduleAll(RescheduleReason.LOCKED_BOOT)
        val catchUp = scheduler.scheduled.getValue(id)

        clock.advance(Duration.ofSeconds(1))
        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled.getValue(id)).isEqualTo(catchUp)
    }

    @Test
    fun `missed trigger older than grace is not caught up`() = runTest {
        val id = engine.save(wakeUp.copy(repeatDays = DayOfWeek.entries.toSet())).id
        clock.now = local("2026-09-28T06:30") + AlarmEngine.LATE_GRACE + Duration.ofMinutes(1)

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-29T06:30"), FireKind.REGULAR))
    }

    @Test
    fun `trigger that already fired is not caught up`() = runTest {
        val id = engine.save(wakeUp).id
        repository.updateRuntime(repository.runtimes.getValue(id).copy(lastFiredAt = local("2026-09-28T06:30")))
        clock.now = local("2026-09-28T06:35")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled[id]?.kind).isEqualTo(FireKind.REGULAR)
        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-29T06:30"))
    }

    @Test
    fun `pending snooze in the future is kept on reschedule`() = runTest {
        val id = engine.save(wakeUp).id
        val snoozeUntil = local("2026-09-28T06:40")
        repository.updateRuntime(AlarmRuntimeState(id, snoozeUntil, TriggerKind.SNOOZE, snoozeCount = 1))
        clock.now = local("2026-09-28T06:36")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, snoozeUntil, FireKind.SNOOZE))
        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(1)
    }

    @Test
    fun `missed snooze of a disabled one shot alarm is still caught up`() = runTest {
        val id = engine.save(wakeUp).id
        repository.setEnabled(id, false) // разовый выключился при срабатывании, затем отложен
        repository.updateRuntime(AlarmRuntimeState(id, local("2026-09-28T06:40"), TriggerKind.SNOOZE, snoozeCount = 1))
        clock.now = local("2026-09-28T06:42")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled[id]?.kind).isEqualTo(FireKind.CATCH_UP)
    }

    @Test
    fun `when the system refuses the alarm the failure is logged and the moment kept for retry`() = runTest {
        scheduler.accept = false

        val id = engine.save(wakeUp).id

        assertThat(log.events).contains(AlarmEvent.ScheduleFailed(id, local("2026-09-28T06:30")))
        assertThat(repository.runtimes[id]?.nextTriggerAt).isEqualTo(local("2026-09-28T06:30"))
    }

    @Test
    fun `concurrent reschedules produce one consistent schedule`() = runTest {
        val id = engine.save(wakeUp).id

        val results = listOf(
            async { engine.rescheduleAll(RescheduleReason.TIME_SET) },
            async { engine.rescheduleAll(RescheduleReason.TIMEZONE_CHANGED) },
        ).map { it.await() }

        assertThat(results).containsExactly(1, 1)
        assertThat(scheduler.scheduled.keys).containsExactly(id)
    }

    @Test
    fun `setting enabled on a missing alarm does nothing`() = runTest {
        engine.setEnabled(AlarmId(99), true)

        assertThat(scheduler.scheduleCalls).isEqualTo(0)
    }

    @Test
    fun `log lines contain no user data`() {
        val line = AlarmEvent.Scheduled(AlarmId(7), local("2026-09-28T06:30"), FireKind.REGULAR).toLogLine()

        assertThat(line).isEqualTo("SCHEDULED id=7 at=2026-09-28T03:30:00Z kind=REGULAR")
    }
}
