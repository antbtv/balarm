package com.antbtv.balarm.feature.alarmedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmDefaults
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.domain.schedule.timeUntil
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Редактор будильника (FR-EDIT). [alarmId] `null` — новый будильник со значениями по умолчанию.
 * Мутации — только через [AlarmEngine] (ADR-011 §5), «Тест» — через [TestAlarmRunner] (ADR-010).
 *
 * Передаётся assisted-параметром как `Long?`, а не `AlarmId?`: `value class` с Dagger/KSP хрупок.
 *
 * Черновик переживает поворот (ViewModel), но не смерть процесса — принято для v0.1.
 */
@HiltViewModel(assistedFactory = AlarmEditViewModel.Factory::class)
class AlarmEditViewModel @AssistedInject constructor(
    @Assisted private val alarmId: Long?,
    private val repository: AlarmRepository,
    private val engine: AlarmEngine,
    private val testAlarms: TestAlarmRunner,
    private val clock: Clock,
    flags: FeatureFlagProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(alarmId: Long?): AlarmEditViewModel
    }

    private val snoozeVisible = flags.isEnabled(Feature.SNOOZE)

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<AlarmEditUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<AlarmEditEffect>(Channel.BUFFERED)

    /** Экран закрывается (`Close`/`Saved` отправлены): дальнейшие события не обрабатываются. */
    @Volatile private var closing = false

    /** Одно сообщение — одному подписчику; ждёт его, если экрана нет. Собирать одним сборщиком. */
    val effects: Flow<AlarmEditEffect> = effectChannel.receiveAsFlow()

    init {
        if (alarmId != null) load(alarmId)
    }

    private fun initialState(): AlarmEditUiState {
        val isNew = alarmId == null
        val placeholder = AlarmDefaults.newAlarm(LocalTime.now(clock))
        return AlarmEditUiState(
            loading = !isNew,
            isNew = isNew,
            initial = placeholder,
            draft = placeholder,
            snoozeVisible = snoozeVisible,
        )
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // сбой — сообщение пользователю, не падение
    private fun load(id: Long) {
        viewModelScope.launch {
            val stored = try {
                repository.get(AlarmId(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(AlarmEditEffect.LoadFailed)
                return@launch
            }
            if (stored == null) {
                // Удалён, пока редактор был в стеке (смерть процесса, удаление с другого экрана): нечего править.
                send(AlarmEditEffect.Close)
            } else {
                _uiState.update { it.copy(loading = false, initial = stored, draft = stored) }
            }
        }
    }

    fun onEvent(event: AlarmEditEvent) {
        val state = _uiState.value
        if (closing) return
        when (event) {
            AlarmEditEvent.Save -> save(state)

            AlarmEditEvent.Test -> test(state)

            AlarmEditEvent.ConfirmDelete -> delete(state)

            // Во время сохранения/удаления выход отложен: иначе экран закроется раньше тоста «зазвонит через …».
            AlarmEditEvent.DiscardConfirmed -> if (!state.saving) close()

            AlarmEditEvent.Back -> if (!state.saving) back(state)

            else -> reduce(event)
        }
    }

    private fun back(state: AlarmEditUiState) {
        if (state.isDirty && !state.loading) reduce(AlarmEditEvent.Back) else close()
    }

    private fun reduce(event: AlarmEditEvent) {
        _uiState.update { AlarmEditReducer.reduce(it, event) }
    }

    /** После первого `Close`/`Saved` экран уходит; повторные события (двойной Back) игнорируются. */
    private fun close() {
        closing = true
        viewModelScope.launch { effectChannel.send(AlarmEditEffect.Close) }
    }

    private suspend fun send(effect: AlarmEditEffect) {
        if (effect is AlarmEditEffect.Close || effect is AlarmEditEffect.Saved) closing = true
        effectChannel.send(effect)
    }

    /** Метка обрезается пробелами по краям (ADR-011 §7); «Сохранить» всегда включает будильник. */
    private fun AlarmEditUiState.toSave(): Alarm = draft.copy(label = draft.label.trim(), enabled = true)

    /**
     * Сохраняет всегда, даже без правок: у нового будильника это создание, у выключенного — включение
     * («Сохранить» включает), у включённого движок идемпотентен и не теряет snooze. Тост «зазвонит через …»
     * подтверждает результат.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // сбой — сообщение пользователю, не падение
    private fun save(state: AlarmEditUiState) {
        if (state.loading || state.saving) return
        _uiState.update { it.copy(saving = true, dialog = null) }
        viewModelScope.launch {
            val effect = try {
                val result = withContext(NonCancellable) { engine.save(state.toSave()) }
                val until = result.nextTriggerAt?.takeIf { result.scheduled }?.let { timeUntil(clock.instant(), it) }
                AlarmEditEffect.Saved(result, until)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false) }
                AlarmEditEffect.SaveFailed
            }
            send(effect)
        }
    }

    private fun test(state: AlarmEditUiState) {
        if (state.loading) return
        val at = testAlarms.schedule(state.toSave(), TestAlarmRunner.EDITOR_DELAY)
        viewModelScope.launch { effectChannel.send(AlarmEditEffect.TestScheduled(at)) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // см. save
    private fun delete(state: AlarmEditUiState) {
        // Только из открытого подтверждения и не поверх сохранения: двойной тап не удаляет дважды.
        if (state.dialog != EditDialog.ConfirmDelete || state.saving) return
        val id = alarmId?.takeIf { state.canDelete } ?: return
        _uiState.update { it.copy(saving = true, dialog = null) }
        viewModelScope.launch {
            val effect = try {
                withContext(NonCancellable) { engine.delete(AlarmId(id)) }
                AlarmEditEffect.Close
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false) }
                AlarmEditEffect.DeleteFailed
            }
            send(effect)
        }
    }
}
