package com.antbtv.balarm.core.domain.sound

import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.CustomSoundId
import kotlinx.coroutines.flow.Flow

/** Источник импорта: `content://` из SAF; домен не знает `Uri` (ADR-016 §4). */
@JvmInline
value class SoundSource(val uri: String)

sealed interface ImportResult {
    data class Imported(val sound: CustomSound) : ImportResult

    data class TooLarge(val limitBytes: Long) : ImportResult

    /** Нет аудиодорожки или декодера. */
    data object Unsupported : ImportResult

    data object NoSpace : ImportResult

    data class Failed(val reason: String) : ImportResult
}

/** Библиотека своих мелодий (FR-SND-2/3). Реализация — `:core:data`; на пути звонка не используется. */
interface SoundRepository {
    /** По `addedAt` по убыванию. */
    fun observeCustomSounds(): Flow<List<CustomSound>>

    suspend fun getCustom(id: CustomSoundId): CustomSound?

    suspend fun import(source: SoundSource): ImportResult

    suspend fun rename(id: CustomSoundId, title: String): Boolean

    /** Сколько будильников используют мелодию — для предупреждения перед удалением. */
    suspend fun usageCount(id: CustomSoundId): Int

    /** Удаляет мелодию; возвращает число будильников, переключённых на встроенную по умолчанию. */
    suspend fun delete(id: CustomSoundId): Int

    /** Убирает сирот: tmp-файлы, файлы без строк, строки без файлов. Вызывается при запуске UI. */
    suspend fun cleanUp()

    companion object {
        const val IMPORT_LIMIT_BYTES: Long = 20L * 1024 * 1024
    }
}
