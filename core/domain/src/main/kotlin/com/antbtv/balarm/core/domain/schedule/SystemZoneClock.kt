package com.antbtv.balarm.core.domain.schedule

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Системные часы, которые читают текущую зону на каждый вызов (ADR-006 §4).
 * `Clock.systemDefaultZone()` фиксирует зону при создании: синглтон после смены TZ
 * продолжил бы считать будильники в старой зоне. В продакшн-коде используется только этот класс.
 */
class SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()

    override fun millis(): Long = System.currentTimeMillis()
}
