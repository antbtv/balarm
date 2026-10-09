package com.antbtv.balarm.core.format

import androidx.annotation.StringRes
import com.antbtv.balarm.core.model.BuiltinSound

/** Название встроенной мелодии для редактора, пикера и библиотеки (RU/EN, NFR-7). */
@StringRes
fun BuiltinSound.titleRes(): Int = when (this) {
    BuiltinSound.DEFAULT -> R.string.sound_default
    BuiltinSound.SUNRISE -> R.string.sound_sunrise
    BuiltinSound.MARIMBA -> R.string.sound_marimba
    BuiltinSound.BELLS -> R.string.sound_bells
    BuiltinSound.PIANO -> R.string.sound_piano
    BuiltinSound.CHIPTUNE -> R.string.sound_chiptune
    BuiltinSound.DIGITAL -> R.string.sound_digital
    BuiltinSound.ASCEND -> R.string.sound_ascend
    BuiltinSound.CHIMES -> R.string.sound_chimes
    BuiltinSound.SIREN -> R.string.sound_siren
}
