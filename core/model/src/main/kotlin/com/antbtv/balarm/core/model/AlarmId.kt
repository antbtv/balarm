package com.antbtv.balarm.core.model

/** Идентификатор будильника; совпадает с первичным ключом в хранилище (M1). */
@JvmInline
value class AlarmId(val value: Long) {
    init {
        require(value > 0) { "AlarmId must be positive, was $value" }
    }
}
