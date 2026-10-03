package com.antbtv.balarm.di

import android.util.Log
import com.antbtv.balarm.AppUiIntents
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.alarm.LogcatAlarmEventLog
import com.antbtv.balarm.core.domain.di.ApplicationScope
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {
    @Binds
    fun bindUiIntents(impl: AppUiIntents): AlarmUiIntents

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(
            // IO: открытие Room и миграции блокируют поток. Обработчик — последняя страховка: падение
            // фоновой работы не должно ронять приложение-будильник.
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e(LogcatAlarmEventLog.TAG, "APP_SCOPE_ERROR error=${e.javaClass.simpleName}")
            },
        )
    }
}
