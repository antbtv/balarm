package com.antbtv.balarm.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.health.HealthReport
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.health.healthReportFlow
import com.antbtv.balarm.core.domain.settings.SettingsRepository
import com.antbtv.balarm.core.model.AppSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Вкладка «Настройки»: сводка по здоровью для строки «Здоровье будильника» (ADR-014 §5), видимость строки
 * «Мелодии» по флагу `feature.customSounds` и секция «Общие» по флагу `feature.globalSettings` (ADR-021). Флаги
 * читаются один раз: без перезапуска экрана они не меняются.
 *
 * Секция «Общие» читает и пишет настройки только через [SettingsRepository]; выбор сохраняется сразу, звонок
 * подхватит его со следующего старта (снимок на звонок, ADR-021 п. 4). При выключенном флаге репозиторий не
 * читается вовсе.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val checker: PermissionHealthChecker,
    setup: SetupStateRepository,
    repository: AlarmRepository,
    flags: FeatureFlagProvider,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val snapshot = MutableStateFlow(checker.snapshot())
    private val soundsVisible = flags.isEnabled(Feature.CUSTOM_SOUNDS)
    private val generalVisible = flags.isEnabled(Feature.GLOBAL_SETTINGS)
    private val saveFailed = MutableStateFlow(false)

    // Сводка и настройки не ждут друг друга: каждая часть сначала «ещё читается» (null).
    private val problems: Flow<Int?> =
        healthReportFlow(snapshot, setup.state, repository.observeAlarmsWithRuntime())
            .map<HealthReport, Int?> { it.problemCount() }
            .onStart { emit(null) }

    private val general: Flow<GeneralSettingsUi?> =
        if (generalVisible) {
            settings.settings.map<AppSettings, GeneralSettingsUi?> { it.toGeneralUi() }.onStart { emit(null) }
        } else {
            flowOf(null)
        }

    // Первые состояния полей совпадают с начальным состоянием — StateFlow их не повторяет.
    private val initialState = SettingsUiState(soundsVisible = soundsVisible, generalVisible = generalVisible)

    val uiState: StateFlow<SettingsUiState> =
        combine(problems, general, saveFailed) { problems, general, saveFailed ->
            initialState.copy(
                loading = problems == null,
                problems = problems ?: 0,
                general = general,
                saveFailed = saveFailed,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            // Строки «Мелодии» и «Общие» не зависят от сводки и не появляются с задержкой.
            initialState,
        )

    /** `ON_RESUME` вкладки: перечитать платформенные статусы. */
    fun onResumed() {
        snapshot.value = checker.snapshot()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.AutoStopChanged -> save { it.copy(autoStop = event.value) }

            is SettingsEvent.MissionIdleChanged ->
                save { it.copy(missionIdleTimeout = Duration.ofSeconds(event.seconds)) }

            is SettingsEvent.MissionDuckChanged -> save { it.copy(missionDuckPercent = event.percent) }

            is SettingsEvent.FirstDayOfWeekChanged -> save { it.copy(firstDayOfWeek = event.day) }

            SettingsEvent.SaveFailedShown -> saveFailed.value = false
        }
    }

    private fun save(transform: (AppSettings) -> AppSettings) {
        if (!generalVisible) return
        viewModelScope.launch {
            // Значение на экране — из репозитория: при ошибке остаётся прежнее, пользователь видит снекбар.
            try {
                settings.update(transform)
            } catch (_: IOException) {
                saveFailed.value = true
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun HealthReport.problemCount(): Int = checks.count { it.status == HealthStatus.PROBLEM }
