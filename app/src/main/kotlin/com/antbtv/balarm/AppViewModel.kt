package com.antbtv.balarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Стартовое решение `MainActivity` (ADR-013 §2): показывать ли онбординг. Читается один раз — дальше навигация
 * живёт своим сохранённым состоянием, а смена флага после завершения онбординга экран не перестраивает.
 */
@HiltViewModel
class AppViewModel @Inject constructor(setup: SetupStateRepository) : ViewModel() {

    private val showOnboardingState = MutableStateFlow<Boolean?>(null)

    /** `null` — `SetupState` ещё не прочитан: системный splash держится, навигация не компонуется. */
    val showOnboarding: StateFlow<Boolean?> = showOnboardingState.asStateFlow()

    init {
        viewModelScope.launch { showOnboardingState.value = readShowOnboarding(setup) }
    }

    @Suppress("TooGenericExceptionCaught") // splash не должен висеть вечно из-за сбоя чтения
    private suspend fun readShowOnboarding(setup: SetupStateRepository): Boolean = try {
        // Зависшее чтение (NFR-2) тоже не держит splash: по таймауту — онбординг, как при ошибке.
        withTimeoutOrNull(READ_TIMEOUT_MS) { !setup.state.first().onboardingCompleted } ?: true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Онбординг ещё раз — безопасно (как ошибка чтения DataStore, ADR-013 §1).
        true
    }

    private companion object {
        const val READ_TIMEOUT_MS = 2_000L
    }
}
