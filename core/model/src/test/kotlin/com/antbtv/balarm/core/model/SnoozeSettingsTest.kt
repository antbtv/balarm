package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Assert.assertThrows
import org.junit.Test

class SnoozeSettingsTest {

    @Test
    fun `remaining counts down to zero and never below`() {
        val settings = SnoozeSettings(Duration.ofMinutes(5), maxCount = 3)

        assertThat(settings.remaining(0)).isEqualTo(3)
        assertThat(settings.remaining(3)).isEqualTo(0)
        assertThat(settings.remaining(5)).isEqualTo(0)
    }

    @Test
    fun `editor options are valid settings`() {
        val minutes = SnoozeSettings.INTERVAL_OPTIONS.map { it.toMinutes() }
        assertThat(minutes).containsExactly(1L, 3L, 5L, 10L, 15L, 20L, 30L).inOrder()
        assertThat(SnoozeSettings.LIMIT_OPTIONS).containsExactly(1, 2, 3, 5, 10, null).inOrder()
        SnoozeSettings.INTERVAL_OPTIONS.forEach { interval ->
            SnoozeSettings.LIMIT_OPTIONS.forEach { limit ->
                assertThat(SnoozeSettings(interval, limit).isEnabled).isTrue() // конструктор не бросает
            }
        }
        assertThat(SnoozeSettings.INTERVAL_OPTIONS).contains(SnoozeSettings.DEFAULT.interval)
        assertThat(SnoozeSettings.LIMIT_OPTIONS).contains(SnoozeSettings.DEFAULT.maxCount)
    }

    @Test
    fun `when unlimited then remaining is null`() {
        assertThat(SnoozeSettings(Duration.ofMinutes(5), maxCount = null).remaining(7)).isNull()
    }

    @Test
    fun `when disabled then nothing remains`() {
        assertThat(SnoozeSettings.DISABLED.isEnabled).isFalse()
        assertThat(SnoozeSettings.DISABLED.remaining(0)).isEqualTo(0)
        assertThat(SnoozeSettings(interval = null, maxCount = 3).remaining(0)).isEqualTo(0)
    }

    @Test
    fun `when interval or count out of range then creation fails`() {
        assertThrows(IllegalArgumentException::class.java) { SnoozeSettings(Duration.ofSeconds(30), 3) }
        assertThrows(IllegalArgumentException::class.java) { SnoozeSettings(Duration.ofMinutes(31), 3) }
        assertThrows(IllegalArgumentException::class.java) { SnoozeSettings(Duration.ofSeconds(90), 3) }
        assertThrows(IllegalArgumentException::class.java) { SnoozeSettings(Duration.ofMinutes(5), 0) }
        assertThrows(IllegalArgumentException::class.java) { SnoozeSettings(Duration.ofMinutes(5), 11) }
    }
}
