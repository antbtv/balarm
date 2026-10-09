package com.antbtv.balarm.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.health.HealthReport
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.health.healthReportFlow
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Вкладка «Настройки»: сводка по здоровью для строки «Здоровье будильника» (ADR-014 §5) и видимость строки
 * «Мелодии» по флагу `feature.customSounds` (читается один раз: флаг не меняется без перезапуска экрана).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val checker: PermissionHealthChecker,
    setup: SetupStateRepository,
    repository: AlarmRepository,
    flags: FeatureFlagProvider,
) : ViewModel() {

    private val snapshot = MutableStateFlow(checker.snapshot())
    private val soundsVisible = flags.isEnabled(Feature.CUSTOM_SOUNDS)

    val uiState: StateFlow<SettingsUiState> =
        healthReportFlow(snapshot, setup.state, repository.observeAlarmsWithRuntime())
            .map { report ->
                SettingsUiState(loading = false, problems = report.problemCount(), soundsVisible = soundsVisible)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                // Строка «Мелодии» не зависит от сводки и не появляется с задержкой.
                SettingsUiState(soundsVisible = soundsVisible),
            )

    /** `ON_RESUME` вкладки: перечитать платформенные статусы. */
    fun onResumed() {
        snapshot.value = checker.snapshot()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun HealthReport.problemCount(): Int = checks.count { it.status == HealthStatus.PROBLEM }
