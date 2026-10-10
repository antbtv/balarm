package com.antbtv.balarm.feature.alarmedit

import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.MissionSpec
import com.antbtv.balarm.core.model.MissionTypeId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.SoundSettings
import com.antbtv.balarm.core.model.takeCodePoints
import com.antbtv.balarm.feature.missions.api.MissionDefinition
import java.time.Duration

/**
 * Чистые правки черновика и диалогов. Побочные действия (сохранить, тест, удалить, закрыть) — в
 * [AlarmEditViewModel]; здесь для них меняется только состояние (диалоги).
 */
internal object AlarmEditReducer {

    fun reduce(state: AlarmEditUiState, event: AlarmEditEvent): AlarmEditUiState {
        if (state.loading) return state
        return reduceMission(state, event) ?: reduceEdit(state, event) ?: reduceDialog(state, event)
    }

    /** Миссия и её подэкран; `null` — событие не про миссию. */
    private fun reduceMission(state: AlarmEditUiState, event: AlarmEditEvent): AlarmEditUiState? = when (event) {
        is AlarmEditEvent.MissionSelected -> state.editMission { withMission(event.type, state.missions) }

        is AlarmEditEvent.MissionConfigChanged ->
            state.editMission { withMissionConfig(event.config, state.selectedMission) }

        AlarmEditEvent.OpenMissionSettings ->
            if (state.missionVisible && !state.saving) state.copy(missionSettingsOpen = true, dialog = null) else state

        AlarmEditEvent.CloseMissionSettings -> state.copy(missionSettingsOpen = false)

        // Back на подэкране миссии возвращает к форме, а не спрашивает «Отменить изменения?».
        AlarmEditEvent.Back -> if (state.missionSettingsOpen) state.copy(missionSettingsOpen = false) else null

        else -> null
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

    /** Миссий нет (флаги выключены) — миссию будильника не трогаем, она сохранится как есть (FR-FLAG-3). */
    private fun AlarmEditUiState.editMission(change: Alarm.() -> Alarm): AlarmEditUiState =
        if (!missionVisible) this else edit(change)

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

/**
 * Выбор миссии: `null` — без миссии; тип не из [available] — без изменений. Повторный выбор того же типа сохраняет
 * его настройки, новый тип начинает с настроек по умолчанию; конфиг всегда нормализован.
 */
internal fun Alarm.withMission(type: MissionTypeId?, available: List<MissionDefinition>): Alarm {
    if (type == null) return copy(mission = null)
    val definition = available.firstOrNull { it.type == type } ?: return this
    val config = mission?.takeIf { it.type == type }?.config ?: definition.defaultConfig()
    return copy(mission = MissionSpec(type, definition.normalize(config)))
}

/** Новые настройки выбранной миссии [definition] (нормализуются); без выбранной миссии — без изменений. */
internal fun Alarm.withMissionConfig(config: String, definition: MissionDefinition?): Alarm {
    val spec = mission ?: return this
    if (definition == null || definition.type != spec.type) return this
    return copy(mission = spec.copy(config = definition.normalize(config)))
}
