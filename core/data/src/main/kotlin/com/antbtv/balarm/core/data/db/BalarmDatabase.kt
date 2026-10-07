package com.antbtv.balarm.core.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

/**
 * БД приложения (ADR-004). Строится только от device-protected контекста (ADR-001);
 * миграции — только аддитивные, `fallbackToDestructiveMigration` запрещён в любой сборке.
 */
@Database(
    entities = [AlarmEntity::class, AlarmRuntimeEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class BalarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        const val NAME = "balarm.db"

        fun create(deviceProtectedContext: Context): BalarmDatabase {
            require(deviceProtectedContext.isDeviceProtectedStorage) {
                "Database must live in device-protected storage"
            }
            return Room.databaseBuilder(deviceProtectedContext, BalarmDatabase::class.java, NAME)
                .setDriver(AndroidSQLiteDriver())
                .build()
        }
    }
}
