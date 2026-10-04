package com.antbtv.balarm.core.model

/**
 * Идентификатор будильника = первичный ключ хранилища (ADR-006 §1).
 * [UNSAVED] (0) — будильник ещё не сохранён; хранилище выдаёт id > 0 (кроме [TEST]) и не переиспользует их.
 */
@JvmInline
value class AlarmId(val value: Long) {
    init {
        require(value >= 0) { "AlarmId must not be negative, was $value" }
    }

    /**
     * `true` и для [TEST] — планировщику нужен любой адресуемый id. Для проверки «есть в хранилище» —
     * `isSaved && !isTest`.
     */
    val isSaved: Boolean get() = value > 0

    /** Зарезервированный id тестового звонка (ADR-010): в хранилище не попадает, но планируется как обычный. */
    val isTest: Boolean get() = this == TEST

    companion object {
        val UNSAVED = AlarmId(0)

        /** `Long.MAX_VALUE`: `AUTOINCREMENT` его не выдаст, URI `balarm://alarm/<id>` уникален (ADR-010). */
        val TEST = AlarmId(Long.MAX_VALUE)
    }
}
