package com.antbtv.balarm.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.antbtv.balarm.core.data.db.AlarmDao
import com.antbtv.balarm.core.data.db.AlarmMapper
import com.antbtv.balarm.core.data.db.BalarmDatabase
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.AlarmWithRuntime
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomAlarmRepository @Inject constructor(private val database: BalarmDatabase) : AlarmRepository {

    private val dao: AlarmDao = database.alarmDao()

    override fun observeAlarmsWithRuntime(): Flow<List<AlarmWithRuntime>> = dao.observeAllWithRuntime().map { rows ->
        rows.map { AlarmWithRuntime(AlarmMapper.toDomain(it.alarm), it.runtime?.let(AlarmMapper::toDomain)) }
    }

    override suspend fun get(id: AlarmId): Alarm? = dao.get(id.value)?.let(AlarmMapper::toDomain)

    override suspend fun save(alarm: Alarm): AlarmId {
        require(!alarm.id.isTest) { "The test alarm id is reserved and never stored" }
        val entity = AlarmMapper.toEntity(alarm)
        if (alarm.id.isSaved && dao.update(entity) > 0) return alarm.id
        return AlarmId(dao.insert(entity.copy(id = 0)))
    }

    override suspend fun setEnabled(id: AlarmId, enabled: Boolean) = dao.setEnabled(id.value, enabled)

    override suspend fun delete(id: AlarmId) = dao.delete(id.value)

    override suspend fun loadAll(): List<AlarmWithRuntime> {
        val runtimes = dao.getAllRuntime().associateBy { it.alarmId }
        return dao.getAll().map { entity ->
            AlarmWithRuntime(AlarmMapper.toDomain(entity), runtimes[entity.id]?.let(AlarmMapper::toDomain))
        }
    }

    override suspend fun getRuntime(id: AlarmId): AlarmRuntimeState? =
        dao.getRuntime(id.value)?.let(AlarmMapper::toDomain)

    override suspend fun updateRuntime(state: AlarmRuntimeState) = dao.upsertRuntime(AlarmMapper.toEntity(state))

    override suspend fun <R> transaction(block: suspend () -> R): R =
        database.useWriterConnection { connection -> connection.immediateTransaction { block() } }
}
