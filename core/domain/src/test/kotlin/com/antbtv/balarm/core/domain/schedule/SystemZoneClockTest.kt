package com.antbtv.balarm.core.domain.schedule

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Test

class SystemZoneClockTest {

    private val original = TimeZone.getDefault()

    @After
    fun restore() = TimeZone.setDefault(original)

    @Test
    fun `zone follows system default changes after creation`() {
        val clock = SystemZoneClock()

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertThat(clock.zone).isEqualTo(ZoneId.of("Asia/Tokyo"))

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
        assertThat(clock.zone).isEqualTo(ZoneId.of("Europe/Moscow"))
    }

    @Test
    fun `instant is close to system time`() {
        val clock = SystemZoneClock()
        val drift = kotlin.math.abs(clock.millis() - System.currentTimeMillis())

        assertThat(drift).isLessThan(1_000L)
        assertThat(clock.withZone(ZoneId.of("UTC")).zone).isEqualTo(ZoneId.of("UTC"))
    }
}
