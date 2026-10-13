package com.antbtv.balarm.feature.ringing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.MissionRun
import com.antbtv.balarm.core.domain.alarm.RingingController
import com.antbtv.balarm.core.domain.alarm.RingingState
import com.antbtv.balarm.core.domain.schedule.minuteTicks
import com.antbtv.balarm.feature.missions.api.MissionDefinition
import com.antbtv.balarm.feature.missions.api.MissionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/**
 * Экран звонка: только читает [RingingController.state] и отправляет команды (ADR-007 §3).
 *
 * `Idle` до первого `Ringing` — не конец звонка: уведомление с fullScreenIntent публикуется раньше,
 * чем движок принимает решение (≤ 2 с, ADR-007 §2). Поэтому экран ждёт [WAIT_FOR_RINGING] и только потом
 * закрывается; `Idle` после `Ringing` закрывает его сразу.
 *
 * Миссия (FR-RING-3, ADR-019): «Отключить» при миссии начинает её (`startMission`), отключает только
 * `completeMission`. Пока сервис ведёт сессию (`missionRun`), экран показывает миссию; определение берётся из
 * [MissionRegistry] по типу — неизвестный тип отключается как будильник без миссии. Прогресс «2/3» и счётчик
 * касаний живут здесь, привязанные к seed сессии: новая сессия начинает с нуля.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RingingViewModel @Inject constructor(
    private val controller: RingingController,
    private val missions: MissionRegistry,
    private val clock: Clock,
) : ViewModel() {

    private var seenRinging = false

    private val missionLocal = MutableStateFlow<MissionLocal?>(null)

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

    val uiState: StateFlow<RingingUiState> = combine(session, minuteTicks(clock), missionLocal) { session, now, local ->
        session.toUiState(now, local)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = controller.state.value.toSession().toUiState(currentMinute(clock), local = null),
    )

    fun onEvent(event: RingingEvent) {
        val ringing = controller.state.value as? RingingState.Ringing ?: return
        val run = ringing.missionRun
        when (event) {
            RingingEvent.Dismiss -> when {
                ringing.missionDefinition() == null -> controller.dismiss()
                run == null -> controller.startMission()
                else -> Unit // миссия уже идёт — «Отключить» на экране нет
            }

            RingingEvent.Snooze -> if (ringing.canSnooze) controller.snooze()

            else -> if (run != null) onMissionEvent(event, run)
        }
    }

    /** События экрана миссии — только внутри сессии, начатой сервисом. */
    private fun onMissionEvent(event: RingingEvent, run: MissionRun) {
        when (event) {
            RingingEvent.MissionInteraction -> {
                controller.missionInteraction()
                updateLocal(run.seed) { it.copy(interactions = it.interactions + 1) }
            }

            is RingingEvent.MissionProgress -> updateLocal(run.seed) { it.copy(done = event.done, total = event.total) }

            RingingEvent.MissionCompleted -> controller.completeMission()

            RingingEvent.MissionBack -> controller.leaveMission()

            RingingEvent.Dismiss, RingingEvent.Snooze -> Unit
        }
    }

    /**
     * Экран звонка скрыт при включённом экране (Home, Recents): у будильника с миссией сервис сразу делает звук
     * громким и возвращает экран (FR-RING-5, R17). Без миссии — как раньше: звонок просто продолжается.
     */
    fun onScreenHidden() {
        val ringing = controller.state.value as? RingingState.Ringing ?: return
        if (ringing.mission != null) controller.missionScreenHidden()
    }

    private fun updateLocal(seed: Long, block: (MissionLocal) -> MissionLocal) {
        missionLocal.update { current -> block(current?.takeIf { it.seed == seed } ?: MissionLocal(seed)) }
    }

    private fun RingingState.Ringing.missionDefinition(): MissionDefinition? =
        mission?.let { missions.definition(it.type) }

    /** Состояние миссии, известное только экрану: прогресс и число касаний текущей сессии ([seed]). */
    private data class MissionLocal(val seed: Long, val done: Int = 0, val total: Int = 0, val interactions: Int = 0)

    private sealed interface Session {
        data object Waiting : Session

        data class Active(val ringing: RingingState.Ringing) : Session

        data object Finished : Session
    }

    private fun RingingState.toSession(): Session = when (this) {
        is RingingState.Ringing -> Session.Active(this)
        RingingState.Idle -> Session.Waiting
    }

    private fun Session.toUiState(now: LocalDateTime, local: MissionLocal?): RingingUiState = when (this) {
        Session.Waiting -> RingingUiState(now = now, phase = RingingPhase.WAITING)

        Session.Finished -> RingingUiState(now = now, phase = RingingPhase.FINISHED)

        is Session.Active -> RingingUiState(
            now = now,
            phase = RingingPhase.RINGING,
            label = ringing.alarm.label,
            snooze = ringing.toSnoozeUi(),
            mission = ringing.toMissionUi(local),
        )
    }

    private fun RingingState.Ringing.toMissionUi(local: MissionLocal?): MissionUi? {
        val run = missionRun ?: return null
        val spec = mission ?: return null
        val definition = missions.definition(spec.type) ?: return null
        val own = local?.takeIf { it.seed == run.seed }
        return MissionUi(
            definition = definition,
            config = spec.config,
            seed = run.seed,
            resetSignal = run.idleResets,
            done = own?.done ?: 0,
            total = own?.total ?: 0,
            interactions = own?.interactions ?: 0,
            idleTimeout = missionIdleTimeout,
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
