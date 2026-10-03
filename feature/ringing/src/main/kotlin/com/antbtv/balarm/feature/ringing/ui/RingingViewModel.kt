package com.antbtv.balarm.feature.ringing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Экран звонка: только читает [RingingController.state] и отправляет команды (ADR-007 §3).
 *
 * `Idle` до первого `Ringing` — не конец звонка: уведомление с fullScreenIntent публикуется раньше,
 * чем движок принимает решение (≤ 2 с, ADR-007 §2). Поэтому экран ждёт [WAIT_FOR_RINGING] и только потом
 * закрывается; `Idle` после `Ringing` закрывает его сразу.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RingingViewModel @Inject constructor(private val controller: RingingController, private val clock: Clock) :
    ViewModel() {

    private var seenRinging = false

    private val session: Flow<Session> = controller.state.transformLatest { state ->
        when (state) {
            is RingingState.Ringing -> {
                seenRinging = true
                emit(Session.Active(state))
            }

            RingingState.Idle -> if (seenRinging) {
                emit(Session.Finished)
            } else {
                emit(Session.Waiting)
                delay(WAIT_FOR_RINGING)
                emit(Session.Finished)
            }
        }
    }

    val uiState: StateFlow<RingingUiState> = combine(session, minuteTicks(clock)) { session, now ->
        session.toUiState(now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = controller.state.value.toSession().toUiState(currentMinute(clock)),
    )

    fun onEvent(event: RingingEvent) {
        val ringing = controller.state.value as? RingingState.Ringing ?: return
        when (event) {
            RingingEvent.Dismiss -> controller.dismiss()
            RingingEvent.Snooze -> if (ringing.canSnooze) controller.snooze()
        }
    }

    private sealed interface Session {
        data object Waiting : Session

        data class Active(val ringing: RingingState.Ringing) : Session

        data object Finished : Session
    }

    private fun RingingState.toSession(): Session = when (this) {
        is RingingState.Ringing -> Session.Active(this)
        RingingState.Idle -> Session.Waiting
    }

    private fun Session.toUiState(now: LocalDateTime): RingingUiState = when (this) {
        Session.Waiting -> RingingUiState(now = now, phase = RingingPhase.WAITING)

        Session.Finished -> RingingUiState(now = now, phase = RingingPhase.FINISHED)

        is Session.Active -> RingingUiState(
            now = now,
            phase = RingingPhase.RINGING,
            label = ringing.alarm.label,
            snooze = ringing.toSnoozeUi(),
        )
    }

    companion object {
        /** С запасом к сторожу движка (2 с, ADR-007 §2): дольше `Idle` без звонка — экран не нужен. */
        val WAIT_FOR_RINGING = 5.seconds
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun RingingState.Ringing.toSnoozeUi(): SnoozeUi {
    val left = snoozesLeft
    return when {
        !canSnooze -> SnoozeUi.Hidden
        left == null -> SnoozeUi.Unlimited
        left <= 0 -> SnoozeUi.Hidden
        else -> SnoozeUi.Limited(left)
    }
}

private fun currentMinute(clock: Clock): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)

/**
 * Текущее время с шагом в минуту, по границам минут. Зона берётся из [clock] на каждом шаге
 * (`SystemZoneClock`), поэтому смена часового пояса во время звонка видна на следующей минуте.
 */
internal fun minuteTicks(clock: Clock): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now(clock)
        emit(now.truncatedTo(ChronoUnit.MINUTES))
        val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        delay(Duration.between(now, nextMinute).toMillis().coerceAtLeast(1))
    }
}.distinctUntilChanged()
