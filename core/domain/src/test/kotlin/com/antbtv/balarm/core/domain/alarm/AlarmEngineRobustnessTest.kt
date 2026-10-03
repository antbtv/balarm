package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.TriggerKind
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** Сценарии отказов из ревью T04/T05: ни один не должен оставить будильник без звонка или позвонить дважды. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEngineRobustnessTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(local("2026-09-28T05:00"), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val engine = AlarmEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log)

    private fun local(iso: String) = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private val daily = Alarm(time = LocalTime.of(6, 30), repeatDays = DayOfWeek.entries.toSet())

    @Test
    fun `snooze refused by the system is reported as not allowed and keeps the regular schedule`() = runTest {
        val id = engine.save(daily)
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        val regular = repository.runtimes.getValue(id)
        scheduler.accept = false

        assertThat(engine.snooze(id)).isEqualTo(SnoozeResult.NotAllowed)
        assertThat(repository.runtimes.getValue(id)).isEqualTo(regular)
    }

    @Test
    fun `one failing alarm does not stop rescheduling of the others`() = runTest {
        val first = engine.save(daily)
        val broken = engine.save(daily.copy(time = LocalTime.of(7, 0)))
        val third = engine.save(daily.copy(time = LocalTime.of(8, 0)))
        scheduler.scheduled.clear()
        scheduler.throwFor += broken

        val count = engine.rescheduleAll(RescheduleReason.LOCKED_BOOT)

        assertThat(count).isEqualTo(2)
        assertThat(scheduler.scheduled.keys).containsExactly(first, third)
        assertThat(log.events).contains(AlarmEvent.RescheduleError(broken, "IllegalStateException"))
    }

    @Test
    fun `alarms refused by the system are not counted as rescheduled`() = runTest {
        engine.save(daily)
        scheduler.accept = false

        assertThat(engine.rescheduleAll(RescheduleReason.BOOT)).isEqualTo(0)
    }

    @Test
    fun `failed delete keeps the alarm scheduled`() = runTest {
        val id = engine.save(daily)
        repository.failOnDelete = IOException("disk")

        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { engine.delete(id) } }

        assertThat(scheduler.scheduled).containsKey(id)
        assertThat(repository.get(id)).isNotNull()
    }

    @Test
    fun `cancelled caller does not interrupt a started operation`() = runTest {
        val id = engine.save(daily)
        engine.setEnabled(id, false)
        val gate = CompletableDeferred<Unit>()
        repository.transactionGate = gate

        val job = launch { engine.setEnabled(id, true) }
        advanceUntilIdle()
        job.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(repository.get(id)?.enabled).isTrue()
        assertThat(scheduler.scheduled).containsKey(id)
    }

    @Test
    fun `when scheduling the next occurrence fails the real alarm still rings`() = runTest {
        val id = engine.save(daily)
        scheduler.throwFor += id
        clock.now = local("2026-09-28T06:30")

        val decision = engine.onFired(id, clock.now, FireKind.REGULAR)

        assertThat(decision).isEqualTo(FireDecision.Ring(daily.copy(id = id), canSnooze = true, snoozesLeft = 3))
    }

    @Test
    fun `busy engine does not delay the ring beyond the lock timeout`() = runTest {
        val id = engine.save(daily)
        val gate = CompletableDeferred<Unit>()
        repository.transactionGate = gate
        launch { engine.rescheduleAll(RescheduleReason.BOOT) }
        advanceUntilIdle()

        val decision = async { engine.onFired(id, clock.now, FireKind.REGULAR) }
        advanceUntilIdle()

        assertThat((decision.await() as FireDecision.Ring).degraded).isTrue()
        assertThat(log.events).contains(AlarmEvent.FireDegraded(id, "EngineBusy"))
        gate.complete(Unit)
    }

    @Test
    fun `degraded ring of a one-shot is recorded once the engine is free`() = runTest {
        val id = engine.save(Alarm(time = LocalTime.of(6, 30)))
        clock.now = local("2026-09-28T06:30")
        val gate = CompletableDeferred<Unit>()
        repository.transactionGate = gate
        launch { engine.rescheduleAll(RescheduleReason.BOOT) }
        advanceUntilIdle()
        val decision = async { engine.onFired(id, clock.now, FireKind.REGULAR) }
        advanceUntilIdle()
        assertThat((decision.await() as FireDecision.Ring).degraded).isTrue()

        val record = async { engine.recordDegraded(id, clock.now, FireKind.REGULAR) }
        repository.transactionGate = null
        gate.complete(Unit)
        record.await()

        assertThat(repository.get(id)?.enabled).isFalse() // завтра не зазвонит
        assertThat(repository.getRuntime(id)?.lastFiredAt).isEqualTo(clock.now)
        // повторная доставка того же срабатывания (например, CATCH_UP после reboot) — дубликат
        assertThat(engine.onFired(id, clock.now, FireKind.REGULAR)).isEqualTo(FireDecision.Skip(SkipReason.DUPLICATE))
    }

    @Test
    fun `pending snooze is pulled back when the clock is set back a day`() = runTest {
        val id = engine.save(daily)
        repository.updateRuntime(AlarmRuntimeState(id, local("2026-09-29T06:35"), TriggerKind.SNOOZE, snoozeCount = 1))
        clock.now = local("2026-09-28T06:31") // время перевели на сутки назад

        engine.rescheduleAll(RescheduleReason.TIME_SET)

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T06:36"), FireKind.SNOOZE))
    }

    @Test
    fun `one shot missed longer than grace is disabled instead of moved to tomorrow`() = runTest {
        val id = engine.save(Alarm(time = LocalTime.of(6, 30)))
        clock.now = local("2026-09-28T09:00")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(repository.get(id)?.enabled).isFalse()
        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(log.events).contains(AlarmEvent.FireSkipped(id, SkipReason.MISSED))
    }

    @Test
    fun `double snooze tap spends the limit only once`() = runTest {
        val id = engine.save(daily)
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)

        val first = engine.snooze(id)
        val second = engine.snooze(id)

        assertThat(second).isEqualTo(first)
        assertThat(repository.runtimes.getValue(id).snoozeCount).isEqualTo(1)
    }

    @Test
    fun `clock set back after ringing does not ring again the same day`() = runTest {
        val id = engine.save(daily)
        clock.now = local("2026-09-28T06:30").plusSeconds(2)
        engine.onFired(id, local("2026-09-28T06:30"), FireKind.REGULAR)
        clock.now = local("2026-09-28T05:30") // перевели на час назад

        engine.rescheduleAll(RescheduleReason.TIME_SET)

        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-29T06:30"))
    }

    @Test
    fun `duplicate delivery of the same trigger does not ring twice`() = runTest {
        val id = engine.save(daily)
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)

        val again = engine.onFired(id, local("2026-09-28T06:30"), FireKind.REGULAR)

        assertThat(again).isEqualTo(FireDecision.Skip(SkipReason.DUPLICATE))
    }

    @Test
    fun `operations never run concurrently`() = runTest {
        repeat(3) { engine.save(daily.copy(time = LocalTime.of(6 + it, 0))) }

        List(5) { async { engine.rescheduleAll(RescheduleReason.TIME_SET) } }.forEach { it.await() }

        assertThat(repository.maxConcurrentTransactions).isEqualTo(1)
    }

    @Test
    fun `resume of a deleted alarm is skipped`() = runTest {
        assertThat(
            engine.onFired(AlarmId(9), clock.now, FireKind.RESUME),
        ).isEqualTo(FireDecision.Skip(SkipReason.DELETED))
    }

    @Test
    fun `pending snooze survives a timezone change unchanged`() = runTest {
        val id = engine.save(daily)
        val until = local("2026-09-28T06:40")
        repository.updateRuntime(AlarmRuntimeState(id, until, TriggerKind.SNOOZE, snoozeCount = 1))
        clock.now = local("2026-09-28T06:36")
        clock.zoneId = ZoneId.of("Asia/Tokyo")

        engine.rescheduleAll(RescheduleReason.TIMEZONE_CHANGED)

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, until, FireKind.SNOOZE))
        assertThat(Duration.between(clock.now, until)).isEqualTo(Duration.ofMinutes(4))
    }
}
