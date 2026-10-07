package com.antbtv.balarm.di

import com.antbtv.balarm.DebugTools
import com.antbtv.balarm.debug.FeatureFlagsDebugTools
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug: заполняет `Optional<DebugTools>` из main (`DebugToolsModule`); в release биндинга нет. */
@Module
@InstallIn(SingletonComponent::class)
interface DebugToolsBindingModule {
    @Binds
    fun bindDebugTools(impl: FeatureFlagsDebugTools): DebugTools
}
