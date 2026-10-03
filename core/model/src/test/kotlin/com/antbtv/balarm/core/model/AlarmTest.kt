package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertThrows
import org.junit.Test

class AlarmTest {

    @Test
    fun `new alarm has safe defaults`() {
        val alarm = Alarm(time = LocalTime.of(6, 30))

        assertThat(alarm.id).isEqualTo(AlarmId.UNSAVED)
        assertThat(alarm.enabled).isTrue()
        assertThat(alarm.isOneShot).isTrue()
        assertThat(alarm.snooze).isEqualTo(SnoozeSettings.DEFAULT)
    }

    @Test
    fun `when repeat days are set then alarm is not one shot`() {
        assertThat(Alarm(time = LocalTime.NOON, repeatDays = setOf(DayOfWeek.MONDAY)).isOneShot).isFalse()
    }

    @Test
    fun `when time has seconds then creation fails`() {
        assertThrows(IllegalArgumentException::class.java) { Alarm(time = LocalTime.of(6, 30, 15)) }
    }

    @Test
    fun `when label is too long then creation fails`() {
        assertThrows(IllegalArgumentException::class.java) {
            Alarm(time = LocalTime.NOON, label = "x".repeat(Alarm.MAX_LABEL_LENGTH + 1))
        }
    }
}
