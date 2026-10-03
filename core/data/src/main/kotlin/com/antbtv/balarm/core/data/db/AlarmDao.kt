package com.antbtv.balarm.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm ORDER BY id")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarm WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity): Int

    @Query("UPDATE alarm SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarm WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM alarm_runtime")
    suspend fun getAllRuntime(): List<AlarmRuntimeEntity>

    @Query("SELECT * FROM alarm_runtime WHERE alarm_id = :id")
    suspend fun getRuntime(id: Long): AlarmRuntimeEntity?

    @Upsert
    suspend fun upsertRuntime(runtime: AlarmRuntimeEntity)
}
