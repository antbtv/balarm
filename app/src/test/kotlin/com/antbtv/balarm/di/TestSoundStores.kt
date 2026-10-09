package com.antbtv.balarm.di

import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.RingVolumeStore
import com.antbtv.balarm.core.domain.sound.SavedVolume
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.sound.SoundSource
import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.CustomSoundId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Библиотека мелодий в памяти для графа Hilt-тестов `:app` (вместо Room из `DataModule`). Импорт не нужен. */
class TestSoundRepository : SoundRepository {
    val sounds = MutableStateFlow<List<CustomSound>>(emptyList())

    override fun observeCustomSounds(): Flow<List<CustomSound>> = sounds

    override suspend fun getCustom(id: CustomSoundId): CustomSound? = sounds.value.firstOrNull { it.id == id }

    override suspend fun import(source: SoundSource): ImportResult = ImportResult.Unsupported

    override suspend fun rename(id: CustomSoundId, title: String): Boolean {
        val exists = getCustom(id) != null
        sounds.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
        return exists
    }

    override suspend fun usageCount(id: CustomSoundId): Int = 0

    override suspend fun delete(id: CustomSoundId): Int {
        sounds.update { list -> list.filterNot { it.id == id } }
        return 0
    }

    override suspend fun cleanUp() = Unit
}

/** Громкость звонка в памяти (вместо DataStore). */
class TestRingVolumeStore : RingVolumeStore {
    private var saved: SavedVolume? = null

    override suspend fun load(): SavedVolume? = saved

    override suspend fun save(volume: SavedVolume) {
        saved = volume
    }

    override suspend fun clear() {
        saved = null
    }
}
