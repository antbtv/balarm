package com.antbtv.balarm.di

import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import com.antbtv.balarm.debug.DebugFeatureFlagProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug: конфиг + переопределения с экрана FeatureFlagsActivity (FR-FLAG-5). */
@Module
@InstallIn(SingletonComponent::class)
interface FeatureFlagsModule {
    @Binds
    fun bindFeatureFlagProvider(impl: DebugFeatureFlagProvider): FeatureFlagProvider
}
