package com.antbtv.balarm.feature.sounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundPreview
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Пикер мелодии (FR-SND-2, ADR-016 §8). Тап по строке отмечает мелодию и сразу играет превью
 * (`SoundSettings(sound = ref)`), тап по играющей — останавливает. Импорт — через [SoundRepository.import];
 * импортированная мелодия отмечается и звучит. Превью замолкает на `ON_STOP`, при закрытии и в [onCleared].
 *
 * [selected] — `SoundRef.encode()` (как в ключе навигации `SoundPickerKey`); битое значение → DEFAULT.
 */
@HiltViewModel(assistedFactory = SoundPickerViewModel.Factory::class)
class SoundPickerViewModel @AssistedInject constructor(
    @Assisted selected: String,
    private val sounds: SoundRepository,
    private val preview: SoundPreview,
    flags: FeatureFlagProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(selected: String): SoundPickerViewModel
    }

    private val initial: SoundRef = SoundRef.decode(selected) ?: SoundRef.DEFAULT
    private val customEnabled = flags.isEnabled(Feature.CUSTOM_SOUNDS)

    private val _uiState = MutableStateFlow(SoundPickerUiState(selected = initial, customSoundsEnabled = customEnabled))
    val uiState: StateFlow<SoundPickerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { preview.playing.collect { ref -> _uiState.update { it.copy(playing = ref) } } }
        // Без флага библиотека нужна, только чтобы показать уже выбранную свою мелодию (ADR-016 §7).
        if (customEnabled || initial is SoundRef.Custom) observeCustoms()
    }

    private fun observeCustoms() {
        viewModelScope.launch {
            sounds.observeCustomSounds()
                // Библиотека недоступна — блок пуст, встроенные выбираются как обычно.
                .catch { emit(emptyList()) }
                .collect { list ->
                    val visible = if (customEnabled) list else list.filter { SoundRef.Custom(it.id) == initial }
                    _uiState.update { it.copy(customs = visible) }
                }
        }
    }

    fun onEvent(event: SoundPickerEvent) {
        when (event) {
            is SoundPickerEvent.RowClicked -> onRowClicked(event.sound)
            is SoundPickerEvent.ImportPicked -> import(event.uri)
            SoundPickerEvent.ImportUnavailable -> _uiState.update { it.copy(message = SoundMessage.ImportFailed) }
            SoundPickerEvent.MessageShown -> _uiState.update { it.copy(message = null) }
            SoundPickerEvent.StopPreview -> preview.stop()
        }
    }

    private fun onRowClicked(sound: SoundRef) {
        if (preview.playing.value == sound) {
            preview.stop()
            return
        }
        _uiState.update { it.copy(selected = sound) }
        preview.play(SoundSettings(sound = sound))
    }

    private fun import(uri: String) {
        if (!customEnabled || _uiState.value.importing) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch {
            val result = sounds.importSafely(uri)
            val imported = (result as? ImportResult.Imported)?.let { SoundRef.Custom(it.sound.id) }
            _uiState.update {
                it.copy(importing = false, message = result.toMessage(), selected = imported ?: it.selected)
            }
            // Новая мелодия сразу слышна: пользователь проверяет, что выбрал тот файл.
            imported?.let { preview.play(SoundSettings(sound = it)) }
        }
    }

    override fun onCleared() {
        preview.stop()
    }
}
