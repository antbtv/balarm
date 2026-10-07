package com.antbtv.balarm.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.antbtv.balarm.core.data.DataStoreSetupStateRepository
import com.antbtv.balarm.core.data.RoomAlarmRepository
import com.antbtv.balarm.core.data.db.BalarmDatabase
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindAlarmRepository(impl: RoomAlarmRepository): AlarmRepository

    @Binds
    fun bindSetupStateRepository(impl: DataStoreSetupStateRepository): SetupStateRepository

    companion object {
        @Provides
        @DeviceProtected
        fun provideDeviceProtectedContext(@ApplicationContext context: Context): Context =
            context.createDeviceProtectedStorageContext()

        /** Один DataStore на файл `app_prefs` (device-protected); позже примет глобальные настройки (PRD §3.9). */
        @Provides
        @Singleton
        fun providePreferencesDataStore(@DeviceProtected context: Context): DataStore<Preferences> =
            // preferencesDataStoreFile берёт applicationContext и теряет device-protected:
            // путь строим от своего контекста.
            PreferenceDataStoreFactory.create { File(context.filesDir, "datastore/app_prefs.preferences_pb") }

        @Provides
        @Singleton
        fun provideDatabase(@DeviceProtected context: Context): BalarmDatabase = BalarmDatabase.create(context)
    }
}
