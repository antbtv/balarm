package com.antbtv.balarm.core.data.sound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.antbtv.balarm.core.domain.sound.RingVolumeStore
import com.antbtv.balarm.core.domain.sound.SavedVolume
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Исходная и выставленная громкость `STREAM_ALARM` в DataStore device-protected (ADR-017). */
@Singleton
class DataStoreRingVolumeStore @Inject constructor(private val store: DataStore<Preferences>) : RingVolumeStore {

    override suspend fun load(): SavedVolume? {
        val prefs = store.data.first()
        val original = prefs[ORIGINAL] ?: return null
        val applied = prefs[APPLIED] ?: return null
        return SavedVolume(original, applied)
    }

    override suspend fun save(volume: SavedVolume) {
        store.edit {
            it[ORIGINAL] = volume.original
            it[APPLIED] = volume.applied
        }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(ORIGINAL)
            it.remove(APPLIED)
        }
    }

    private companion object {
        val ORIGINAL = intPreferencesKey("ring_volume_original")
        val APPLIED = intPreferencesKey("ring_volume_applied")
    }
}
