package com.antbtv.balarm.core.domain.sound

import com.antbtv.balarm.core.model.CustomSoundId
import java.io.File

/** Путь файла своей мелодии в device-protected storage; вычисляется из id, в БД не хранится (ADR-016 §3). */
interface SoundFileStore {
    fun fileOf(id: CustomSoundId): File
}
