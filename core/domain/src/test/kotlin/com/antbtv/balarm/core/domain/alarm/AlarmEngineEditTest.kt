package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.TriggerKind
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** Контракт движка для UI редактора и списка (ADR-011 §5): что сохраняется при правке и тумблере. */
class AlarmEngineEditTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(local("2026-09-28T05:00"), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val engine = testEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log)

    private fun local(iso: String): Instant = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private val oneShot = Alarm(time = LocalTime.of(6, 30))
    private val daily = Alarm(time = LocalTime.of(6, 30), repeatDays = DayOfWeek.entries.toSet())

    /** Будильник отзвонил в 06:30 и отложен до 06:35; сейчас 06:31. */
    private suspend fun snoozed(alarm: Alarm): AlarmId {
        val id = engine.save(alarm).id
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        engine.snooze(id)
        clock.now = local("2026-09-28T06:31")
        return id
    }

    @Test
    fun `save reports the id and the trigger handed to the system`() = runTest {
        val result = engine.save(oneShot)

        assertThat(result).isEqualTo(ScheduleResult(result.id, local("2026-09-28T06:30"), scheduled = true))
        assertThat(result.id.isSaved).isTrue()
    }

    @Test
    fun `save of a disabled alarm reports no trigger but is not a failure`() = runTest {
        val result = engine.save(oneShot.copy(enabled = false))

        assertThat(result.nextTriggerAt).isNull()
        assertThat(result.scheduled).isTrue()
    }

    @Test
    fun `save reports a refusal of the system`() = runTest {
        scheduler.accept = false

        val result = engine.save(oneShot)

        assertThat(result.scheduled).isFalse()
        assertThat(result.nextTriggerAt).isEqualTo(local("2026-09-28T06:30"))
    }

    @Test
    fun `saving another field during snooze keeps the pending snooze`() = runTest {
        val id = snoozed(daily)

        val result = engine.save(daily.copy(id = id, label = "Gym"))

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T06:35"), FireKind.SNOOZE))
        assertThat(result.nextTriggerAt).isEqualTo(local("2026-09-28T06:35"))
        assertThat(repository.runtimes[id]).isEqualTo(
            AlarmRuntimeState(id, local("2026-09-28T06:35"), TriggerKind.SNOOZE, 1, local("2026-09-28T06:30")),
        )
        assertThat(repository.get(id)?.label).isEqualTo("Gym")
    }

    @Test
    fun `moving the time before the pending snooze wins and ends the snooze cycle`() = runTest {
        val id = snoozed(daily)

        engine.save(daily.copy(id = id, time = LocalTime.of(6, 33)))

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T06:33"), FireKind.REGULAR))
        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(0)
    }

    @Test
    fun `moving the time after the pending snooze keeps the snooze first`() = runTest {
        val id = snoozed(daily)

        engine.save(daily.copy(id = id, time = LocalTime.of(7, 0)))

        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-28T06:35"))

        clock.now = local("2026-09-28T06:35")
        val decision = engine.onFired(id, clock.now, FireKind.SNOOZE)

        assertThat(decision).isInstanceOf(FireDecision.Ring::class.java)
        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T07:00"), FireKind.REGULAR))
    }

    @Test
    fun `a one shot edited during snooze still rings at its new time after the snooze`() = runTest {
        val id = snoozed(oneShot)

        engine.save(oneShot.copy(id = id, time = LocalTime.of(7, 0), enabled = true))
        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-28T06:35"))

        clock.now = local("2026-09-28T06:35")
        engine.onFired(id, clock.now, FireKind.SNOOZE)

        assertThat(repository.get(id)?.enabled).isTrue()
        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T07:00"), FireKind.REGULAR))
    }

    @Test
    fun `save keeps lastFiredAt so a clock set back does not ring twice`() = runTest {
        val id = engine.save(daily).id
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        engine.dismiss(id, DismissReason.USER)
        clock.now = local("2026-09-28T05:30") // часы перевели назад

        engine.save(daily.copy(id = id, label = "x"))

        assertThat(repository.runtimes[id]?.lastFiredAt).isEqualTo(local("2026-09-28T06:30"))
        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-29T06:30"))
    }

    @Test
    fun `disabling cancels everything including a pending snooze`() = runTest {
        val id = snoozed(daily)

        val result = engine.setEnabled(id, false)

        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(scheduler.cancelled).contains(id)
        assertThat(result).isEqualTo(ScheduleResult(id, null, scheduled = true))
        assertThat(repository.runtimes[id]).isEqualTo(
            AlarmRuntimeState(id, null, TriggerKind.REGULAR, 0, local("2026-09-28T06:30")),
        )
    }

    @Test
    fun `a one shot with a pending snooze can be switched off although it is already disabled`() = runTest {
        val id = snoozed(oneShot)
        assertThat(repository.get(id)?.enabled).isFalse()

        engine.setEnabled(id, false)

        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(repository.runtimes[id]?.nextTriggerAt).isNull()
    }

    @Test
    fun `enabling restores the regular schedule and reports the trigger`() = runTest {
        val id = engine.save(daily.copy(enabled = false)).id

        val result = engine.setEnabled(id, true)

        assertThat(result).isEqualTo(ScheduleResult(id, local("2026-09-28T06:30"), scheduled = true))
        assertThat(scheduler.scheduled[id]?.kind).isEqualTo(FireKind.REGULAR)
    }

    @Test
    fun `enabling an unknown alarm reports nothing`() = runTest {
        assertThat(engine.setEnabled(AlarmId(99), true)).isNull()
        assertThat(scheduler.scheduleCalls).isEqualTo(0)
    }

    @Test
    fun `editing a ringing alarm keeps its snooze counter and dismiss plans by the new settings`() = runTest {
        val id = snoozed(daily)
        clock.now = local("2026-09-28T06:35")
        engine.onFired(id, clock.now, FireKind.SNOOZE) // звонит после первого snooze

        engine.save(daily.copy(id = id, time = LocalTime.of(8, 0)))

        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(1)

        engine.dismiss(id, DismissReason.USER)

        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(0)
        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T08:00"), FireKind.REGULAR))
    }

    @Test
    fun `deleting a ringing alarm leaves dismiss and resume harmless`() = runTest {
        val id = engine.save(daily).id
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)

        engine.delete(id)
        engine.dismiss(id, DismissReason.USER)
        val resume = engine.onFired(id, clock.now, FireKind.RESUME)

        assertThat(resume).isEqualTo(FireDecision.Skip(SkipReason.DELETED))
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(repository.runtimes).isEmpty()
    }

    @Test
    fun `an unedited one shot stays disabled after its snooze rings`() = runTest {
        val id = snoozed(oneShot)

        clock.now = local("2026-09-28T06:35")
        val decision = engine.onFired(id, clock.now, FireKind.SNOOZE)

        assertThat(decision).isInstanceOf(FireDecision.Ring::class.java)
        assertThat(repository.get(id)?.enabled).isFalse()
        assertThat(scheduler.scheduled).doesNotContainKey(id)
    }

    @Test
    fun `saving only the label of a fired one shot re-arms it for the next day`() = runTest {
        val id = snoozed(oneShot)

        engine.save(oneShot.copy(id = id, label = "Gym")) // «Сохранить» всегда включает (ADR-011 §5)
        clock.now = local("2026-09-28T06:35")
        engine.onFired(id, clock.now, FireKind.SNOOZE)

        assertThat(repository.get(id)?.enabled).isTrue()
        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-29T06:30"))
    }

    @Test
    fun `resume of an edited one shot whose new time is ahead does not disable it`() = runTest {
        val id = snoozed(oneShot)
        engine.save(oneShot.copy(id = id, time = LocalTime.of(7, 0)))
        clock.now = local("2026-09-28T06:35")
        engine.onFired(id, clock.now, FireKind.SNOOZE)
        clock.now = local("2026-09-28T06:36") // процесс упал, пока звонил snooze

        engine.onFired(id, clock.now, FireKind.RESUME)

        assertThat(repository.get(id)?.enabled).isTrue()
        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T07:00"), FireKind.REGULAR))
    }

    @Test
    fun `save keeps a pending catch up`() = runTest {
        val id = engine.save(daily).id
        val catchUpAt = clock.now + AlarmEngine.CATCH_UP_DELAY
        repository.updateRuntime(AlarmRuntimeState(id, catchUpAt, TriggerKind.CATCH_UP))

        engine.save(daily.copy(id = id, label = "x"))

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, catchUpAt, FireKind.CATCH_UP))
    }

    @Test
    fun `a pending snooze that is too far is clamped like on reschedule`() = runTest {
        val id = engine.save(daily).id
        repository.updateRuntime(AlarmRuntimeState(id, local("2026-09-28T06:00"), TriggerKind.SNOOZE, 1)) // часы назад

        engine.save(daily.copy(id = id, label = "x"))

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T05:05"), FireKind.SNOOZE))
    }

    @Test
    fun `when the snooze and the new trigger coincide the regular trigger wins`() = runTest {
        val id = snoozed(daily)
        clock.now = local("2026-09-28T06:00")
        repository.updateRuntime(AlarmRuntimeState(id, local("2026-09-28T06:30"), TriggerKind.SNOOZE, 1))

        engine.save(daily.copy(id = id, label = "x"))

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T06:30"), FireKind.REGULAR))
        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(0)
    }

    @Test
    fun `turning snooze off in the editor keeps the already pending snooze`() = runTest {
        val id = snoozed(daily)

        engine.save(daily.copy(id = id, snooze = SnoozeSettings.DISABLED))

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T06:35"), FireKind.SNOOZE))
    }

    @Test
    fun `enabling an already enabled alarm does not erase its pending snooze`() = runTest {
        val id = snoozed(daily)

        val result = engine.setEnabled(id, true)

        assertThat(scheduler.scheduled[id]?.kind).isEqualTo(FireKind.SNOOZE)
        assertThat(result?.nextTriggerAt).isEqualTo(local("2026-09-28T06:35"))
        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(1)
    }

    @Test
    fun `the reserved test alarm is rejected by every mutation`() = runTest {
        val rejected = listOf<suspend () -> Unit>(
            { engine.save(oneShot.copy(id = AlarmId.TEST)) },
            { engine.setEnabled(AlarmId.TEST, true) },
            { engine.delete(AlarmId.TEST) },
        )
        rejected.forEach { call ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { call() } }
        }
        assertThat(scheduler.scheduleCalls).isEqualTo(0)
    }
}
