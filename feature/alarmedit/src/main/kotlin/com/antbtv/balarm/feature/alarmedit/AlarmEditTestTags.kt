package com.antbtv.balarm.feature.alarmedit

/**
 * Теги экрана редактора. Внутри — теги дизайн-системы (`TimeWheelPickerTestTags`, `DayChipsTestTags`,
 * `PresetChipsTestTags`, `LabelFieldTestTags`, `SingleChoiceDialogTestTags`, `ConfirmDialogTestTags`).
 */
object AlarmEditTestTags {
    const val ROOT = "alarmEditRoot"
    const val TITLE = "alarmEditTitle"

    /** Прокручиваемая часть экрана (всё, кроме закреплённой «Сохранить»). */
    const val CONTENT = "alarmEditContent"
    const val SNOOZE_INTERVAL = "alarmEditSnoozeInterval"
    const val SNOOZE_LIMIT = "alarmEditSnoozeLimit"

    /** Секция «Звук» (за `feature.alarmSound`). */
    const val SOUND_SECTION = "alarmEditSoundSection"
    const val SOUND = "alarmEditSound"
    const val SOUND_MISSING_HINT = "alarmEditSoundMissingHint"

    /** Строка громкости; сам ползунок внутри — `SliderRowTestTags.SLIDER`. */
    const val VOLUME = "alarmEditVolume"
    const val FADE_IN = "alarmEditFadeIn"
    const val VIBRATE = "alarmEditVibrate"
    const val SAVE = "alarmEditSave"
    const val TEST = "alarmEditTest"
    const val DELETE = "alarmEditDelete"

    /** Подложка под статус-баром (`SystemBarScrim`). */
    const val TOP_SCRIM = "alarmEditTopScrim"
}
