package com.antbtv.balarm.feature.alarmedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.alarm.AlarmDefaults
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmRepository
import com.antbtv.balarm.core.domain.alarm.TestAlarmRunner
import com.antbtv.balarm.core.domain.schedule.timeUntil
import com.antbtv.balarm.core.domain.sound.SoundPreview
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.SoundRef
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
import kotlinx.coroutines.flow.catch
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
 *
 * Звук (FR-EDIT-5, ADR-016/017, за `feature.alarmSound`): названия своих мелодий — из [sounds], смена громкости
 * сразу играет превью через [preview] (видимый экран — громкость можно менять, ADR-017 §6); превью замолкает
 * на `ON_STOP` экрана ([AlarmEditEvent.StopSoundPreview]), перед пикером и в [onCleared].
 */
@HiltViewModel(assistedFactory = AlarmEditViewModel.Factory::class)
class AlarmEditViewModel @AssistedInject constructor(
    @Assisted private val alarmId: Long?,
    private val repository: AlarmRepository,
    private val engine: AlarmEngine,
    private val testAlarms: TestAlarmRunner,
    private val clock: Clock,
    flags: FeatureFlagProvider,
    private val sounds: SoundRepository,
    private val preview: SoundPreview,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(alarmId: Long?): AlarmEditViewModel
    }

    private val snoozeVisible = flags.isEnabled(Feature.SNOOZE)
    private val soundVisible = flags.isEnabled(Feature.ALARM_SOUND)

    /** Выбор из пикера, пришедший до загрузки будильника (восстановление после смерти процесса). */
    private var pendingSound: SoundRef? = null

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<AlarmEditUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<AlarmEditEffect>(Channel.BUFFERED)

    /** Экран закрывается (`Close`/`Saved` отправлены): дальнейшие события не обрабатываются. */
    @Volatile private var closing = false

    /** Одно сообщение — одному подписчику; ждёт его, если экрана нет. Собирать одним сборщиком. */
    val effects: Flow<AlarmEditEffect> = effectChannel.receiveAsFlow()

    init {
        if (alarmId != null) load(alarmId)
        if (soundVisible) observeCustomTitles()
    }

    /** Названия живые: переименование/удаление в библиотеке сразу видно в строке «Мелодия». */
    private fun observeCustomTitles() {
        viewModelScope.launch {
            sounds.observeCustomSounds()
                // Библиотека недоступна — название своей мелодии не показываем, но и «удалена» не утверждаем.
                .catch { }
                .collect { list ->
                    val titles = list.associate { it.id to it.title }
                    _uiState.update { it.copy(customTitles = titles) }
                }
        }
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
            soundVisible = soundVisible,
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
                pendingSound?.let { reduce(AlarmEditEvent.SoundSelected(it)) }
                pendingSound = null
            }
        }
    }

    fun onEvent(event: AlarmEditEvent) {
        val state = _uiState.value
        // Тишина — всегда, даже когда экран уже закрывается.
        if (event == AlarmEditEvent.StopSoundPreview) return preview.stop()
        if (closing) return
        when (event) {
            AlarmEditEvent.Save -> save(state)

            AlarmEditEvent.Test -> test(state)

            AlarmEditEvent.ConfirmDelete -> delete(state)

            // Во время сохранения/удаления выход отложен: иначе экран закроется раньше тоста «зазвонит через …».
            AlarmEditEvent.DiscardConfirmed -> if (!state.saving) close()

            AlarmEditEvent.Back -> if (!state.saving) back(state)

            is AlarmEditEvent.SoundSelected, is AlarmEditEvent.VolumeChanged, AlarmEditEvent.PickSound ->
                onSoundEvent(state, event)

            else -> reduce(event)
        }
    }

    /** Звуковые события с побочными действиями: превью, пикер, выбор до загрузки. */
    private fun onSoundEvent(state: AlarmEditUiState, event: AlarmEditEvent) {
        when (event) {
            is AlarmEditEvent.SoundSelected -> if (state.loading) pendingSound = event.sound else reduce(event)
            is AlarmEditEvent.VolumeChanged -> changeVolume(event)
            AlarmEditEvent.PickSound -> pickSound(state)
            else -> reduce(event)
        }
    }

    /** Новая громкость сразу слышна: превью перезапускается с ней (до автостопа через 10 с). */
    private fun changeVolume(event: AlarmEditEvent.VolumeChanged) {
        val before = _uiState.value.draft.sound
        reduce(event)
        val after = _uiState.value.draft.sound
        if (after != before) preview.play(after)
    }

    private fun pickSound(state: AlarmEditUiState) {
        if (!state.soundVisible || state.loading || state.saving) return
        preview.stop()
        viewModelScope.launch { effectChannel.send(AlarmEditEffect.OpenSoundPicker(state.draft.sound.sound)) }
    }

    override fun onCleared() {
        preview.stop()
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
        if (state.loading || state.saving) return
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
