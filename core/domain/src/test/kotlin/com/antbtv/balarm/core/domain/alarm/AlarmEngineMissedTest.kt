package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.RecordingMissedNotifier
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Уведомление «пропущен будильник» при перепланировании (FR-REL-6). */
class AlarmEngineMissedTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(local("2026-09-28T05:00"), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val notifier = RecordingMissedNotifier()
    private val engine = testEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log, notifier)

    private fun local(iso: String) = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private val daily = Alarm(time = LocalTime.of(6, 30), repeatDays = DayOfWeek.entries.toSet())

    @Test
    fun `one shot missed longer than grace is disabled and reported`() = runTest {
        val id = engine.save(Alarm(time = LocalTime.of(6, 30))).id
        clock.now = local("2026-09-28T09:00")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(repository.get(id)?.enabled).isFalse()
        assertThat(notifier.missed.map { it.first.id to it.second }).containsExactly(id to local("2026-09-28T06:30"))
    }

    @Test
    fun `repeating alarm missed longer than grace moves to the next day and is reported`() = runTest {
        val id = engine.save(daily).id
        clock.now = local("2026-09-28T09:00")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled.getValue(id).triggerAt).isEqualTo(local("2026-09-29T06:30"))
        assertThat(notifier.missed.map { it.second }).containsExactly(local("2026-09-28T06:30"))
    }

    @Test
    fun `repeated reschedule does not report the same miss twice`() = runTest {
        engine.save(daily)
        clock.now = local("2026-09-28T09:00")

        engine.rescheduleAll(RescheduleReason.BOOT)
        engine.rescheduleAll(RescheduleReason.TIME_SET)

        assertThat(notifier.missed).hasSize(1)
    }

    @Test
    fun `miss within grace is caught up and not reported`() = runTest {
        engine.save(daily)
        clock.now = local("2026-09-28T06:35")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(notifier.missed).isEmpty()
    }

    @Test
    fun `disabled alarm is never reported`() = runTest {
        val id = engine.save(daily).id
        engine.setEnabled(id, false)
        clock.now = local("2026-09-28T09:00")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(notifier.missed).isEmpty()
    }

    @Test
    fun `failing notifier does not break rescheduling`() = runTest {
        val failing = testEngine(
            repository = repository,
            scheduler = scheduler,
            clock = clock,
            flags = ConfigFeatureFlagProvider,
            log = log,
            missedNotifier = MissedAlarmNotifier { _, _ -> error("boom") },
        )
        val id = failing.save(daily).id
        clock.now = local("2026-09-28T09:00")

        failing.rescheduleAll(RescheduleReason.BOOT)

        assertThat(scheduler.scheduled.getValue(id).triggerAt).isEqualTo(local("2026-09-29T06:30"))
    }

    @Test
    fun `alarm that already rang is not reported even if overdue`() = runTest {
        val id = engine.save(daily).id
        val at = local("2026-09-28T06:30")
        repository.updateRuntime(AlarmRuntimeState(id, at, lastFiredAt = at))
        clock.now = local("2026-09-28T09:00")

        engine.rescheduleAll(RescheduleReason.APP_LAUNCH)

        assertThat(notifier.missed).isEmpty()
    }

    @Test
    fun `disabled alarm with a stale trigger is not reported`() = runTest {
        val id = engine.save(daily.copy(enabled = false)).id
        repository.updateRuntime(AlarmRuntimeState(id, local("2026-09-28T06:30")))
        clock.now = local("2026-09-28T09:00")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(notifier.missed).isEmpty()
    }

    @Test
    fun `just under grace is caught up and the pending catch-up is not reported later`() = runTest {
        engine.save(daily)
        clock.now = local("2026-09-28T06:39:59")
        engine.rescheduleAll(RescheduleReason.BOOT)
        assertThat(notifier.missed).isEmpty()

        clock.now = local("2026-09-28T06:40")
        engine.rescheduleAll(RescheduleReason.BOOT)
        assertThat(notifier.missed).isEmpty()
    }

    @Test
    fun `failed runtime write does not report so the next reschedule cannot duplicate it`() = runTest {
        val id = engine.save(daily).id
        clock.now = local("2026-09-28T09:00")
        repository.failRuntimeFor += id

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(notifier.missed).isEmpty()
    }
}
