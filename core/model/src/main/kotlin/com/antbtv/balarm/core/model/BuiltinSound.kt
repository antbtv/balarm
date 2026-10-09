package com.antbtv.balarm.core.model

/**
 * Встроенные мелодии (FR-SND-1, ADR-016 §1).
 * [key] — имя файла в `res/raw` и значение в БД: **не меняется никогда** (R9).
 */
enum class BuiltinSound(val key: String) {
    /** M1; им же звонит канал `alarm_fallback`, ресурс не переименовывать. */
    DEFAULT("alarm_default"),
    SUNRISE("snd_sunrise"),
    MARIMBA("snd_marimba"),
    BELLS("snd_bells"),
    PIANO("snd_piano"),
    CHIPTUNE("snd_chiptune"),
    DIGITAL("snd_digital"),
    ASCEND("snd_ascend"),
    CHIMES("snd_chimes"),
    SIREN("snd_siren"),
    ;

    companion object {
        fun fromKey(key: String): BuiltinSound? = entries.firstOrNull { it.key == key }
    }
}
