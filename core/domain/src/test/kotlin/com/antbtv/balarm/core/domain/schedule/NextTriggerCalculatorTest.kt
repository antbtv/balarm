package com.antbtv.balarm.core.domain.schedule

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class NextTriggerCalculatorTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(iso: String, zone: ZoneId): Instant = LocalDateTime.parse(iso).atZone(zone).toInstant()

    private fun next(time: String, days: Set<DayOfWeek>, after: String, zone: ZoneId = moscow): Instant =
        NextTriggerCalculator.next(LocalTime.parse(time), days, at(after, zone), zone)

    // 2026-09-28 — понедельник.

    @Test
    fun `one shot later today rings today`() {
        assertThat(next("06:30", emptySet(), "2026-09-28T05:00")).isEqualTo(at("2026-09-28T06:30", moscow))
    }

    @Test
    fun `one shot already passed today rings tomorrow`() {
        assertThat(next("06:30", emptySet(), "2026-09-28T07:00")).isEqualTo(at("2026-09-29T06:30", moscow))
    }

    @Test
    fun `when after equals the alarm moment then next occurrence is used`() {
        assertThat(next("06:30", emptySet(), "2026-09-28T06:30")).isEqualTo(at("2026-09-29T06:30", moscow))
    }

    @Test
    fun `repeating alarm picks next matching day of week`() {
        assertThat(next("06:30", setOf(FRIDAY), "2026-09-28T05:00")).isEqualTo(at("2026-10-02T06:30", moscow))
        assertThat(next("06:30", setOf(MONDAY), "2026-09-28T07:00")).isEqualTo(at("2026-10-05T06:30", moscow))
        assertThat(next("06:30", setOf(MONDAY, WEDNESDAY), "2026-09-28T07:00"))
            .isEqualTo(at("2026-09-30T06:30", moscow))
    }

    @Test
    fun `every day of week is reachable`() {
        DayOfWeek.entries.forEachIndexed { index, day ->
            assertThat(next("09:00", setOf(day), "2026-09-28T00:00"))
                .isEqualTo(at("2026-09-28T09:00", moscow).plusSeconds(index * 86_400L))
        }
    }

    @Test
    fun `crosses month and year boundaries`() {
        assertThat(next("00:05", emptySet(), "2026-01-31T23:59")).isEqualTo(at("2026-02-01T00:05", moscow))
        assertThat(next("00:05", emptySet(), "2026-12-31T23:59")).isEqualTo(at("2027-01-01T00:05", moscow))
        assertThat(next("07:00", setOf(SATURDAY, SUNDAY), "2026-12-31T08:00"))
            .isEqualTo(at("2027-01-02T07:00", moscow))
    }

    @Test
    fun `leap day is a normal day`() {
        assertThat(next("07:00", setOf(TUESDAY), "2028-02-28T12:00")).isEqualTo(at("2028-02-29T07:00", moscow))
    }

    @Test
    fun `spring forward gap rings at the transition not an hour later`() {
        // Berlin 2026-03-29: 02:00 CET → 03:00 CEST (01:00Z); 02:30 не существует.
        val result = next("02:30", emptySet(), "2026-03-28T23:00", berlin)

        assertThat(result).isEqualTo(Instant.parse("2026-03-29T01:00:00Z"))
        assertThat(result.atZone(berlin).toLocalTime()).isEqualTo(LocalTime.of(3, 0))
    }

    @Test
    fun `fall back overlap rings only at the first occurrence`() {
        // Berlin 2026-10-25: 03:00 CEST → 02:00 CET; 02:30 наступает в 00:30Z и в 01:30Z.
        val first = next("02:30", setOf(SUNDAY), "2026-10-24T23:00", berlin)
        assertThat(first).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))

        val afterFirst = NextTriggerCalculator.next(LocalTime.of(2, 30), setOf(SUNDAY), first, berlin)
        assertThat(afterFirst).isEqualTo(Instant.parse("2026-11-01T01:30:00Z"))

        val daily = NextTriggerCalculator.next(LocalTime.of(2, 30), emptySet(), first, berlin)
        assertThat(daily).isEqualTo(Instant.parse("2026-10-26T01:30:00Z"))
    }

    @Test
    fun `half hour offset zone`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        assertThat(next("07:00", emptySet(), "2026-09-28T05:00", kolkata))
            .isEqualTo(Instant.parse("2026-09-28T01:30:00Z"))
    }

    @Test
    fun `half hour dst gap in Lord Howe rings at the transition`() {
        // Lord Howe 2026-10-04: 02:00 (+10:30) → 02:30 (+11:00); 02:15 не существует.
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        val result = next("02:15", emptySet(), "2026-10-04T01:00", lordHowe)

        assertThat(result).isEqualTo(ZonedDateTime.of(2026, 10, 4, 2, 30, 0, 0, lordHowe).toInstant())
    }

    @Test
    fun `calendar day missing in the zone is skipped`() {
        // Pacific/Apia перешла через линию перемены дат: 2011-12-30 не существовало.
        val apia = ZoneId.of("Pacific/Apia")
        val result = next("06:30", emptySet(), "2011-12-29T12:00", apia)

        assertThat(result).isEqualTo(ZonedDateTime.of(2011, 12, 31, 6, 30, 0, 0, apia).toInstant())
    }

    @Test
    fun `plus fourteen hours zone`() {
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        val result = next("07:00", emptySet(), "2026-09-28T08:00", kiritimati)

        assertThat(result.atZone(kiritimati).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-09-29T07:00"))
    }

    @Test
    fun `after strictly between the two overlap occurrences moves to the next day`() {
        val result = NextTriggerCalculator.next(
            LocalTime.of(2, 30),
            emptySet(),
            Instant.parse("2026-10-25T00:45:00Z"),
            berlin,
        )

        assertThat(result).isEqualTo(Instant.parse("2026-10-26T01:30:00Z"))
    }

    @Test
    fun `after exactly at the gap transition moves to the next day`() {
        val result = NextTriggerCalculator.next(
            LocalTime.of(2, 30),
            emptySet(),
            Instant.parse("2026-03-29T01:00:00Z"),
            berlin,
        )

        assertThat(result).isEqualTo(at("2026-03-30T02:30", berlin))
    }

    @Test
    fun `repeating alarm on the gap day rings at the transition`() {
        assertThat(
            next("02:30", setOf(SUNDAY), "2026-03-27T12:00", berlin),
        ).isEqualTo(Instant.parse("2026-03-29T01:00:00Z"))
    }

    @Test
    fun `spring forward gap in a negative offset zone`() {
        // New York 2026-03-08: 02:00 EST → 03:00 EDT (07:00Z).
        val newYork = ZoneId.of("America/New_York")
        assertThat(
            next("02:30", emptySet(), "2026-03-08T00:00", newYork),
        ).isEqualTo(Instant.parse("2026-03-08T07:00:00Z"))
    }

    @Test
    fun `gap starting at midnight keeps the day`() {
        // Havana 2026-03-08: 00:00 → 01:00.
        val havana = ZoneId.of("America/Havana")
        val result = next("00:30", emptySet(), "2026-03-07T12:00", havana)

        assertThat(result).isEqualTo(ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, havana).toInstant())
    }

    @Test
    fun `repeating alarm on a missing calendar day waits for the next week`() {
        // 2011-12-30 в Apia — пятница, которой не было.
        val apia = ZoneId.of("Pacific/Apia")
        val result = next("06:30", setOf(FRIDAY), "2011-12-29T12:00", apia)

        assertThat(result).isEqualTo(ZonedDateTime.of(2012, 1, 6, 6, 30, 0, 0, apia).toInstant())
    }
}
