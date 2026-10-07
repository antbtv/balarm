package com.antbtv.balarm.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.health.HealthReport
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.health.healthReportFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Вкладка «Настройки»: сводка по здоровью для строки «Здоровье будильника» (ADR-014 §5). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val checker: PermissionHealthChecker,
    setup: SetupStateRepository,
    repository: AlarmRepository,
) : ViewModel() {

    private val snapshot = MutableStateFlow(checker.snapshot())

    val uiState: StateFlow<SettingsUiState> =
        healthReportFlow(snapshot, setup.state, repository.observeAlarmsWithRuntime())
            .map { report -> SettingsUiState(loading = false, problems = report.problemCount()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /** `ON_RESUME` вкладки: перечитать платформенные статусы. */
    fun onResumed() {
        snapshot.value = checker.snapshot()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun HealthReport.problemCount(): Int = checks.count { it.status == HealthStatus.PROBLEM }
