package com.antbtv.balarm.feature.sounds

import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.sound.SoundSource
import kotlin.coroutines.cancellation.CancellationException

// MIME-типы SAF-пикера (ADR-016 §4): часть провайдеров отдаёт OGG как application/ogg, а не как аудио-тип.
internal val IMPORT_MIME_TYPES = arrayOf("audio/*", "application/ogg")

/**
 * Импорт без исключений наружу: сбой репозитория — [ImportResult.Failed]. Отмена (экран закрыт) пробрасывается —
 * репозиторий сам удаляет временный файл.
 */
@Suppress("TooGenericExceptionCaught") // любой сбой импорта — сообщение пользователю, не падение
internal suspend fun SoundRepository.importSafely(uri: String): ImportResult = try {
    import(SoundSource(uri))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ImportResult.Failed(e.message ?: e.javaClass.simpleName)
}

internal fun ImportResult.toMessage(): SoundMessage = when (this) {
    is ImportResult.Imported -> SoundMessage.Imported(sound.title)
    is ImportResult.TooLarge -> SoundMessage.TooLarge(limitBytes)
    ImportResult.Unsupported -> SoundMessage.Unsupported
    ImportResult.NoSpace -> SoundMessage.NoSpace
    is ImportResult.Failed -> SoundMessage.ImportFailed
}
