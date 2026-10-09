package com.antbtv.balarm.feature.alarmedit

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.takeCodePoints
import java.time.Duration

/**
 * Чистые правки черновика и диалогов. Побочные действия (сохранить, тест, удалить, закрыть) — в
 * [AlarmEditViewModel]; здесь для них меняется только состояние (диалоги).
 */
internal object AlarmEditReducer {

    fun reduce(state: AlarmEditUiState, event: AlarmEditEvent): AlarmEditUiState {
        if (state.loading) return state
        return reduceEdit(state, event) ?: reduceDialog(state, event)
    }

    /** Правки черновика; `null` — событие не про черновик. */
    private fun reduceEdit(state: AlarmEditUiState, event: AlarmEditEvent): AlarmEditUiState? = when (event) {
        is AlarmEditEvent.TimeChanged -> state.edit { copy(time = event.time.withSecond(0).withNano(0)) }

        is AlarmEditEvent.DayToggled -> state.edit {
            copy(repeatDays = if (event.day in repeatDays) repeatDays - event.day else repeatDays + event.day)
        }

        is AlarmEditEvent.PresetSelected -> state.edit { copy(repeatDays = event.preset.toggle(repeatDays)) }

        is AlarmEditEvent.LabelChanged -> state.edit { copy(label = event.label.cleanLabel()) }

        is AlarmEditEvent.SnoozeIntervalSelected -> state.editSnooze { snooze.withInterval(event.interval) }

        is AlarmEditEvent.SnoozeLimitSelected -> state.editSnooze { snooze.withMaxCount(event.maxCount) }

        is AlarmEditEvent.SoundSelected -> state.editSound { copy(sound = sound.copy(sound = event.sound)) }

        is AlarmEditEvent.VolumeChanged ->
            state.editSound { copy(sound = sound.copy(volumePercent = snapVolume(event.percent))) }

        is AlarmEditEvent.FadeInSelected -> if (event.fadeIn in SoundSettings.FADE_IN_OPTIONS) {
            state.editSound { copy(sound = sound.copy(fadeIn = event.fadeIn)) }
        } else {
            state.copy(dialog = null)
        }

        is AlarmEditEvent.VibrateChanged -> state.editSound { copy(vibrate = event.vibrate) }

        else -> null
    }

    /** Диалоги; остальное (сохранить, тест, подтвердить удаление, закрыть) — действия ViewModel. */
    private fun reduceDialog(state: AlarmEditUiState, event: AlarmEditEvent): AlarmEditUiState = when (event) {
        AlarmEditEvent.ShowFadeInDialog -> state.showFadeInDialog()

        AlarmEditEvent.ShowSnoozeIntervalDialog ->
            if (state.snoozeVisible) state.copy(dialog = EditDialog.SnoozeInterval) else state

        AlarmEditEvent.ShowSnoozeLimitDialog ->
            if (state.snoozeVisible &&
                state.draft.snooze.isEnabled
            ) {
                state.copy(dialog = EditDialog.SnoozeLimit)
            } else {
                state
            }

        AlarmEditEvent.Delete -> if (state.canDelete &&
            !state.saving
        ) {
            state.copy(dialog = EditDialog.ConfirmDelete)
        } else {
            state
        }

        AlarmEditEvent.Back -> if (state.isDirty) state.copy(dialog = EditDialog.ConfirmDiscard) else state

        AlarmEditEvent.DialogDismissed -> state.copy(dialog = null)

        else -> state
    }

    private fun AlarmEditUiState.edit(change: Alarm.() -> Alarm): AlarmEditUiState =
        if (saving) this else copy(draft = draft.change(), dialog = null)

    /** Секция snooze скрыта флагом — данные будильника не трогаем (FR-FLAG-3). */
    private fun AlarmEditUiState.editSnooze(change: Alarm.() -> SnoozeSettings): AlarmEditUiState =
        if (!snoozeVisible) this else edit { copy(snooze = change()) }

    private fun AlarmEditUiState.showFadeInDialog(): AlarmEditUiState =
        if (soundVisible) copy(dialog = EditDialog.FadeIn) else this

    /** Секция «Звук» скрыта флагом — звук и вибрацию будильника не трогаем (ADR-016 §7). */
    private fun AlarmEditUiState.editSound(change: Alarm.() -> Alarm): AlarmEditUiState =
        if (!soundVisible) this else edit(change)

    /** Однострочная метка не длиннее лимита: перевод строки → пробел, лишнее отрезается по code points. */
    private fun String.cleanLabel(): String = replace(LINE_BREAKS, " ").takeCodePoints(Alarm.MAX_LABEL_LENGTH)

    private val LINE_BREAKS = Regex("\r\n|[\n\r\u0085\u2028\u2029]")
}

/**
 * Интервал `null` выключает snooze, сохраняя лимит; включение из выключенного состояния без лимита
 * (`DISABLED`) берёт лимит по умолчанию, а не «без ограничения». Следствие: «∞ → выкл → вкл» возвращает лимит
 * по умолчанию, а не ∞ (лимит `null` при выключении неотличим от «не задан»).
 */
internal fun SnoozeSettings.withInterval(interval: Duration?): SnoozeSettings = when {
    interval == null -> SnoozeSettings(interval = null, maxCount = maxCount)
    isEnabled -> SnoozeSettings(interval = interval, maxCount = maxCount)
    else -> SnoozeSettings(interval = interval, maxCount = maxCount ?: SnoozeSettings.DEFAULT.maxCount)
}

internal fun SnoozeSettings.withMaxCount(maxCount: Int?): SnoozeSettings =
    if (isEnabled) SnoozeSettings(interval = interval, maxCount = maxCount) else this

/** Громкость слайдера → допустимое значение `SoundSettings`: 10..100, ближайшее кратное 10. */
internal fun snapVolume(percent: Int): Int {
    val step = SoundSettings.VOLUME_STEP
    val clamped = percent.coerceIn(SoundSettings.MIN_VOLUME, SoundSettings.MAX_VOLUME)
    return (clamped + step / 2) / step * step
}
