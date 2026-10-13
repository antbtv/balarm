package com.antbtv.balarm.core.domain.settings

import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.AutoStop
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import org.junit.Test

class SettingsPolicyTest {

    @Test
    fun `defaults match former constants`() {
        val d = SettingsPolicy.DEFAULTS
        assertThat(d.autoStop).isEqualTo(AutoStop.MIN_30)
        assertThat(d.missionIdleTimeout).isEqualTo(Duration.ofSeconds(15))
        assertThat(d.missionDuckPercent).isEqualTo(30)
        assertThat(d.firstDayOfWeek).isNull()
    }

    @Test
    fun `valid values are kept`() {
        val s = AppSettings(AutoStop.NEVER, Duration.ofSeconds(60), 50, DayOfWeek.SUNDAY)
        assertThat(SettingsPolicy.sanitize(s)).isEqualTo(s)
    }

    @Test
    fun `idle timeout out of range or off step falls back to default`() {
        for (seconds in listOf(0L, 5L, 12L, 65L, 3600L)) {
            val s = AppSettings(missionIdleTimeout = Duration.ofSeconds(seconds))
            assertThat(SettingsPolicy.sanitize(s).missionIdleTimeout).isEqualTo(Duration.ofSeconds(15))
        }
        assertThat(
            SettingsPolicy.sanitize(AppSettings(missionIdleTimeout = Duration.ofMillis(20_500))).missionIdleTimeout,
        ).isEqualTo(Duration.ofSeconds(15))
    }

    @Test
    fun `duck percent out of set falls back to default`() {
        for (p in listOf(-10, 0, 5, 35, 60, 100)) {
            assertThat(SettingsPolicy.sanitize(AppSettings(missionDuckPercent = p)).missionDuckPercent).isEqualTo(30)
        }
    }

    @Test
    fun `auto stop durations`() {
        assertThat(AutoStop.MIN_5.duration).isEqualTo(Duration.ofMinutes(5))
        assertThat(AutoStop.NEVER.duration).isNull()
    }

    @Test
    fun `duck gain is percent over hundred`() {
        assertThat(SettingsPolicy.duckGain(AppSettings(missionDuckPercent = 30))).isWithin(0.0001f).of(0.3f)
    }
}
