package com.antbtv.balarm.feature.alarmedit

import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundPreview
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.sound.SoundSource
import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Превью без звука: запоминает, что и сколько раз просили сыграть и заглушить. */
internal class RecordingSoundPreview : SoundPreview {
    private val current = MutableStateFlow<SoundRef?>(null)
    override val playing: StateFlow<SoundRef?> = current.asStateFlow()

    val played = mutableListOf<SoundSettings>()
    var stops = 0
        private set

    override fun play(settings: SoundSettings) {
        played += settings
        current.value = settings.sound
    }

    override fun stop() {
        stops++
        current.value = null
    }
}

/** Библиотека в памяти: редактору нужны только названия. */
internal class FakeSoundRepository(initial: List<CustomSound> = emptyList()) : SoundRepository {
    val sounds = MutableStateFlow(initial)

    override fun observeCustomSounds(): Flow<List<CustomSound>> = sounds

    override suspend fun getCustom(id: CustomSoundId): CustomSound? = sounds.value.firstOrNull { it.id == id }

    override suspend fun import(source: SoundSource): ImportResult = ImportResult.Unsupported

    override suspend fun rename(id: CustomSoundId, title: String): Boolean = false

    override suspend fun usageCount(id: CustomSoundId): Int = 0

    override suspend fun delete(id: CustomSoundId): Int = 0

    override suspend fun cleanUp() = Unit
}

internal fun customSound(id: Long, title: String) = CustomSound(
    id = CustomSoundId(id),
    title = title,
    duration = Duration.ofSeconds(30),
    sizeBytes = 1_000,
    addedAt = Instant.EPOCH,
)
