package com.antbtv.balarm.di

import com.antbtv.balarm.AppUiIntents
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.data.di.DataModule
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.di.ApplicationScope
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakeSetupStateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Граф Hilt-тестов `:app`: репозиторий в памяти, application scope на главном потоке (детерминированно). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class, DataModule::class])
interface TestAppModule {
    @Binds
    fun bindUiIntents(impl: AppUiIntents): AlarmUiIntents

    @Binds
    fun bindRepository(fake: FakeAlarmRepository): AlarmRepository

    @Binds
    fun bindSetupState(fake: FakeSetupStateRepository): SetupStateRepository

    companion object {
        @Provides
        @Singleton
        fun repository() = FakeAlarmRepository()

        @Provides
        @Singleton
        fun setupState() = FakeSetupStateRepository()

        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
