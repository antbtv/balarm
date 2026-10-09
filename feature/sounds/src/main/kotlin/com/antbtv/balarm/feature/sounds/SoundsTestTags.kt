package com.antbtv.balarm.feature.sounds

import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId

/** Test-теги пикера и библиотеки мелодий. */
object SoundsTestTags {
    const val PICKER = "soundPicker"
    const val LIBRARY = "soundLibrary"
    const val LIST = "soundList"
    const val BUILTIN_HEADER = "soundBuiltinHeader"
    const val CUSTOM_HEADER = "soundCustomHeader"
    const val MANAGE = "soundManage"
    const val ADD = "soundAdd"
    const val CONFIRM = "soundConfirm"
    const val IMPORT_PROGRESS = "soundImportProgress"
    const val SELECTED_MISSING = "soundSelectedMissing"
    const val CUSTOM_EMPTY = "soundCustomEmpty"
    const val LIBRARY_EMPTY = "soundLibraryEmpty"
    const val DELETE_DIALOG = "soundDeleteDialog"
    const val RENAME_DIALOG = "soundRenameDialog"

    /** Строка встроенной мелодии в пикере: `soundBuiltin_snd_bells`. */
    fun builtin(sound: BuiltinSound): String = "soundBuiltin_${sound.key}"

    /** Строка своей мелодии (пикер и библиотека): `soundCustom_7`. */
    fun custom(id: CustomSoundId): String = "soundCustom_${id.value}"

    /** Кнопки строки библиотеки. */
    fun play(id: CustomSoundId): String = "soundPlay_${id.value}"

    fun rename(id: CustomSoundId): String = "soundRename_${id.value}"

    fun delete(id: CustomSoundId): String = "soundDelete_${id.value}"
}
