package com.antbtv.balarm.di

import com.antbtv.balarm.core.model.feature.ConfigFeatureFlagProvider
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release: флаги только из config/features.properties, без переопределений (FR-FLAG-5). */
@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsModule {
    @Provides
    fun provideFeatureFlagProvider(): FeatureFlagProvider = ConfigFeatureFlagProvider
}
