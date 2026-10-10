package com.antbtv.balarm.feature.missions.api

import com.antbtv.balarm.core.domain.alarm.MissionCatalog
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider

/**
 * Реестр миссий (ADR-018). Тип доступен, если он зарегистрирован и его флаг включён (FR-FLAG-3):
 * иначе будильник отключается без миссии — «застрявшего» будильника быть не может.
 */
class MissionRegistry(private val definitions: List<MissionDefinition>, private val flags: FeatureFlagProvider) :
    MissionCatalog {

    /** Определения, доступные в UI редактора. */
    val available: List<MissionDefinition> get() = definitions.filter { flags.isEnabled(it.feature) }

    fun definition(type: MissionTypeId): MissionDefinition? = definitions.firstOrNull { it.type == type }

    override fun isAvailable(type: MissionTypeId): Boolean =
        definition(type)?.let { flags.isEnabled(it.feature) } == true
}
