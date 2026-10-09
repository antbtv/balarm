package com.antbtv.balarm.feature.alarmedit

import androidx.compose.runtime.Immutable
import com.antbtv.balarm.core.designsystem.component.DayPreset
import com.antbtv.balarm.core.domain.alarm.ScheduleResult
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.BuiltinSound
import com.antbtv.balarm.core.model.CustomSoundId
import com.antbtv.balarm.core.model.SoundRef
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Состояние редактора (FR-EDIT). Без строк и локали: время, дни и тексты форматирует экран.
 *
 * @property initial то, с чем редактор открыт (для нового — значения по умолчанию); [isDirty] — отличие от него.
 * @property draft черновик; метка и snooze правятся только через события, значения проверены редуктором.
 * @property snoozeVisible флаг `feature.snooze`: при `false` секция скрыта и `draft.snooze` не меняется.
 * @property soundVisible флаг `feature.alarmSound`: при `false` секции «Звук» нет, `draft.sound` и `draft.vibrate`
 * не меняются (сохранённые значения звонят как есть, ADR-016 §7).
 * @property customTitles названия своих мелодий из библиотеки; `null` — ещё не загружены (или флаг выключен).
 * @property saving «Сохранить» нажато: повторные нажатия игнорируются, пока операция движка не закончилась.
 */
@Immutable
data class AlarmEditUiState(
    val loading: Boolean,
    val isNew: Boolean,
    val initial: Alarm,
    val draft: Alarm,
    val snoozeVisible: Boolean,
    val soundVisible: Boolean = false,
    val customTitles: Map<CustomSoundId, String>? = null,
    val saving: Boolean = false,
    val dialog: EditDialog? = null,
) {
    /** Учитывает и звук: [Alarm.sound] и [Alarm.vibrate] — часть черновика. */
    val isDirty: Boolean get() = draft != initial

    /** Что показать в строке «Мелодия». */
    val soundName: SoundName
        get() = when (val ref = draft.sound.sound) {
            is SoundRef.Builtin -> SoundName.Builtin(ref.sound)

            is SoundRef.Custom -> when (val titles = customTitles) {
                null -> SoundName.Pending
                else -> titles[ref.id]?.let(SoundName::Custom) ?: SoundName.Missing
            }
        }

    /** «Удалить» — только для существующего будильника. */
    val canDelete: Boolean get() = !isNew && !loading
}

/** Название мелодии в строке «Мелодия»; текст — на экране. */
sealed interface SoundName {
    data class Builtin(val sound: BuiltinSound) : SoundName

    data class Custom(val title: String) : SoundName

    /** Своя мелодия удалена из библиотеки (ADR-016 §5): на звонке сработает резерв, экран предлагает другую. */
    data object Missing : SoundName

    /** Библиотека ещё не загружена — значение не показывается, чтобы не мигнуть «Мелодия удалена». */
    data object Pending : SoundName
}

/** Открытый диалог; тексты — на экране. */
sealed interface EditDialog {
    /** Нарастание громкости: Выкл / 15 с / 30 с / 1 мин. */
    data object FadeIn : EditDialog

    data object SnoozeInterval : EditDialog

    data object SnoozeLimit : EditDialog

    data object ConfirmDelete : EditDialog

    /** «Отменить изменения?» — Back с несохранёнными правками. */
    data object ConfirmDiscard : EditDialog
}

sealed interface AlarmEditEvent {
    data class TimeChanged(val time: LocalTime) : AlarmEditEvent

    data class DayToggled(val day: DayOfWeek) : AlarmEditEvent

    data class PresetSelected(val preset: DayPreset) : AlarmEditEvent

    /** Приходит уже очищенной полем ввода; редуктор страхуется от превышения лимита и переводов строки. */
    data class LabelChanged(val label: String) : AlarmEditEvent

    /** `null` — snooze выключен. */
    data class SnoozeIntervalSelected(val interval: Duration?) : AlarmEditEvent

    /** `null` — без ограничения. */
    data class SnoozeLimitSelected(val maxCount: Int?) : AlarmEditEvent

    /** Мелодия выбрана в пикере (приходит параметром Route, ADR-016 §8). */
    data class SoundSelected(val sound: SoundRef) : AlarmEditEvent

    /** Шаг слайдера громкости; редуктор приводит к 10..100 с шагом 10, ViewModel запускает превью. */
    data class VolumeChanged(val percent: Int) : AlarmEditEvent

    /** Одно из `SoundSettings.FADE_IN_OPTIONS`; другое значение игнорируется. */
    data class FadeInSelected(val fadeIn: Duration) : AlarmEditEvent

    data class VibrateChanged(val vibrate: Boolean) : AlarmEditEvent

    /** Тап по строке «Мелодия»: ViewModel глушит превью и просит открыть пикер ([AlarmEditEffect.OpenSoundPicker]). */
    data object PickSound : AlarmEditEvent

    data object ShowFadeInDialog : AlarmEditEvent

    /** Экран ушёл с переднего плана (`ON_STOP`): превью замолкает. */
    data object StopSoundPreview : AlarmEditEvent

    data object ShowSnoozeIntervalDialog : AlarmEditEvent

    data object ShowSnoozeLimitDialog : AlarmEditEvent

    data object Save : AlarmEditEvent

    data object Test : AlarmEditEvent

    /** Нажата «Удалить»: откроется подтверждение. */
    data object Delete : AlarmEditEvent

    data object ConfirmDelete : AlarmEditEvent

    /** Системный/экранный Back: без правок — закрыть, с правками — спросить. */
    data object Back : AlarmEditEvent

    data object DiscardConfirmed : AlarmEditEvent

    data object DialogDismissed : AlarmEditEvent
}

/** Одноразовые сообщения экрану; тексты (тосты) — на экране. */
sealed interface AlarmEditEffect {
    /**
     * Сохранено, экран закрывается. [until] — «Будильник зазвонит через …» (FR-EDIT-11); `null`, если звонить
     * нечему или система отказала ([ScheduleResult.scheduled] `false`) — тогда «Не удалось запланировать».
     * После `Saved` и `Close` ViewModel события больше не обрабатывает.
     */
    data class Saved(val result: ScheduleResult, val until: TimeUntil?) : AlarmEditEffect

    /** Тестовый звонок поставлен на [at]; `null` — система отказала (нет права на точные будильники). */
    data class TestScheduled(val at: Instant?) : AlarmEditEffect

    data object SaveFailed : AlarmEditEffect

    data object DeleteFailed : AlarmEditEffect

    /** Загрузка будильника упала (ошибка БД): экран сообщает и закрывается. */
    data object LoadFailed : AlarmEditEffect

    data object Close : AlarmEditEffect

    /** Открыть пикер мелодий с текущим выбором [current]; результат вернётся параметром Route. */
    data class OpenSoundPicker(val current: SoundRef) : AlarmEditEffect
}
