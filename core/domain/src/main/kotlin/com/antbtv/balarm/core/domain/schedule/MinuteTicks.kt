package com.antbtv.balarm.core.domain.schedule

import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Текущее время с шагом в минуту, по границам минут. Зона берётся из [clock] на каждом шаге
 * (`SystemZoneClock`), поэтому смена часового пояса видна на следующей минуте.
 *
 * Тикает только пока на поток подписаны (экран на переднем плане) — постоянных сервисов и воркеров нет (NFR-9).
 */
fun minuteTicks(clock: Clock): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now(clock)
        emit(now.truncatedTo(ChronoUnit.MINUTES))
        val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        delay(Duration.between(now, nextMinute).toMillis().coerceAtLeast(1))
    }
}.distinctUntilChanged()
