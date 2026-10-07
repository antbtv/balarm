package com.antbtv.balarm

import android.content.Intent
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Debug-инструменты, доступные из UI (FR-FLAG-5: 7 тапов по версии в «О приложении»). Реализация есть только
 * в debug source set; в release `Optional` пуст и экран получает `onOpenDebugFlags = null`.
 */
interface DebugTools {
    fun featureFlagsIntent(): Intent
}

@Module
@InstallIn(SingletonComponent::class)
interface DebugToolsModule {
    @BindsOptionalOf
    fun optionalDebugTools(): DebugTools
}
