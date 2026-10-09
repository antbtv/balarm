package com.antbtv.balarm.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomSoundDao {
    @Query("SELECT * FROM custom_sound ORDER BY added_at DESC, id DESC")
    fun observeAll(): Flow<List<CustomSoundEntity>>

    @Query("SELECT * FROM custom_sound")
    suspend fun getAll(): List<CustomSoundEntity>

    @Query("SELECT * FROM custom_sound WHERE id = :id")
    suspend fun get(id: Long): CustomSoundEntity?

    @Query("SELECT COUNT(*) FROM custom_sound")
    suspend fun count(): Int

    @Insert
    suspend fun insert(sound: CustomSoundEntity): Long

    @Query("UPDATE custom_sound SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String): Int

    @Query("DELETE FROM custom_sound WHERE id = :id")
    suspend fun delete(id: Long)

    /** Будильники, звонящие этой мелодией ([ref] = `SoundRef.encode()`). */
    @Query("SELECT COUNT(*) FROM alarm WHERE sound = :ref")
    suspend fun countAlarmsUsing(ref: String): Int

    /** Исключение из «alarm пишет движок» (ADR-016 §5): колонки звука расписание не затрагивают. */
    @Query("UPDATE alarm SET sound = :to WHERE sound = :from")
    suspend fun switchAlarmsSound(from: String, to: String): Int
}
