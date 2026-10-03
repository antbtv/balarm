package com.antbtv.balarm.core.model

/**
 * Идентификатор будильника = первичный ключ хранилища (ADR-006 §1).
 * [UNSAVED] (0) — будильник ещё не сохранён; хранилище выдаёт id > 0 и не переиспользует их.
 */
@JvmInline
value class AlarmId(val value: Long) {
    init {
        require(value >= 0) { "AlarmId must not be negative, was $value" }
    }

    val isSaved: Boolean get() = value > 0

    companion object {
        val UNSAVED = AlarmId(0)
    }
}
