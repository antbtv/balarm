package com.antbtv.balarm.core.domain.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Ближайший момент срабатывания будильника (FR-REL-4, ADR-006 §3).
 *
 * * Время будильника — локальное, в текущей зоне; результат — абсолютный [Instant].
 * * Переход на летнее время (gap): несуществующее 02:30 → момент перехода (03:00 нового времени),
 *   а не 03:30, как по умолчанию в java.time.
 * * Переход на зимнее время (overlap): только первое наступление; второе никогда не кандидат,
 *   поэтому после отката часов будильник не звонит повторно.
 * * Дата, целиком отсутствующая в зоне (Pacific/Apia 2011-12-30), пропускается.
 */
object NextTriggerCalculator {

    /**
     * Ближайшее наступление строго после [after]. Пустой [repeatDays] — разовый (сегодня или завтра).
     * За [SEARCH_DAYS] дней наступление есть всегда (максимум неделя + выпавший день), поэтому `error` —
     * проверка инварианта, а не ожидаемый путь.
     */
    fun next(time: LocalTime, repeatDays: Set<DayOfWeek>, after: Instant, zone: ZoneId): Instant {
        val startDate = LocalDate.ofInstant(after, zone)
        return (0L..SEARCH_DAYS).asSequence()
            .map(startDate::plusDays)
            .filter { repeatDays.isEmpty() || it.dayOfWeek in repeatDays }
            .mapNotNull { resolve(it, time, zone) }
            .firstOrNull { it.isAfter(after) }
            ?: error("No trigger within $SEARCH_DAYS days for $time $repeatDays in $zone")
    }

    private fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): Instant? {
        val local = LocalDateTime.of(date, time)
        val rules = zone.rules
        val offsets = rules.getValidOffsets(local)
        return when {
            // overlap: первый в списке — offset до перехода, т. е. первое наступление
            offsets.isNotEmpty() -> local.toInstant(offsets.first())

            else -> {
                val transition = rules.getTransition(local)
                // Разрыв накрывает весь календарный день — будильник этого дня не звонит.
                val dayMissing = !transition.dateTimeBefore.isAfter(date.atStartOfDay()) &&
                    !transition.dateTimeAfter.isBefore(date.plusDays(1).atStartOfDay())
                transition.instant.takeUnless { dayMissing }
            }
        }
    }

    /** Неделя + запас на пропущенные календарные даты. */
    private const val SEARCH_DAYS = 14L
}
