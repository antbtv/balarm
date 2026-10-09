package com.antbtv.balarm.core.model

/** Идентификатор своей (импортированной) мелодии. */
@JvmInline
value class CustomSoundId(val value: Long) {
    init {
        require(value > 0) { "CustomSoundId must be positive, was $value" }
    }
}

/** Ссылка на мелодию будильника: встроенная или своя. В БД хранится как [encode] (ADR-016 §1). */
sealed interface SoundRef {
    data class Builtin(val sound: BuiltinSound) : SoundRef

    data class Custom(val id: CustomSoundId) : SoundRef

    /** `builtin:<key>` | `custom:<id>`. */
    fun encode(): String = when (this) {
        is Builtin -> "$BUILTIN_PREFIX${sound.key}"
        is Custom -> "$CUSTOM_PREFIX${id.value}"
    }

    companion object {
        private const val BUILTIN_PREFIX = "builtin:"
        private const val CUSTOM_PREFIX = "custom:"

        val DEFAULT: SoundRef = Builtin(BuiltinSound.DEFAULT)

        /** Неизвестное или битое значение → `null`; вызывающий берёт [DEFAULT] и пишет в лог. */
        fun decode(raw: String): SoundRef? = when {
            raw.startsWith(BUILTIN_PREFIX) -> decodeBuiltin(raw.removePrefix(BUILTIN_PREFIX))
            raw.startsWith(CUSTOM_PREFIX) -> decodeCustom(raw.removePrefix(CUSTOM_PREFIX))
            else -> null
        }

        private fun decodeBuiltin(key: String): SoundRef? = BuiltinSound.fromKey(key)?.let(::Builtin)

        private fun decodeCustom(id: String): SoundRef? =
            id.toLongOrNull()?.takeIf { it > 0 }?.let { Custom(CustomSoundId(it)) }
    }
}
