package com.antbtv.balarm.core.designsystem.component

import java.time.DayOfWeek

/**
 * Пресеты дней повтора (FR-EDIT-2). Выходные — суббота и воскресенье во всех локалях (как в PRD), будни — остальные.
 */
enum class DayPreset(val days: Set<DayOfWeek>) {
    WEEKDAYS(
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    ),
    WEEKEND(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
    EVERY_DAY(DayOfWeek.entries.toSet()),
    ;

    /** Пресет выбран, только если набор дней совпадает с ним в точности (будни + суббота — ни один пресет). */
    fun matches(selected: Set<DayOfWeek>): Boolean = selected == days

    /**
     * Новый набор дней после тапа по пресету: невыбранный пресет ставит свои дни, повторный тап по выбранному
     * очищает набор (разовый будильник).
     */
    fun toggle(selected: Set<DayOfWeek>): Set<DayOfWeek> = if (matches(selected)) emptySet() else days
}
