package com.antbtv.balarm.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmDefaults
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.health.healthReportFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Здоровье будильника» (FR-REL-7, ADR-012, ADR-014 §5): отчёт, «Исправить», «Повторить планирование»,
 * тестовый будильник через минуту и подтверждение OEM-настроек. Без `Context`: интенты настроек запускает экран.
 */
@HiltViewModel
class HealthViewModel @Inject constructor(
    private val checker: PermissionHealthChecker,
    private val setup: SetupStateRepository,
    repository: AlarmRepository,
    private val engine: AlarmEngine,
    private val testAlarms: TestAlarmRunner,
    private val clock: Clock,
) : ViewModel() {

    private val snapshot = MutableStateFlow(checker.snapshot())
    private val retrying = MutableStateFlow(false)

    val uiState: StateFlow<HealthUiState> = combine(
        healthReportFlow(snapshot, setup.state, repository.observeAlarmsWithRuntime()),
        retrying,
    ) { report, retrying ->
        HealthUiState(
            loading = false,
            items = report.checks.map { HealthItemUi(it.item, it.status) },
            unscheduledAlarms = report.unscheduledAlarms,
            retrying = retrying,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HealthUiState())

    private val effectChannel = Channel<HealthEffect>(Channel.BUFFERED)

    val effects: Flow<HealthEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: HealthEvent) {
        when (event) {
            HealthEvent.Resumed -> snapshot.value = checker.snapshot()
            is HealthEvent.Fix -> fix(event.item)
            HealthEvent.RetryScheduling -> retry()
            HealthEvent.ScheduleTest -> scheduleTest()
            is HealthEvent.SetOemConfirmed -> setOemConfirmed(event.confirmed)
        }
    }

    private fun fix(item: HealthItem) {
        if (item == HealthItem.SCHEDULING) retry() else effect(HealthEffect.OpenFix(item))
    }

    /** Как мутации списка: уход с экрана не отменяет повтор планирования, начатый за миг до этого. */
    @Suppress("TooGenericExceptionCaught") // сбой — сообщение пользователю, не падение
    private fun retry() {
        if (!retrying.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { engine.rescheduleAll(RescheduleReason.USER_RETRY) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                effectChannel.send(HealthEffect.RetryFailed)
            } finally {
                retrying.value = false
            }
        }
    }

    private fun scheduleTest() {
        // Повторный тап заменяет прежний тест (TestAlarmRunner), поэтому защиты от двойного нажатия не нужно.
        val at = testAlarms.schedule(AlarmDefaults.testAlarm(LocalTime.now(clock)), TestAlarmRunner.HEALTH_DELAY)
        effect(HealthEffect.TestScheduled(at))
    }

    @Suppress("TooGenericExceptionCaught") // IOException DataStore — сообщение, не падение
    private fun setOemConfirmed(confirmed: Boolean) {
        viewModelScope.launch {
            try {
                setup.setOemBackgroundConfirmed(confirmed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                effectChannel.send(HealthEffect.SaveFailed)
            }
        }
    }

    private fun effect(effect: HealthEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
