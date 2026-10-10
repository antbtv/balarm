package com.antbtv.balarm.core.model

/** Тип миссии: `"math"` и т. д. Ядро не знает конкретных типов (FR-MIS-4, ADR-018). */
@JvmInline
value class MissionTypeId(val value: String) {
    init {
        require(value.isNotBlank()) { "Mission type must not be blank" }
    }

    companion object {
        val MATH = MissionTypeId("math")
    }
}

/** Миссия будильника: тип и непрозрачный для ядра JSON-конфиг, разбирает его сама миссия. */
data class MissionSpec(val type: MissionTypeId, val config: String)
