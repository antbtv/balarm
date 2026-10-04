package com.antbtv.balarm.core.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.model.AlarmId
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class AlarmSchedulerImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val uiIntents = object : AlarmUiIntents {
        override fun ringingScreen() = Intent("ringing").setPackage(context.packageName)

        override fun alarmList() = Intent("list").setPackage(context.packageName)
    }
    private val scheduler = AlarmSchedulerImpl(context, uiIntents)

    private val at = Instant.parse("2026-09-29T03:30:00Z")

    @Before
    fun grantExactAlarms() = ShadowAlarmManager.setCanScheduleExactAlarms(true) // USE_EXACT_ALARM в :app

    @After
    fun tearDown() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @Test
    fun `schedules an alarm clock with the exact trigger time`() {
        assertThat(scheduler.schedule(ScheduleRequest(AlarmId(1), at, FireKind.REGULAR))).isTrue()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertThat(alarm.triggerAtMs).isEqualTo(at.toEpochMilli())
        assertThat(alarm.alarmClockInfo?.triggerTime).isEqualTo(at.toEpochMilli())
        assertThat(alarm.alarmClockInfo?.showIntent).isNotNull()
        assertThat(alarm.operation!!.isImmutable).isTrue()
    }

    @Test
    fun `fire intent carries id, moment and kind`() {
        scheduler.schedule(ScheduleRequest(AlarmId(7), at, FireKind.SNOOZE))

        val intent = shadowOf(shadowOf(alarmManager).scheduledAlarms.single().operation).savedIntent

        assertThat(intent.data).isEqualTo(AlarmIntents.uri(AlarmId(7)))
        assertThat(AlarmIntents.parse(intent)).isEqualTo(ScheduleRequest(AlarmId(7), at, FireKind.SNOOZE))
    }

    @Test
    fun `each alarm has its own pending intent and rescheduling replaces it`() {
        scheduler.schedule(ScheduleRequest(AlarmId(1), at, FireKind.REGULAR))
        scheduler.schedule(ScheduleRequest(AlarmId(2), at, FireKind.REGULAR))
        scheduler.schedule(ScheduleRequest(AlarmId(1), at.plusSeconds(60), FireKind.SNOOZE))

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertThat(alarms).hasSize(2)
        assertThat(alarms.map { it.triggerAtMs }).containsExactly(at.toEpochMilli(), at.plusSeconds(60).toEpochMilli())
    }

    @Test
    fun `the reserved test alarm has its own pending intent next to user alarms`() {
        scheduler.schedule(ScheduleRequest(AlarmId(1), at, FireKind.REGULAR))
        scheduler.schedule(ScheduleRequest(AlarmId.TEST, at.plusSeconds(5), FireKind.REGULAR))

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertThat(alarms).hasSize(2)
        val test = alarms.single { it.triggerAtMs == at.plusSeconds(5).toEpochMilli() }
        val intent = shadowOf(test.operation).savedIntent
        assertThat(AlarmIntents.parse(intent)?.alarmId).isEqualTo(AlarmId.TEST)

        scheduler.cancel(AlarmId.TEST)

        assertThat(shadowOf(alarmManager).scheduledAlarms).hasSize(1)
    }

    @Test
    fun `cancel removes only that alarm`() {
        scheduler.schedule(ScheduleRequest(AlarmId(1), at, FireKind.REGULAR))
        scheduler.schedule(ScheduleRequest(AlarmId(2), at, FireKind.REGULAR))

        scheduler.cancel(AlarmId(1))

        val remaining = shadowOf(alarmManager).scheduledAlarms.single()
        assertThat(shadowOf(remaining.operation).savedIntent.data).isEqualTo(AlarmIntents.uri(AlarmId(2)))
    }

    @Test
    fun `cancel of an unknown alarm is harmless`() {
        scheduler.cancel(AlarmId(99))

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }

    @Test
    fun `when exact alarms are not allowed schedule reports failure without crashing`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertThat(scheduler.schedule(ScheduleRequest(AlarmId(1), at, FireKind.REGULAR))).isFalse()
        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }

    @Test
    fun `foreign or broken intents are not parsed`() {
        assertThat(AlarmIntents.parse(Intent("x"))).isNull()
        assertThat(AlarmIntents.parse(Intent("x", android.net.Uri.parse("balarm://alarm/abc")))).isNull()
        assertThat(AlarmIntents.parse(Intent("x", android.net.Uri.parse("other://alarm/5")))).isNull()
    }
}
