package com.antbtv.balarm.feature.sounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antbtv.balarm.core.domain.sound.ImportResult
import com.antbtv.balarm.core.domain.sound.SoundPreview
import com.antbtv.balarm.core.domain.sound.SoundRepository
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Мои мелодии» (FR-SND-3): прослушать, переименовать, удалить, добавить. Весь экран — за `feature.customSounds`:
 * без флага ничего не загружается и не меняется.
 *
 * Удаление — в два шага: [SoundRepository.usageCount] для предупреждения, затем [SoundRepository.delete]
 * (будильники атомарно переключаются на DEFAULT, ADR-016 §5) и итог в снекбаре. Превью — как в пикере.
 */
@HiltViewModel
class SoundLibraryViewModel @Inject constructor(
    private val sounds: SoundRepository,
    private val preview: SoundPreview,
    flags: FeatureFlagProvider,
) : ViewModel() {

    private val enabled = flags.isEnabled(Feature.CUSTOM_SOUNDS)

    private val _uiState = MutableStateFlow(SoundLibraryUiState(enabled = enabled))
    val uiState: StateFlow<SoundLibraryUiState> = _uiState.asStateFlow()

    /** Операция над мелодией (переименование/удаление) уже идёт: двойной тап не повторяет её. */
    private var busy = false

    init {
        if (enabled) {
            viewModelScope.launch { preview.playing.collect { ref -> _uiState.update { it.copy(playing = ref) } } }
            viewModelScope.launch {
                sounds.observeCustomSounds()
                    .catch { emit(emptyList()) }
                    .collect { list -> _uiState.update { it.copy(sounds = list) } }
            }
        }
    }

    fun onEvent(event: SoundLibraryEvent) {
        if (event == SoundLibraryEvent.StopPreview) return preview.stop()
        if (!enabled) return
        when (event) {
            is SoundLibraryEvent.PlayClicked -> togglePlay(event.id)
            is SoundLibraryEvent.RenameClicked -> openRename(event.id)
            is SoundLibraryEvent.RenameInput -> updateRenameInput(event.text)
            SoundLibraryEvent.RenameConfirmed -> rename()
            is SoundLibraryEvent.DeleteClicked -> askDelete(event.id)
            SoundLibraryEvent.DeleteConfirmed -> delete()
            SoundLibraryEvent.DialogDismissed -> _uiState.update { it.copy(dialog = null) }
            is SoundLibraryEvent.ImportPicked -> import(event.uri)
            SoundLibraryEvent.ImportUnavailable -> _uiState.update { it.copy(message = SoundMessage.ImportFailed) }
            SoundLibraryEvent.MessageShown -> _uiState.update { it.copy(message = null) }
            SoundLibraryEvent.StopPreview -> Unit
        }
    }

    private fun togglePlay(id: CustomSoundId) {
        val ref = SoundRef.Custom(id)
        if (preview.playing.value == ref) preview.stop() else preview.play(SoundSettings(sound = ref))
    }

    private fun find(id: CustomSoundId) = _uiState.value.sounds?.firstOrNull { it.id == id }

    private fun openRename(id: CustomSoundId) {
        val sound = find(id) ?: return
        _uiState.update { it.copy(dialog = LibraryDialog.Rename(id, sound.title)) }
    }

    private fun updateRenameInput(text: String) {
        _uiState.update { state ->
            val dialog = state.dialog as? LibraryDialog.Rename ?: return@update state
            state.copy(dialog = dialog.copy(input = text))
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // сбой — сообщение пользователю, не падение
    private fun rename() {
        val dialog = _uiState.value.dialog as? LibraryDialog.Rename ?: return
        if (!dialog.canSave || busy) return
        busy = true
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            val renamed = try {
                withContext(NonCancellable) { sounds.rename(dialog.id, dialog.input.trim()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                busy = false
            }
            // Успех виден в списке (живой Flow) — сообщение только о неудаче.
            if (!renamed) _uiState.update { it.copy(message = SoundMessage.RenameFailed) }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // см. rename
    private fun askDelete(id: CustomSoundId) {
        val sound = find(id) ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val usage = try {
                sounds.usageCount(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Число неизвестно — предупреждение не показываем, но удаление всё равно переключит будильники.
                0
            } finally {
                busy = false
            }
            _uiState.update { it.copy(dialog = LibraryDialog.ConfirmDelete(id, sound.title, usage)) }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // см. rename
    private fun delete() {
        val dialog = _uiState.value.dialog as? LibraryDialog.ConfirmDelete ?: return
        if (busy) return
        busy = true
        _uiState.update { it.copy(dialog = null) }
        // Удаляемая мелодия не должна доигрывать из уже удалённого файла.
        if (preview.playing.value == SoundRef.Custom(dialog.id)) preview.stop()
        viewModelScope.launch {
            val message = try {
                val switched = withContext(NonCancellable) { sounds.delete(dialog.id) }
                SoundMessage.Deleted(dialog.title, switched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SoundMessage.DeleteFailed
            } finally {
                busy = false
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    private fun import(uri: String) {
        if (_uiState.value.importing) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch {
            val result = sounds.importSafely(uri)
            _uiState.update { it.copy(importing = false, message = result.toMessage()) }
            if (result is ImportResult.Imported) preview.play(SoundSettings(sound = SoundRef.Custom(result.sound.id)))
        }
    }

    override fun onCleared() {
        preview.stop()
    }
}
