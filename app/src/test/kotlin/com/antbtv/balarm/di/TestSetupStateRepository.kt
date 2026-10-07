package com.antbtv.balarm.di

import com.antbtv.balarm.core.domain.health.SetupState
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Состояние онбординга для тестов `:app`. По умолчанию онбординг пройден (большинство тестов про вкладки);
 * тесты онбординга меняют [value] до запуска Activity.
 */
class TestSetupStateRepository : SetupStateRepository {
    override val state = MutableStateFlow(SetupState(onboardingCompleted = true, oemBackgroundConfirmed = true))

    var value: SetupState
        get() = state.value
        set(value) {
            state.value = value
        }

    override suspend fun completeOnboarding() {
        state.value = state.value.copy(onboardingCompleted = true)
    }

    override suspend fun setOemBackgroundConfirmed(confirmed: Boolean) {
        state.value = state.value.copy(oemBackgroundConfirmed = confirmed)
    }
}
