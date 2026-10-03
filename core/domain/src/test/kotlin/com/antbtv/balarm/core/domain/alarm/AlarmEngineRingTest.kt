package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.TriggerKind
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AlarmEngineRingTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(local("2026-09-28T05:00"), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private var flags: FeatureFlagProvider = ConfigFeatureFlagProvider
    private val engine by lazy { AlarmEngine(repository, scheduler, clock, { flags.isEnabled(it) }, log) }

    private fun local(iso: String) = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private val oneShot = Alarm(time = LocalTime.of(6, 30))
    private val weekdays = Alarm(time = LocalTime.of(6, 30), repeatDays = DayOfWeek.entries.take(5).toSet())

    private suspend fun fireAt(alarm: Alarm, iso: String): Pair<AlarmId, FireDecision> {
        val id = engine.save(alarm)
        clock.now = local(iso)
        return id to engine.onFired(id, local("2026-09-28T06:30"), FireKind.REGULAR)
    }

    @Test
    fun `firing a one shot alarm rings and disables it`() = runTest {
        val (id, decision) = fireAt(oneShot, "2026-09-28T06:30")

        assertThat(decision).isEqualTo(FireDecision.Ring(oneShot.copy(id = id, enabled = false), true, 3))
        assertThat(repository.get(id)?.enabled).isFalse()
        assertThat(scheduler.scheduled).doesNotContainKey(id)
        assertThat(repository.runtimes[id]?.lastFiredAt).isEqualTo(local("2026-09-28T06:30"))
    }

    @Test
    fun `firing a repeating alarm immediately schedules the next occurrence`() = runTest {
        val (id, decision) = fireAt(weekdays, "2026-09-28T06:30:02")

        assertThat(decision).isInstanceOf(FireDecision.Ring::class.java)
        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-29T06:30"), FireKind.REGULAR))
        assertThat(log.events).contains(AlarmEvent.Fired(id, FireKind.REGULAR, lateMs = 2_000))
    }

    @Test
    fun `stale trigger is skipped and rescheduled`() = runTest {
        val (id, decision) = fireAt(weekdays, "2026-09-28T07:00")

        assertThat(decision).isEqualTo(FireDecision.Skip(SkipReason.STALE))
        assertThat(scheduler.scheduled[id]?.triggerAt).isEqualTo(local("2026-09-29T06:30"))
    }

    @Test
    fun `trigger for a deleted alarm is skipped`() = runTest {
        val decision = engine.onFired(AlarmId(42), local("2026-09-28T06:30"), FireKind.REGULAR)

        assertThat(decision).isEqualTo(FireDecision.Skip(SkipReason.DELETED))
        assertThat(scheduler.cancelled).contains(AlarmId(42))
    }

    @Test
    fun `when storage fails the alarm still rings with defaults`() = runTest {
        val broken = object : AlarmRepository by repository {
            override suspend fun <R> transaction(block: suspend () -> R): R = throw IOException("disk")
        }
        val degradedEngine = AlarmEngine(broken, scheduler, clock, ConfigFeatureFlagProvider, log)
        clock.now = local("2026-09-28T06:30:40")

        val decision = degradedEngine.onFired(AlarmId(5), local("2026-09-28T06:30"), FireKind.REGULAR)

        assertThat(decision).isEqualTo(
            FireDecision.Ring(Alarm(id = AlarmId(5), time = LocalTime.of(6, 30)), false, 0, degraded = true),
        )
        assertThat(log.events.last()).isEqualTo(AlarmEvent.FireDegraded(AlarmId(5), "IOException"))
    }

    @Test
    fun `snooze schedules a trigger after the interval and counts it`() = runTest {
        val (id, _) = fireAt(weekdays, "2026-09-28T06:30")

        val result = engine.snooze(id)

        assertThat(result).isEqualTo(SnoozeResult.Snoozed(local("2026-09-28T06:35")))
        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-28T06:35"), FireKind.SNOOZE))
        assertThat(repository.runtimes[id]?.nextTriggerKind).isEqualTo(TriggerKind.SNOOZE)
        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(1)
    }

    @Test
    fun `snooze limit is enforced across snooze triggers`() = runTest {
        val (id, _) = fireAt(
            weekdays.copy(snooze = SnoozeSettings(Duration.ofMinutes(5), maxCount = 2)),
            "2026-09-28T06:30",
        )

        val rings = List(2) {
            assertThat(engine.snooze(id)).isInstanceOf(SnoozeResult.Snoozed::class.java)
            clock.advance(Duration.ofMinutes(5))
            engine.onFired(id, clock.now, FireKind.SNOOZE) as FireDecision.Ring
        }

        assertThat(rings.map { it.snoozesLeft }).containsExactly(1, 0).inOrder()
        assertThat(rings.last().canSnooze).isFalse()
        assertThat(engine.snooze(id)).isEqualTo(SnoozeResult.NotAllowed)
    }

    @Test
    fun `snooze is not allowed when the feature flag is off`() = runTest {
        flags = FeatureFlagProvider { it != Feature.SNOOZE }
        val (id, decision) = fireAt(weekdays, "2026-09-28T06:30")

        assertThat((decision as FireDecision.Ring).canSnooze).isFalse()
        assertThat(engine.snooze(id)).isEqualTo(SnoozeResult.NotAllowed)
    }

    @Test
    fun `snooze is not allowed when disabled in alarm settings`() = runTest {
        val (id, decision) = fireAt(weekdays.copy(snooze = SnoozeSettings.DISABLED), "2026-09-28T06:30")

        assertThat((decision as FireDecision.Ring).canSnooze).isFalse()
        assertThat(engine.snooze(id)).isEqualTo(SnoozeResult.NotAllowed)
    }

    @Test
    fun `dismiss after snooze restores the regular schedule and resets the counter`() = runTest {
        val (id, _) = fireAt(weekdays, "2026-09-28T06:30")
        engine.snooze(id)

        engine.dismiss(id, DismissReason.USER)

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, local("2026-09-29T06:30"), FireKind.REGULAR))
        assertThat(repository.runtimes[id]?.snoozeCount).isEqualTo(0)
        assertThat(log.events.last()).isEqualTo(AlarmEvent.Dismissed(id, DismissReason.USER))
    }

    @Test
    fun `dismissing a snoozed one shot alarm leaves nothing scheduled`() = runTest {
        val (id, _) = fireAt(oneShot, "2026-09-28T06:30")
        engine.snooze(id)

        engine.dismiss(id, DismissReason.AUTO_STOP)

        assertThat(scheduler.scheduled).doesNotContainKey(id)
    }

    @Test
    fun `snoozed one shot rings again even though it is disabled`() = runTest {
        val (id, _) = fireAt(oneShot, "2026-09-28T06:30")
        engine.snooze(id)
        clock.now = local("2026-09-28T06:35")

        val decision = engine.onFired(id, clock.now, FireKind.SNOOZE)

        assertThat(decision).isInstanceOf(FireDecision.Ring::class.java)
        assertThat((decision as FireDecision.Ring).snoozesLeft).isEqualTo(2)
    }

    @Test
    fun `resume after a crash rings without touching runtime`() = runTest {
        val (id, _) = fireAt(weekdays, "2026-09-28T06:30")
        val runtimeBefore = repository.runtimes.getValue(id)
        clock.now = local("2026-09-28T06:31")

        val decision = engine.onFired(id, clock.now, FireKind.RESUME)

        assertThat(decision).isInstanceOf(FireDecision.Ring::class.java)
        assertThat(repository.runtimes.getValue(id)).isEqualTo(runtimeBefore)
    }

    @Test
    fun `snooze or dismiss of a missing alarm is harmless`() = runTest {
        assertThat(engine.snooze(AlarmId(77))).isEqualTo(SnoozeResult.NotAllowed)
        engine.dismiss(AlarmId(77), DismissReason.USER)

        assertThat(scheduler.scheduleCalls).isEqualTo(0)
    }
}
