package com.antbtv.balarm.core.alarm.sound

import android.content.Context
import android.media.AudioManager
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.sound.RingVolumeStore
import com.antbtv.balarm.core.domain.sound.SavedVolume
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

enum class VolumeOwner { RINGING, PREVIEW }

/**
 * Громкость `STREAM_ALARM` на время звонка/превью и её возврат (FR-SND-7, ADR-017 §2).
 *
 * Меняет громкость только из `RingingService` или видимого экрана (Android 17 игнорирует смену из фона).
 * Все методы — с главного потока. Исходная громкость пишется в [store] **до** изменения потока и
 * синхронно (с таймаутом): если процесс умрёт посреди звонка, пользователь не потеряет свою громкость.
 */
@Suppress("TooGenericExceptionCaught") // вендорский AudioManager может бросить что угодно — звонок важнее громкости
@Singleton
class AlarmVolumeController @Inject constructor(
    @ApplicationContext context: Context,
    private val store: RingVolumeStore,
    private val log: AlarmEventLog,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val owners = mutableSetOf<VolumeOwner>()
    private var saved: SavedVolume? = null
    private var storeChecked = false

    fun acquire(owner: VolumeOwner, percent: Int) {
        owners += owner
        if (audio.isVolumeFixed) {
            log.log(AlarmEvent.VolumeFixed)
            return
        }
        val max = audio.getStreamMaxVolume(STREAM)
        val target = targetIndex(percent, audio.getStreamMinVolume(STREAM), max)
        val current = audio.getStreamVolume(STREAM)
        // Снимок, оставшийся от упавшего процесса, не перезаписываем: текущая громкость — уже выставленная нами.
        val original = existingSnapshot()?.original ?: current
        if (saved == null) blockingStore { store.save(SavedVolume(original, target)) }
        try {
            audio.setStreamVolume(STREAM, target, 0)
        } catch (_: RuntimeException) {
            log.log(AlarmEvent.VolumeNotApplied(target, current))
        }
        val actual = audio.getStreamVolume(STREAM)
        if (actual != target) log.log(AlarmEvent.VolumeNotApplied(target, actual))
        saved = SavedVolume(original, actual)
        blockingStore { store.save(SavedVolume(original, actual)) }
        log.log(AlarmEvent.VolumeApplied(original, actual, max))
    }

    fun release(owner: VolumeOwner) {
        owners -= owner
        if (owners.isNotEmpty()) return
        val snapshot = saved ?: return
        saved = null
        restore(snapshot)
    }

    /** Запуск UI: вернуть громкость, оставшуюся после падения процесса, если звонка сейчас нет. */
    fun restorePendingIfIdle() {
        if (owners.isNotEmpty()) return
        val snapshot = blockingStore { store.load() } ?: return
        restore(snapshot)
    }

    private fun existingSnapshot(): SavedVolume? = saved ?: if (storeChecked) {
        null
    } else {
        storeChecked = true
        blockingStore { store.load() }
    }

    private fun restore(snapshot: SavedVolume) {
        val unchangedByUser = audio.getStreamVolume(STREAM) == snapshot.applied
        if (unchangedByUser && snapshot.original != snapshot.applied) {
            try {
                audio.setStreamVolume(STREAM, snapshot.original, 0)
                log.log(AlarmEvent.VolumeRestored(snapshot.original))
            } catch (_: RuntimeException) {
                log.log(AlarmEvent.VolumeNotApplied(snapshot.original, audio.getStreamVolume(STREAM)))
            }
        }
        blockingStore { store.clear() }
        storeChecked = false
    }

    private fun <T> blockingStore(block: suspend () -> T): T? = try {
        runBlocking(Dispatchers.IO) { withTimeoutOrNull(STORE_TIMEOUT_MS) { block() } }
    } catch (_: Exception) {
        null // ошибка хранилища не должна мешать звонку
    }

    companion object {
        private const val STREAM = AudioManager.STREAM_ALARM
        private const val STORE_TIMEOUT_MS = 300L
        private const val PERCENT = 100.0

        /** Индекс потока для [percent] %: не ниже минимального индекса устройства. */
        fun targetIndex(percent: Int, min: Int, max: Int): Int =
            ceil(percent * max / PERCENT).toInt().coerceIn(min, max)
    }
}
