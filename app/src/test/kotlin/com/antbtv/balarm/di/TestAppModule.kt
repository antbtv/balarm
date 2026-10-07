package com.antbtv.balarm.di

import com.antbtv.balarm.AppUiIntents
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.data.di.DataModule
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.di.ApplicationScope
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.permissions.di.PermissionsModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Граф Hilt-тестов `:app`: репозиторий в памяти, application scope на главном потоке (детерминированно),
 * управляемые статусы разрешений и состояние онбординга (по умолчанию всё выдано и онбординг пройден).
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class, DataModule::class, PermissionsModule::class],
)
interface TestAppModule {
    @Binds
    fun bindUiIntents(impl: AppUiIntents): AlarmUiIntents

    @Binds
    fun bindRepository(fake: FakeAlarmRepository): AlarmRepository

    @Binds
    fun bindSetupState(fake: TestSetupStateRepository): SetupStateRepository

    @Binds
    fun bindHealthChecker(fake: FakePermissionHealthChecker): PermissionHealthChecker

    companion object {
        @Provides
        @Singleton
        fun repository() = FakeAlarmRepository()

        @Provides
        @Singleton
        fun setupState() = TestSetupStateRepository()

        @Provides
        @Singleton
        fun healthChecker() = FakePermissionHealthChecker()

        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
