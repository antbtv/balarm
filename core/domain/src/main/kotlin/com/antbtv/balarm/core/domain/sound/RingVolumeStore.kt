package com.antbtv.balarm.core.domain.sound

/**
 * Громкость `STREAM_ALARM` до звонка ([original]) и выставленная для звонка ([applied]).
 * Нужна, чтобы вернуть громкость, даже если процесс упал посреди звонка (ADR-017).
 */
data class SavedVolume(val original: Int, val applied: Int)

/** Хранилище [SavedVolume] в DataStore device-protected storage. */
interface RingVolumeStore {
    suspend fun load(): SavedVolume?

    suspend fun save(volume: SavedVolume)

    suspend fun clear()
}
