package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmScheduler
import com.antbtv.balarm.core.domain.testing.FakeSettingsRepository
import com.antbtv.balarm.core.domain.testing.MutableClock
import com.antbtv.balarm.core.domain.testing.RecordingEventLog
import com.antbtv.balarm.core.domain.testing.testEngine
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AutoStop
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Маркер «звонит» и возврат прерванного звонка (ADR-020 §3–4, ADR-021 §6, R20). */
class AlarmEngineResumeTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val clock = MutableClock(local("2026-09-28T05:00"), moscow)
    private val repository = FakeAlarmRepository()
    private val scheduler = FakeAlarmScheduler()
    private val log = RecordingEventLog()
    private val settings = FakeSettingsRepository()
    private val engine = testEngine(repository, scheduler, clock, ConfigFeatureFlagProvider, log, settings = settings)

    private fun local(iso: String) = LocalDateTime.parse(iso).atZone(moscow).toInstant()

    private val daily = Alarm(
        time = LocalTime.of(6, 30),
        repeatDays = DayOfWeek.entries.toSet(),
        snooze = SnoozeSettings(Duration.ofMinutes(5), 3),
    )

    private suspend fun ringing(alarm: Alarm = daily): AlarmId {
        val id = engine.save(alarm).id
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.REGULAR)
        return id
    }

    private fun resumeRequest(id: AlarmId) = scheduler.scheduled[id]?.takeIf { it.kind == FireKind.RESUME }

    @Test
    fun `firing marks the alarm as ringing`() = runTest {
        val id = ringing()

        assertThat(repository.runtimes.getValue(id).ringingSince).isEqualTo(local("2026-09-28T06:30"))
    }

    @Test
    fun `dismiss snooze and stale skip clear the marker`() = runTest {
        val dismissed = ringing()
        engine.dismiss(dismissed, DismissReason.USER)
        assertThat(repository.runtimes.getValue(dismissed).ringingSince).isNull()

        val snoozed = ringing()
        engine.snooze(snoozed)
        assertThat(repository.runtimes.getValue(snoozed).ringingSince).isNull()

        val stale = engine.save(daily).id
        clock.now = local("2026-09-29T08:00")
        engine.onFired(stale, local("2026-09-29T06:30"), FireKind.REGULAR)
        assertThat(repository.runtimes.getValue(stale).ringingSince).isNull()
    }

    @Test
    fun `reschedule inside the window resumes the ring and keeps the regular plan`() = runTest {
        val id = ringing()
        val regular = repository.runtimes.getValue(id).nextTriggerAt
        clock.now = local("2026-09-28T06:31")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(resumeRequest(id)?.triggerAt).isEqualTo(clock.now + RingingPolicy.CRASH_RESUME_DELAY)
        assertThat(repository.runtimes.getValue(id).nextTriggerAt).isEqualTo(regular)
        assertThat(repository.runtimes.getValue(id).ringingSince).isEqualTo(local("2026-09-28T06:30"))
    }

    @Test
    fun `app launch right after a crash re-arm does not drop the resume`() = runTest {
        val id = ringing()
        clock.now = local("2026-09-28T06:30:01")

        engine.rescheduleAll(RescheduleReason.APP_LAUNCH)
        engine.rescheduleAll(RescheduleReason.APP_LAUNCH)

        assertThat(resumeRequest(id)).isNotNull()
    }

    @Test
    fun `reschedule after the window clears the marker without resuming`() = runTest {
        val id = ringing()
        clock.now = local("2026-09-28T07:01") // 31 мин > автостоп 30 мин

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(resumeRequest(id)).isNull()
        assertThat(repository.runtimes.getValue(id).ringingSince).isNull()
    }

    @Test
    fun `window follows the auto stop setting`() = runTest {
        settings.update { it.copy(autoStop = AutoStop.MIN_5) }
        val id = ringing()
        clock.now = local("2026-09-28T06:36")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(resumeRequest(id)).isNull()
    }

    @Test
    fun `never auto stop is capped at one hour`() = runTest {
        settings.update { it.copy(autoStop = AutoStop.NEVER) }
        val id = ringing()

        clock.now = local("2026-09-28T07:29")
        engine.rescheduleAll(RescheduleReason.BOOT)
        assertThat(resumeRequest(id)).isNotNull()

        clock.now = local("2026-09-28T07:31")
        engine.rescheduleAll(RescheduleReason.BOOT)
        assertThat(repository.runtimes.getValue(id).ringingSince).isNull()
    }

    @Test
    fun `marker from the future after a clock rollback is dropped`() = runTest {
        val id = ringing()
        clock.now = local("2026-09-28T06:00")

        engine.rescheduleAll(RescheduleReason.TIME_SET)

        assertThat(resumeRequest(id)).isNull()
        assertThat(repository.runtimes.getValue(id).ringingSince).isNull()
    }

    @Test
    fun `a ringing one shot is resumed and stays disabled`() = runTest {
        val id = ringing(Alarm(time = LocalTime.of(6, 30)))
        clock.now = local("2026-09-28T06:31")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(resumeRequest(id)).isNotNull()
        assertThat(repository.get(id)?.enabled).isFalse()
    }

    @Test
    fun `resume fire stamps the marker when it was missing and keeps an existing one`() = runTest {
        val id = engine.save(daily).id
        clock.now = local("2026-09-28T06:30")
        engine.onFired(id, clock.now, FireKind.RESUME)
        assertThat(repository.runtimes.getValue(id).ringingSince).isEqualTo(clock.now)

        clock.now = local("2026-09-28T06:32")
        engine.onFired(id, clock.now, FireKind.RESUME)
        assertThat(repository.runtimes.getValue(id).ringingSince).isEqualTo(local("2026-09-28T06:30"))
    }

    @Test
    fun `disabling a ringing alarm drops the marker so it is not resumed`() = runTest {
        val id = ringing()
        engine.setEnabled(id, false)
        clock.now = local("2026-09-28T06:31")

        engine.rescheduleAll(RescheduleReason.BOOT)

        assertThat(repository.runtimes.getValue(id).ringingSince).isNull()
        assertThat(resumeRequest(id)).isNull()
    }

    @Test
    fun `saving a ringing alarm as disabled drops the marker`() = runTest {
        val id = ringing()
        engine.save(repository.get(id)!!.copy(enabled = false))

        assertThat(repository.runtimes.getValue(id).ringingSince).isNull()
    }

    @Test
    fun `restoring the regular plan replaces an ignored resume`() = runTest {
        val id = ringing()
        val regular = repository.runtimes.getValue(id).nextTriggerAt
        clock.now = local("2026-09-28T06:31")
        engine.rescheduleAll(RescheduleReason.APP_LAUNCH)
        assertThat(resumeRequest(id)).isNotNull()

        engine.restoreRegularPlan(id)

        assertThat(scheduler.scheduled[id]).isEqualTo(ScheduleRequest(id, regular!!, FireKind.REGULAR))
    }
}
