package com.antbtv.balarm.core.data.db

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.TriggerKind
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import org.junit.Test

class AlarmMapperTest {

    @Test
    fun `day mask uses ISO order Monday first`() {
        assertThat(AlarmMapper.maskFromDays(setOf(DayOfWeek.MONDAY))).isEqualTo(0b0000001)
        assertThat(AlarmMapper.maskFromDays(setOf(DayOfWeek.SUNDAY))).isEqualTo(0b1000000)
        assertThat(AlarmMapper.daysFromMask(0b1111111)).isEqualTo(DayOfWeek.entries.toSet())
        assertThat(AlarmMapper.daysFromMask(0)).isEmpty()
    }

    @Test
    fun `snooze settings map to sentinel columns and back`() {
        val unlimited = Alarm(time = LocalTime.NOON, snooze = SnoozeSettings(Duration.ofMinutes(10), null))
        val disabled = Alarm(time = LocalTime.NOON, snooze = SnoozeSettings.DISABLED)

        assertThat(AlarmMapper.toEntity(unlimited).snoozeLimit).isEqualTo(-1)
        assertThat(AlarmMapper.toEntity(disabled).snoozeIntervalMin).isEqualTo(0)
        assertThat(AlarmMapper.toDomain(AlarmMapper.toEntity(unlimited)).snooze).isEqualTo(unlimited.snooze)
        assertThat(AlarmMapper.toDomain(AlarmMapper.toEntity(disabled)).snooze).isEqualTo(SnoozeSettings.DISABLED)
    }

    @Test
    fun `out of range values are coerced instead of throwing`() {
        val alarm = AlarmMapper.toDomain(
            AlarmEntity(
                id = 3,
                hour = 99,
                minute = -5,
                repeatDays = 0xFF,
                label = "x".repeat(500),
                enabled = true,
                vibrate = false,
                snoozeIntervalMin = 999,
                snoozeLimit = 50,
            ),
        )

        assertThat(alarm.time).isEqualTo(LocalTime.of(23, 0))
        assertThat(alarm.repeatDays).isEqualTo(DayOfWeek.entries.toSet())
        assertThat(alarm.label).hasLength(Alarm.MAX_LABEL_LENGTH)
        assertThat(alarm.snooze).isEqualTo(SnoozeSettings(SnoozeSettings.MAX_INTERVAL, SnoozeSettings.MAX_COUNT))
    }

    @Test
    fun `oversized label of emoji is cut by code points without splitting a pair`() {
        val emoji = "\uD83D\uDE00"

        val alarm = AlarmMapper.toDomain(
            AlarmEntity(
                id = 4, hour = 6, minute = 0, repeatDays = 0, label = emoji.repeat(60),
                enabled = true, vibrate = true, snoozeIntervalMin = 5, snoozeLimit = 3,
            ),
        )

        assertThat(alarm.label).isEqualTo(emoji.repeat(Alarm.MAX_LABEL_LENGTH))
    }

    @Test
    fun `label with a lone surrogate does not break reading`() {
        val alarm = AlarmMapper.toDomain(
            AlarmEntity(
                id = 5, hour = 6, minute = 0, repeatDays = 0, label = "a\uD83D".repeat(60),
                enabled = true, vibrate = true, snoozeIntervalMin = 5, snoozeLimit = 3,
            ),
        )

        assertThat(alarm.label.codePointCount(0, alarm.label.length)).isEqualTo(Alarm.MAX_LABEL_LENGTH)
    }

    @Test
    fun `schedule failed mark survives the round trip`() {
        val state = AlarmRuntimeState(alarmId = AlarmId(1), scheduleFailed = true)

        assertThat(AlarmMapper.toDomain(AlarmMapper.toEntity(state))).isEqualTo(state)
    }

    @Test
    fun `unknown trigger kind and negative counter are tolerated`() {
        val runtime = AlarmMapper.toDomain(
            AlarmRuntimeEntity(
                alarmId = 1,
                nextTriggerAt = 10,
                nextTriggerKind = "BOGUS",
                snoozeCount = -3,
                lastFiredAt = null,
            ),
        )

        assertThat(runtime.nextTriggerKind).isEqualTo(TriggerKind.REGULAR)
        assertThat(runtime.snoozeCount).isEqualTo(0)
    }

    @Test
    fun `sound settings round trip`() {
        val alarm = Alarm(
            time = LocalTime.NOON,
            sound = SoundSettings(
                sound = SoundRef.Custom(CustomSoundId(5)),
                volumePercent = 40,
                fadeIn = Duration.ofSeconds(30),
            ),
        )

        val entity = AlarmMapper.toEntity(alarm)

        assertThat(entity.sound).isEqualTo("custom:5")
        assertThat(entity.volumePercent).isEqualTo(40)
        assertThat(entity.fadeInSec).isEqualTo(30)
        assertThat(AlarmMapper.toDomain(entity).sound).isEqualTo(alarm.sound)
    }

    @Test
    fun `garbage sound columns fall back to safe defaults`() {
        val base = AlarmMapper.toEntity(Alarm(time = LocalTime.NOON))

        val garbage = AlarmMapper.toDomain(base.copy(sound = "wat", volumePercent = 0, fadeInSec = 7)).sound
        val loud = AlarmMapper.toDomain(base.copy(volumePercent = 999)).sound
        val odd = AlarmMapper.toDomain(base.copy(volumePercent = 47)).sound

        assertThat(garbage.sound).isEqualTo(SoundRef.DEFAULT)
        assertThat(garbage.volumePercent).isEqualTo(SoundSettings.MIN_VOLUME)
        assertThat(garbage.fadeIn).isEqualTo(Duration.ZERO)
        assertThat(loud.volumePercent).isEqualTo(100)
        assertThat(odd.volumePercent).isEqualTo(40)
    }
}
