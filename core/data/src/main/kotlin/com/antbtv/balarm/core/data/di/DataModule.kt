package com.antbtv.balarm.core.data.di

import android.content.Context
import com.antbtv.balarm.core.data.RoomAlarmRepository
import com.antbtv.balarm.core.data.db.BalarmDatabase
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindAlarmRepository(impl: RoomAlarmRepository): AlarmRepository

    companion object {
        @Provides
        @DeviceProtected
        fun provideDeviceProtectedContext(@ApplicationContext context: Context): Context =
            context.createDeviceProtectedStorageContext()

        @Provides
        @Singleton
        fun provideDatabase(@DeviceProtected context: Context): BalarmDatabase = BalarmDatabase.create(context)
    }
}
