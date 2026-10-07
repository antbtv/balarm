package com.antbtv.balarm.feature.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthReport
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.health.Severity
import com.antbtv.balarm.core.domain.health.healthReportFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class OnboardingUiState(
    val loading: Boolean = true,
    /** `null` — шагов не осталось, идёт завершение. */
    val step: OnboardingStep? = null,
    /** Номер шага из [totalSteps] (позиция в PRD §3.7; выполненные заранее шаги не показываются). */
    val stepNumber: Int = 1,
    val totalSteps: Int = OnboardingStep.entries.size,
    /** Что исправляет «Разрешить» (для шага батареи — оптимизация или режим «Ограничено»). */
    val fixItem: HealthItem? = null,
    /** Шаг показан в состоянии «режим Ограничено» (вариант «снимите ограничение»). */
    val batteryRestricted: Boolean = false,
    /** Показывать «Позже»/«Продолжить без этого». */
    val canPostpone: Boolean = false,
    /** Критичный шаг, по которому уже была попытка: «Продолжить без этого» с предупреждением. */
    val warnOnSkip: Boolean = false,
)

sealed interface OnboardingEvent {
    /** `ON_RESUME`: перечитать статусы (возврат из системных настроек, результат запроса). */
    data object Resumed : OnboardingEvent

    /** «Разрешить» / «Открыть настройки»: отмечает попытку и просит экран исправить пункт. */
    data object Primary : OnboardingEvent

    /** «Позже» / «Продолжить без этого». */
    data object Postpone : OnboardingEvent

    /**
     * «Продолжить» на шаге OEM с отмеченным «Я сделал» (чек-бокс держит экран, иначе шаг исчез бы при отметке).
     * Завершает шаг: подтверждение сохраняется.
     */
    data class OemConfirmed(val confirmed: Boolean) : OnboardingEvent
}

sealed interface OnboardingEffect {
    data class OpenFix(val item: HealthItem) : OnboardingEffect

    /** Онбординг пройден (или шагов не осталось): экран зовёт `onFinished`. */
    data object Finished : OnboardingEffect

    data object SaveFailed : OnboardingEffect
}

/**
 * Онбординг (ADR-013): текущий шаг вычисляется по свежему отчёту здоровья, в `SavedStateHandle` лежат только
 * `skipped` и `attempted` — после смерти процесса шаг восстанавливается по фактическим статусам.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val checker: PermissionHealthChecker,
    private val setup: SetupStateRepository,
    repository: AlarmRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val snapshot = MutableStateFlow(checker.snapshot())
    private val skipped = savedState.getStateFlow(KEY_SKIPPED, emptyList<String>())
    private val attempted = savedState.getStateFlow(KEY_ATTEMPTED, emptyList<String>())
    private val report: Flow<HealthReport> =
        healthReportFlow(snapshot, setup.state, repository.observeAlarmsWithRuntime())

    val uiState: StateFlow<OnboardingUiState> = combine(report, skipped, attempted) { report, skipped, attempted ->
        report.toUiState(skipped.toSteps(), attempted.toSteps())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingUiState())

    private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)

    val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            uiState.first { !it.loading && it.step == null }
            finish()
        }
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Resumed -> snapshot.value = checker.snapshot()
            OnboardingEvent.Primary -> primary()
            OnboardingEvent.Postpone -> postpone()
            is OnboardingEvent.OemConfirmed -> confirmOem(event.confirmed)
        }
    }

    private fun primary() {
        val state = uiState.value
        val step = state.step ?: return
        val item = state.fixItem ?: return
        savedState[KEY_ATTEMPTED] = ArrayList((attempted.value + step.name).distinct())
        effectChannel.trySend(OnboardingEffect.OpenFix(item))
    }

    private fun postpone() {
        val state = uiState.value
        val step = state.step ?: return
        if (!state.canPostpone) return
        savedState[KEY_SKIPPED] = ArrayList((skipped.value + step.name).distinct())
    }

    @Suppress("TooGenericExceptionCaught") // сбой DataStore — сообщение, не падение
    private fun confirmOem(confirmed: Boolean) {
        viewModelScope.launch {
            try {
                setup.setOemBackgroundConfirmed(confirmed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                effectChannel.send(OnboardingEffect.SaveFailed)
            }
        }
    }

    /** Если запись не удалась, онбординг покажется ещё раз при следующем запуске — это безопасно. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun finish() {
        try {
            setup.completeOnboarding()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            effectChannel.send(OnboardingEffect.SaveFailed)
        }
        effectChannel.send(OnboardingEffect.Finished)
    }

    private fun HealthReport.toUiState(
        skipped: Set<OnboardingStep>,
        attempted: Set<OnboardingStep>,
    ): OnboardingUiState {
        val step = currentStep(this, skipped) ?: return OnboardingUiState(loading = false, step = null)
        val postpone = canPostpone(step, this, attempted)
        return OnboardingUiState(
            loading = false,
            step = step,
            fixItem = step.fixItem(this),
            stepNumber = step.ordinal + 1,
            batteryRestricted = step == OnboardingStep.BATTERY &&
                status(HealthItem.BACKGROUND_RESTRICTION) == HealthStatus.PROBLEM,
            canPostpone = postpone,
            warnOnSkip = postpone && step.severity(this) == Severity.CRITICAL,
        )
    }

    private fun List<String>.toSteps(): Set<OnboardingStep> =
        mapNotNull { name -> OnboardingStep.entries.firstOrNull { it.name == name } }.toSet()

    private companion object {
        const val KEY_SKIPPED = "skipped"
        const val KEY_ATTEMPTED = "attempted"
    }
}
