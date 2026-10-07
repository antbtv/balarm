package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test

class AlarmDefaultsTest {

    @Test
    fun `new alarm is a one shot with default snooze and no label`() {
        val alarm = AlarmDefaults.newAlarm(LocalTime.of(14, 20, 33))

        assertThat(alarm.id).isEqualTo(AlarmId.UNSAVED)
        assertThat(alarm.isOneShot).isTrue()
        assertThat(alarm.label).isEmpty()
        assertThat(alarm.enabled).isTrue()
        assertThat(alarm.vibrate).isTrue()
        assertThat(alarm.snooze).isEqualTo(SnoozeSettings.DEFAULT)
    }

    @Test
    fun `time is the next whole hour`() {
        assertThat(AlarmDefaults.newAlarm(LocalTime.of(14, 20)).time).isEqualTo(LocalTime.of(15, 0))
        assertThat(AlarmDefaults.newAlarm(LocalTime.of(14, 0)).time).isEqualTo(LocalTime.of(15, 0))
        assertThat(AlarmDefaults.newAlarm(LocalTime.of(14, 59, 59)).time).isEqualTo(LocalTime.of(15, 0))
    }

    @Test
    fun `after 23 the next hour wraps to midnight`() {
        assertThat(AlarmDefaults.newAlarm(LocalTime.of(23, 40)).time).isEqualTo(LocalTime.MIDNIGHT)
    }

    @Test
    fun `test alarm has the reserved id, whole minutes and no snooze`() {
        val alarm = AlarmDefaults.testAlarm(LocalTime.of(5, 0, 20, 5))

        assertThat(alarm.id).isEqualTo(AlarmId.TEST)
        assertThat(alarm.time).isEqualTo(LocalTime.of(5, 0))
        assertThat(alarm.isOneShot).isTrue()
        assertThat(alarm.snooze).isEqualTo(SnoozeSettings.DISABLED)
    }
}
