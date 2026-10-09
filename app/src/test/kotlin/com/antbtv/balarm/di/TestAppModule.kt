package com.antbtv.balarm.di

import android.content.Context
import com.antbtv.balarm.AppUiIntents
import com.antbtv.balarm.core.alarm.AlarmUiIntents
import com.antbtv.balarm.core.data.di.DataModule
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.di.ApplicationScope
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.sound.RingVolumeStore
import com.antbtv.balarm.core.domain.sound.SoundFileStore
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.domain.testing.FakeAlarmRepository
import com.antbtv.balarm.core.domain.testing.FakePermissionHealthChecker
import com.antbtv.balarm.core.permissions.di.PermissionsModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Граф Hilt-тестов `:app`: репозиторий в памяти, application scope на главном потоке (детерминированно),
 * управляемые статусы разрешений и состояние онбординга (по умолчанию всё выдано и онбординг пройден),
 * библиотека мелодий и сохранённая громкость звонка в памяти, файлы своих мелодий — в кэше теста.
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

    @Binds
    fun bindSoundRepository(fake: TestSoundRepository): SoundRepository

    @Binds
    fun bindRingVolumeStore(fake: TestRingVolumeStore): RingVolumeStore

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
        fun soundRepository() = TestSoundRepository()

        @Provides
        @Singleton
        fun ringVolumeStore() = TestRingVolumeStore()

        @Provides
        fun soundFileStore(@ApplicationContext context: Context): SoundFileStore =
            SoundFileStore { id -> File(context.cacheDir, "sounds/${id.value}") }

        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
