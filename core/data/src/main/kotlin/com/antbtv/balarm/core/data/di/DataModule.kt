package com.antbtv.balarm.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.antbtv.balarm.core.data.DataStoreSetupStateRepository
import com.antbtv.balarm.core.data.R
import com.antbtv.balarm.core.data.RoomAlarmRepository
import com.antbtv.balarm.core.data.db.BalarmDatabase
import com.antbtv.balarm.core.data.sound.AudioProbe
import com.antbtv.balarm.core.data.sound.ContentResolverSoundSourceOpener
import com.antbtv.balarm.core.data.sound.DataStoreRingVolumeStore
import com.antbtv.balarm.core.data.sound.DeviceProtectedSoundFileStore
import com.antbtv.balarm.core.data.sound.MediaAudioProbe
import com.antbtv.balarm.core.data.sound.RoomSoundRepository
import com.antbtv.balarm.core.data.sound.SoundSourceOpener
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.sound.RingVolumeStore
import com.antbtv.balarm.core.domain.sound.SoundFileStore
import com.antbtv.balarm.core.domain.sound.SoundRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindAlarmRepository(impl: RoomAlarmRepository): AlarmRepository

    @Binds
    fun bindSetupStateRepository(impl: DataStoreSetupStateRepository): SetupStateRepository

    @Binds
    fun bindSoundFileStore(impl: DeviceProtectedSoundFileStore): SoundFileStore

    @Binds
    fun bindSoundSourceOpener(impl: ContentResolverSoundSourceOpener): SoundSourceOpener

    @Binds
    fun bindAudioProbe(impl: MediaAudioProbe): AudioProbe

    @Binds
    fun bindRingVolumeStore(impl: DataStoreRingVolumeStore): RingVolumeStore

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

        @Provides
        @Singleton
        fun provideSoundRepository(
            database: BalarmDatabase,
            files: DeviceProtectedSoundFileStore,
            opener: SoundSourceOpener,
            probe: AudioProbe,
            clock: Clock,
            @DeviceProtected context: Context,
        ): SoundRepository = RoomSoundRepository(
            database,
            files,
            opener,
            probe,
            clock,
            Dispatchers.IO,
        ) { context.getString(R.string.sound_default_title, it) }
    }
}
