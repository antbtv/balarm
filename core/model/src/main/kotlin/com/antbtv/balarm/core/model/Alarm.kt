package com.antbtv.balarm.core.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Будильник: локальное время + дни недели (PRD §6.4, FR-REL-4). Пустой [repeatDays] — разовый.
 * Поля M4–M7 (звук, миссии, цитаты, озвучка) добавляются аддитивно, с безопасными значениями по умолчанию.
 */
data class Alarm(
    val id: AlarmId = AlarmId.UNSAVED,
    val time: LocalTime,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val snooze: SnoozeSettings = SnoozeSettings.DEFAULT,
) {
    init {
        require(time.second == 0 && time.nano == 0) { "Alarm time must have whole minutes, was $time" }
        require(label.length <= MAX_LABEL_LENGTH) { "Label longer than $MAX_LABEL_LENGTH chars" }
    }

    val isOneShot: Boolean get() = repeatDays.isEmpty()

    companion object {
        const val MAX_LABEL_LENGTH = 40
    }
}
