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

    /** Строка «Миссия» (за флагами миссий) и подэкран её настройки. */
    const val MISSION_SECTION = "alarmEditMissionSection"
    const val MISSION = "alarmEditMission"
    const val MISSION_SETTINGS = "alarmEditMissionSettings"
    const val MISSION_NONE = "alarmEditMissionNone"
    const val MISSION_CONFIG = "alarmEditMissionConfig"
    const val MISSION_PREVIEW = "alarmEditMissionPreview"
    const val MISSION_DONE = "alarmEditMissionDone"
    const val SAVE = "alarmEditSave"
    const val TEST = "alarmEditTest"
    const val DELETE = "alarmEditDelete"

    /** Подложка под статус-баром (`SystemBarScrim`). */
    const val TOP_SCRIM = "alarmEditTopScrim"

    /** Вариант миссии в списке подэкрана; [type] — `MissionTypeId.value`. */
    fun missionOption(type: String): String = "alarmEditMissionOption_$type"
}
