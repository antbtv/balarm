package com.antbtv.balarm.core.domain.schedule

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class TimeUntilTest {

    private val now = Instant.parse("2026-09-28T06:00:00Z")

    private fun until(seconds: Long) = timeUntil(now, now.plusSeconds(seconds))

    @Test
    fun `seconds are rounded up to a whole minute`() {
        assertThat(until(1)).isEqualTo(TimeUntil(0, 0, 1))
        assertThat(until(59)).isEqualTo(TimeUntil(0, 0, 1))
        assertThat(until(60)).isEqualTo(TimeUntil(0, 0, 1))
        assertThat(until(61)).isEqualTo(TimeUntil(0, 0, 2))
    }

    @Test
    fun `half a minute before the hour boundary is one minute`() {
        val at = Instant.parse("2026-09-28T07:00:00Z")

        assertThat(timeUntil(Instant.parse("2026-09-28T06:59:30Z"), at)).isEqualTo(TimeUntil(0, 0, 1))
    }

    @Test
    fun `a fraction of a second still rounds up to the next minute`() {
        val at = Instant.parse("2026-09-28T07:00:00Z")

        assertThat(timeUntil(Instant.parse("2026-09-28T06:58:59.500Z"), at)).isEqualTo(TimeUntil(0, 0, 2))
        assertThat(timeUntil(Instant.parse("2026-09-28T06:59:00Z"), at)).isEqualTo(TimeUntil(0, 0, 1))
        assertThat(timeUntil(Instant.parse("2026-09-28T06:59:59.999Z"), at)).isEqualTo(TimeUntil(0, 0, 1))
    }

    @Test
    fun `half a minute before midnight is one minute`() {
        assertThat(timeUntil(Instant.parse("2026-09-28T23:59:30Z"), Instant.parse("2026-09-29T00:00:00Z")))
            .isEqualTo(TimeUntil(0, 0, 1))
    }

    @Test
    fun `hours and minutes are split`() {
        assertThat(until(7 * 3_600L + 12 * 60)).isEqualTo(TimeUntil(0, 7, 12))
        assertThat(until(3_600)).isEqualTo(TimeUntil(0, 1, 0))
        assertThat(until(23 * 3_600L + 59 * 60 + 30)).isEqualTo(TimeUntil(1, 0, 0)) // 23:59:30 → ровно сутки
    }

    @Test
    fun `days are split off`() {
        assertThat(until(86_400)).isEqualTo(TimeUntil(1, 0, 0))
        assertThat(until(2 * 86_400L + 3 * 3_600 + 5 * 60)).isEqualTo(TimeUntil(2, 3, 5))
        assertThat(until(7 * 86_400L)).isEqualTo(TimeUntil(7, 0, 0))
    }

    @Test
    fun `a trigger that is not in the future still reads as one minute`() {
        assertThat(timeUntil(now, now)).isEqualTo(TimeUntil(0, 0, 1))
        assertThat(timeUntil(now, now.minusSeconds(30))).isEqualTo(TimeUntil(0, 0, 1))
    }
}
