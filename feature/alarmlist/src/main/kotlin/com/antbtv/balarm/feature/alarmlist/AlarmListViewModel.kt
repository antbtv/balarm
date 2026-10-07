package com.antbtv.balarm.feature.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.AlarmWithRuntime
import com.antbtv.balarm.core.domain.alarm.isActive
import com.antbtv.balarm.core.domain.alarm.nextTrigger
import com.antbtv.balarm.core.domain.alarm.upcomingTrigger
import com.antbtv.balarm.core.domain.health.PermissionHealthChecker
import com.antbtv.balarm.core.domain.health.SetupStateRepository
import com.antbtv.balarm.core.domain.health.healthReportFlow
import com.antbtv.balarm.core.domain.schedule.minuteTicks
import com.antbtv.balarm.core.domain.schedule.timeUntil
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.TriggerKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Список будильников (FR-LIST-1…4). Читает [AlarmRepository.observeAlarmsWithRuntime] и минутный тик,
 * меняет расписание только через [AlarmEngine] (ADR-011 §5). Тик живёт, пока на экран подписаны (NFR-9).
 */
@HiltViewModel
class AlarmListViewModel @Inject constructor(
    repository: AlarmRepository,
    private val engine: AlarmEngine,
    private val clock: Clock,
    private val checker: PermissionHealthChecker,
    setup: SetupStateRepository,
) : ViewModel() {

    private val snapshot = MutableStateFlow(checker.snapshot())

    private val alarms = repository.observeAlarmsWithRuntime().distinctUntilChanged()

    val uiState: StateFlow<AlarmListUiState> = combine(
        alarms,
        minuteTicks(clock),
        healthReportFlow(snapshot, setup.state, alarms),
    ) { alarms, _, report ->
        // Тик нужен только как повод пересчитать; момент берём точный, иначе «через 1 мин» округлится не туда.
        alarms.toUiState(clock).copy(healthWarning = report.needsAttention)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AlarmListUiState(),
    )

    private val effectChannel = Channel<AlarmListEffect>(Channel.BUFFERED)

    /** Тосты: каждое сообщение доставляется ровно одному подписчику и ждёт его, если экрана нет. */
    val effects: Flow<AlarmListEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: AlarmListEvent) {
        when (event) {
            AlarmListEvent.Resumed -> snapshot.value = checker.snapshot()
            is AlarmListEvent.Toggle -> toggle(event.id, event.enabled)
            is AlarmListEvent.Delete -> delete(event.id)
        }
    }

    /**
     * Мутации расписания не отменяются уходом с экрана (как «Сохранить» в редакторе): тап по тумблеру или удаление,
     * начатые за миг до закрытия, всё равно доходят до движка.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // сбой — сообщение пользователю, не падение
    private fun toggle(id: AlarmId, enabled: Boolean) {
        viewModelScope.launch {
            val result = try {
                withContext(NonCancellable) { engine.setEnabled(id, enabled) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effectChannel.send(if (enabled) AlarmListEffect.ScheduleFailed else AlarmListEffect.DisableFailed)
                return@launch
            }
            val effect = when {
                result == null || !enabled -> null
                !result.scheduled -> AlarmListEffect.ScheduleFailed
                else -> result.nextTriggerAt?.let { AlarmListEffect.RingsIn(timeUntil(clock.instant(), it)) }
            }
            effect?.let { effectChannel.send(it) }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // см. toggle
    private fun delete(id: AlarmId) {
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { engine.delete(id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effectChannel.send(AlarmListEffect.DeleteFailed)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun List<AlarmWithRuntime>.toUiState(clock: Clock): AlarmListUiState {
    val now = clock.instant()
    return AlarmListUiState(
        loading = false,
        alarms = map { it.toItem(now, clock) },
        nextIn = nextTrigger(now)?.let { timeUntil(now, it) },
    )
}

private fun AlarmWithRuntime.toItem(now: Instant, clock: Clock): AlarmItemUi = AlarmItemUi(
    id = alarm.id,
    time = alarm.time,
    label = alarm.label,
    repeatDays = alarm.repeatDays,
    active = isActive(now),
    subtitle = if (alarm.enabled && runtime?.scheduleFailed == true) {
        AlarmSubtitle.NotScheduled
    } else {
        upcomingTrigger(now)?.let { subtitleFor(it, now, clock) }
    },
)

private fun AlarmWithRuntime.subtitleFor(at: Instant, now: Instant, clock: Clock): AlarmSubtitle? {
    val zone = clock.zone
    val triggerDate = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
        runtime?.nextTriggerKind == TriggerKind.SNOOZE -> AlarmSubtitle.SnoozedUntil(at.atZone(zone).toLocalTime())
        triggerDate == today -> AlarmSubtitle.Today
        triggerDate == today.plusDays(1) -> AlarmSubtitle.Tomorrow
        else -> null
    }
}
