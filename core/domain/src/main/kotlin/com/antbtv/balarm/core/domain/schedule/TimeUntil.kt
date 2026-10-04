package com.antbtv.balarm.core.domain.schedule

import java.time.Duration
import java.time.Instant

/** Время до срабатывания для шапки списка и тоста (ADR-011 §4). Нулевых значений «всё сразу» не бывает. */
data class TimeUntil(val days: Int, val hours: Int, val minutes: Int)

/**
 * Округляет **вверх** до минуты: 06:59:30 → 07:00:00 — это «через 1 мин», а не «через 0 мин».
 * [at] не позже [now] (звонок вот-вот) тоже даёт минимум 1 минуту.
 */
fun timeUntil(now: Instant, at: Instant): TimeUntil {
    val millis = Duration.between(now, at).toMillis().coerceAtLeast(0)
    val totalMinutes = ((millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).coerceAtLeast(1)
    return TimeUntil(
        days = (totalMinutes / MINUTES_PER_DAY).toInt(),
        hours = (totalMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR).toInt(),
        minutes = (totalMinutes % MINUTES_PER_HOUR).toInt(),
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * 60L
