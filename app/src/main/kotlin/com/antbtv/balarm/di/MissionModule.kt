package com.antbtv.balarm.di

import com.antbtv.balarm.core.domain.alarm.MissionCatalog
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.antbtv.balarm.feature.missions.api.MissionRegistry
import com.antbtv.balarm.feature.missions.math.MathMission
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Реестр миссий (ADR-018): новая миссия добавляется в список — ядро и звонок не меняются. */
@Module
@InstallIn(SingletonComponent::class)
object MissionModule {
    @Provides
    @Singleton
    fun provideRegistry(flags: FeatureFlagProvider): MissionRegistry = MissionRegistry(listOf(MathMission), flags)

    @Provides
    fun provideCatalog(registry: MissionRegistry): MissionCatalog = registry
}
