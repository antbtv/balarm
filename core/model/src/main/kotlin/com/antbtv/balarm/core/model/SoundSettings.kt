package com.antbtv.balarm.core.model

import java.time.Duration
import java.time.Instant

/**
 * Звук будильника (FR-EDIT-5, ADR-016/017).
 * [volumePercent] — [MIN_VOLUME]..100 с шагом 10: 0 % сделал бы будильник беззвучным.
 * [fadeIn] — одно из [FADE_IN_OPTIONS].
 */
data class SoundSettings(
    val sound: SoundRef = SoundRef.DEFAULT,
    val volumePercent: Int = DEFAULT_VOLUME,
    val fadeIn: Duration = Duration.ZERO,
) {
    init {
        require(volumePercent in MIN_VOLUME..MAX_VOLUME && volumePercent % VOLUME_STEP == 0) {
            "Volume out of range or not a multiple of $VOLUME_STEP: $volumePercent"
        }
        require(fadeIn in FADE_IN_OPTIONS) { "Unsupported fade-in: $fadeIn" }
    }

    companion object {
        const val MIN_VOLUME = 10
        const val MAX_VOLUME = 100
        const val VOLUME_STEP = 10
        const val DEFAULT_VOLUME = 80

        val FADE_IN_OPTIONS: List<Duration> = listOf(0L, 15, 30, 60).map(Duration::ofSeconds)

        val DEFAULT = SoundSettings()
    }
}

/** Своя мелодия из библиотеки (FR-SND-2/3). */
data class CustomSound(
    val id: CustomSoundId,
    val title: String,
    val duration: Duration,
    val sizeBytes: Long,
    val addedAt: Instant,
) {
    init {
        require(title.isNotBlank()) { "Title must not be blank" }
        require(title.codePointCount(0, title.length) <= MAX_TITLE_LENGTH) {
            "Title longer than $MAX_TITLE_LENGTH code points"
        }
        require(!duration.isNegative) { "Negative duration: $duration" }
        require(sizeBytes >= 0) { "Negative size: $sizeBytes" }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 40
    }
}
