package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.settings.SettingsPolicy
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import java.time.Duration

/** Какие миссии сейчас доступны: тип известен и его флаг включён (FR-FLAG-3). Реализация — реестр миссий. */
fun interface MissionCatalog {
    fun isAvailable(type: MissionTypeId): Boolean
}

/** Миссия, которую надо пройти на звонке; `null` — недоступный (выключен флаг, неизвестный тип) или отсутствует. */
fun Alarm.effectiveMission(catalog: MissionCatalog): MissionSpec? = mission?.takeIf { catalog.isAvailable(it.type) }

/** Умолчания миссии (FR-MIS-2); действующие значения — `AppSettings` (ADR-021), снимок на звонок. */
object MissionPolicy {
    /** Бездействие в миссии: после него звук возвращается на 100 %, ввод сбрасывается. */
    val IDLE_TIMEOUT: Duration = SettingsPolicy.DEFAULTS.missionIdleTimeout

    /** Коэффициент усиления плеера на время миссии (30 %). */
    val DUCK_GAIN: Float = SettingsPolicy.duckGain(SettingsPolicy.DEFAULTS)
}
