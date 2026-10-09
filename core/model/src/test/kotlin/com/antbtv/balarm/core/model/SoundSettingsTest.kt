package com.antbtv.balarm.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class SoundSettingsTest {

    @Test
    fun `default settings are audible`() {
        assertThat(SoundSettings.DEFAULT.volumePercent).isEqualTo(80)
        assertThat(SoundSettings.DEFAULT.sound).isEqualTo(SoundRef.DEFAULT)
        assertThat(SoundSettings.DEFAULT.fadeIn).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `volume range is 10 to 100 in steps of 10`() {
        (10..100 step 10).forEach { SoundSettings(volumePercent = it) }
        listOf(0, 5, 15, 110, -10).forEach {
            assertThrows(IllegalArgumentException::class.java) { SoundSettings(volumePercent = it) }
        }
    }

    @Test
    fun `fade-in must be one of the options`() {
        SoundSettings.FADE_IN_OPTIONS.forEach { SoundSettings(fadeIn = it) }
        assertThrows(IllegalArgumentException::class.java) { SoundSettings(fadeIn = Duration.ofSeconds(20)) }
    }

    @Test
    fun `alarm carries default sound`() {
        val alarm = Alarm(time = java.time.LocalTime.of(7, 0))

        assertThat(alarm.sound).isEqualTo(SoundSettings.DEFAULT)
    }

    @Test
    fun `custom sound title is validated in code points`() {
        fun sound(title: String) = CustomSound(CustomSoundId(1), title, Duration.ofSeconds(5), 100, Instant.EPOCH)

        sound("a".repeat(40))
        assertThrows(IllegalArgumentException::class.java) { sound("a".repeat(41)) }
        assertThrows(IllegalArgumentException::class.java) { sound("  ") }
        assertThat(sound("🎵".repeat(40)).title).hasLength(80)
    }
}
