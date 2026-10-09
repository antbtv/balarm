package com.antbtv.balarm.core.data.db

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.TriggerKind
import com.antbtv.balarm.core.model.takeCodePoints
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Преобразования entity ↔ domain.
 *
 * Чтение **толерантно**: значения вне допустимых диапазонов приводятся к ближайшим допустимым,
 * а не бросают исключение. Одна повреждённая строка не должна сорвать перепланирование
 * всех будильников после перезагрузки (главный инвариант).
 */
internal object AlarmMapper {

    fun toDomain(entity: AlarmEntity): Alarm = Alarm(
        id = AlarmId(entity.id.coerceAtLeast(0)),
        time = LocalTime.of(entity.hour.coerceIn(0, MAX_HOUR), entity.minute.coerceIn(0, MAX_MINUTE)),
        repeatDays = daysFromMask(entity.repeatDays),
        label = entity.label.takeCodePoints(Alarm.MAX_LABEL_LENGTH),
        enabled = entity.enabled,
        vibrate = entity.vibrate,
        snooze = snoozeFrom(entity.snoozeIntervalMin, entity.snoozeLimit),
        sound = soundFrom(entity),
    )

    fun toEntity(alarm: Alarm): AlarmEntity = AlarmEntity(
        id = alarm.id.value,
        hour = alarm.time.hour,
        minute = alarm.time.minute,
        repeatDays = maskFromDays(alarm.repeatDays),
        label = alarm.label,
        enabled = alarm.enabled,
        vibrate = alarm.vibrate,
        snoozeIntervalMin = alarm.snooze.interval?.toMinutes()?.toInt() ?: 0,
        snoozeLimit = alarm.snooze.maxCount ?: UNLIMITED,
        sound = alarm.sound.sound.encode(),
        volumePercent = alarm.sound.volumePercent,
        fadeInSec = alarm.sound.fadeIn.seconds.toInt(),
    )

    fun toDomain(entity: AlarmRuntimeEntity): AlarmRuntimeState = AlarmRuntimeState(
        alarmId = AlarmId(entity.alarmId),
        nextTriggerAt = entity.nextTriggerAt?.let(Instant::ofEpochMilli),
        nextTriggerKind = TriggerKind.entries.firstOrNull { it.name == entity.nextTriggerKind } ?: TriggerKind.REGULAR,
        snoozeCount = entity.snoozeCount.coerceAtLeast(0),
        lastFiredAt = entity.lastFiredAt?.let(Instant::ofEpochMilli),
        scheduleFailed = entity.scheduleFailed,
    )

    fun toEntity(state: AlarmRuntimeState): AlarmRuntimeEntity = AlarmRuntimeEntity(
        alarmId = state.alarmId.value,
        nextTriggerAt = state.nextTriggerAt?.toEpochMilli(),
        nextTriggerKind = state.nextTriggerKind.name,
        snoozeCount = state.snoozeCount,
        lastFiredAt = state.lastFiredAt?.toEpochMilli(),
        scheduleFailed = state.scheduleFailed,
    )

    fun maskFromDays(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun daysFromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filterTo(mutableSetOf()) {
        mask and (1 shl (it.value - 1)) != 0
    }

    private fun snoozeFrom(intervalMin: Int, limit: Int): SnoozeSettings {
        if (intervalMin <= 0) return SnoozeSettings.DISABLED
        val interval = Duration.ofMinutes(
            intervalMin.toLong().coerceIn(
                SnoozeSettings.MIN_INTERVAL.toMinutes(),
                SnoozeSettings.MAX_INTERVAL.toMinutes(),
            ),
        )
        val maxCount = if (limit < 0) null else limit.coerceIn(1, SnoozeSettings.MAX_COUNT)
        return SnoozeSettings(interval, maxCount)
    }

    /** Мусор в колонках звука не роняет чтение: неизвестная мелодия → по умолчанию, числа → ближайшие допустимые. */
    private fun soundFrom(entity: AlarmEntity): SoundSettings {
        val step = SoundSettings.VOLUME_STEP
        val volume = (entity.volumePercent.coerceIn(SoundSettings.MIN_VOLUME, SoundSettings.MAX_VOLUME) / step) * step
        val fadeIn = SoundSettings.FADE_IN_OPTIONS.firstOrNull { it.seconds == entity.fadeInSec.toLong() }
            ?: Duration.ZERO
        return SoundSettings(
            sound = SoundRef.decode(entity.sound) ?: SoundRef.DEFAULT,
            volumePercent = volume,
            fadeIn = fadeIn,
        )
    }

    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59
    private const val UNLIMITED = -1
}
