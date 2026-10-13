package com.antbtv.balarm.core.domain.settings

import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.AutoStop
import java.time.Duration

/** Снимок настроек, действующий до конца одного звонка (ADR-021 §4). */
data class RingSettings(val autoStop: AutoStop, val missionIdleTimeout: Duration, val duckGain: Float) {
    companion object {
        val DEFAULTS: RingSettings = SettingsPolicy.DEFAULTS.toRingSettings()
    }
}

fun AppSettings.toRingSettings(): RingSettings =
    RingSettings(autoStop, missionIdleTimeout, SettingsPolicy.duckGain(this))
