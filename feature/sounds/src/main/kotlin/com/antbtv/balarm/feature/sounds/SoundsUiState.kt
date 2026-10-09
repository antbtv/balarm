package com.antbtv.balarm.feature.sounds

import androidx.compose.runtime.Immutable
import com.antbtv.balarm.core.model.CustomSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef

/**
 * Пикер мелодии (FR-SND-2, ADR-016 §8). Без строк: названия встроенных и тексты сообщений — у экрана.
 *
 * @property selected отмеченная мелодия; «Выбрать» возвращает её.
 * @property playing что звучит в превью (`null` — тишина); общий для всего приложения [SoundPreview.playing].
 * @property customs свои мелодии для блока «Мои мелодии»; `null` — ещё не загружены. При выключенном
 * `feature.customSounds` — только мелодия, с которой пикер открыт (если она своя и ещё существует).
 * @property customSoundsEnabled флаг `feature.customSounds`: «Добавить мелодию», «Управлять», пустой блок.
 * @property importing идёт импорт файла: индикатор прогресса, «Добавить мелодию» недоступна.
 * @property message результат действия для снекбара; экран сообщает о показе [SoundPickerEvent.MessageShown].
 */
@Immutable
data class SoundPickerUiState(
    val selected: SoundRef,
    val playing: SoundRef? = null,
    val customs: List<CustomSound>? = null,
    val customSoundsEnabled: Boolean = false,
    val importing: Boolean = false,
    val message: SoundMessage? = null,
) {
    /** Своя отмеченная мелодия удалена (или ещё не загружена): «Выбрать» недоступна. */
    val selectedMissing: Boolean
        get() = selected is SoundRef.Custom && customs?.none { it.id == selected.id } != false

    val canConfirm: Boolean get() = !selectedMissing

    /** Блок «Мои мелодии»: при флаге — всегда (даже пустой — с подсказкой), без флага — если есть что показать. */
    val customBlockVisible: Boolean get() = customSoundsEnabled || !customs.isNullOrEmpty()
}

sealed interface SoundPickerEvent {
    /** Тап по строке: выбрать и прослушать; по играющей — остановить. */
    data class RowClicked(val sound: SoundRef) : SoundPickerEvent

    /** SAF вернул документ (`content://…`). */
    data class ImportPicked(val uri: String) : SoundPickerEvent

    /** Системного пикера документов нет на устройстве. */
    data object ImportUnavailable : SoundPickerEvent

    data object MessageShown : SoundPickerEvent

    /** Экран ушёл в фон, закрывается или открывает SAF — тишина. */
    data object StopPreview : SoundPickerEvent
}

/**
 * Библиотека «Мои мелодии» (FR-SND-3). Весь экран — за `feature.customSounds` ([enabled]).
 *
 * @property sounds `null` — загрузка.
 * @property dialog открытый диалог; ввод нового названия хранится здесь же.
 */
@Immutable
data class SoundLibraryUiState(
    val enabled: Boolean,
    val sounds: List<CustomSound>? = null,
    val playing: SoundRef? = null,
    val importing: Boolean = false,
    val dialog: LibraryDialog? = null,
    val message: SoundMessage? = null,
)

@Immutable
sealed interface LibraryDialog {
    /** [input] — текущий ввод; «Сохранить» доступна при [canSave]. */
    data class Rename(val id: CustomSoundId, val input: String) : LibraryDialog {
        val canSave: Boolean get() = isValidTitle(input)
    }

    /** [usageCount] > 0 — предупреждение «используется N будильниками — переключатся на «Классика»». */
    data class ConfirmDelete(val id: CustomSoundId, val title: String, val usageCount: Int) : LibraryDialog
}

sealed interface SoundLibraryEvent {
    data class PlayClicked(val id: CustomSoundId) : SoundLibraryEvent

    data class RenameClicked(val id: CustomSoundId) : SoundLibraryEvent

    data class RenameInput(val text: String) : SoundLibraryEvent

    data object RenameConfirmed : SoundLibraryEvent

    data class DeleteClicked(val id: CustomSoundId) : SoundLibraryEvent

    data object DeleteConfirmed : SoundLibraryEvent

    data object DialogDismissed : SoundLibraryEvent

    data class ImportPicked(val uri: String) : SoundLibraryEvent

    data object ImportUnavailable : SoundLibraryEvent

    data object MessageShown : SoundLibraryEvent

    data object StopPreview : SoundLibraryEvent
}

/** Итог действия для снекбара; текст — у экрана ([soundMessageText]). */
@Immutable
sealed interface SoundMessage {
    data class Imported(val title: String) : SoundMessage

    data class TooLarge(val limitBytes: Long) : SoundMessage

    data object Unsupported : SoundMessage

    data object NoSpace : SoundMessage

    data object ImportFailed : SoundMessage

    data class Deleted(val title: String, val switchedAlarms: Int) : SoundMessage

    data object DeleteFailed : SoundMessage

    data object RenameFailed : SoundMessage
}

/** Название своей мелодии: после `trim` непустое и не длиннее [CustomSound.MAX_TITLE_LENGTH] code points. */
internal fun isValidTitle(input: String): Boolean {
    val title = input.trim()
    return title.isNotEmpty() && title.codePointCount(0, title.length) <= CustomSound.MAX_TITLE_LENGTH
}
