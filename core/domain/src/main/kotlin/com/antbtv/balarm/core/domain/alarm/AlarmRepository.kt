package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import kotlinx.coroutines.flow.Flow

data class AlarmWithRuntime(val alarm: Alarm, val runtime: AlarmRuntimeState?)

/** Хранилище будильников (реализация — Room в DE-storage, `:core:data`, ADR-004). */
interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>

    suspend fun get(id: AlarmId): Alarm?

    /** [AlarmId.UNSAVED] → вставка с новым id; иначе обновление. Возвращает id сохранённого будильника. */
    suspend fun save(alarm: Alarm): AlarmId

    suspend fun setEnabled(id: AlarmId, enabled: Boolean)

    /** Удаляет будильник вместе с runtime-состоянием. */
    suspend fun delete(id: AlarmId)

    suspend fun loadAll(): List<AlarmWithRuntime>

    suspend fun getRuntime(id: AlarmId): AlarmRuntimeState?

    suspend fun updateRuntime(state: AlarmRuntimeState)

    suspend fun <R> transaction(block: suspend () -> R): R
}
