package com.antbtv.balarm.core.designsystem.component

import java.time.DayOfWeek

// Test-теги компонентов дизайн-системы: общие для Compose UI-тестов модуля и feature-экранов.

object AlarmCardTestTags {
    const val CARD = "alarmCard"
    const val SWITCH = "alarmCardSwitch"
}

object DayPillsTestTags {
    const val ROW = "dayPillsRow"
}

object NextAlarmHeaderTestTags {
    const val HEADER = "nextAlarmHeader"
}

object TimeWheelPickerTestTags {
    const val PICKER = "timeWheelPicker"
    const val HOURS = "timeWheelHours"
    const val MINUTES = "timeWheelMinutes"
    const val PERIOD = "timeWheelPeriod"
}

object DayChipsTestTags {
    const val ROW = "dayChipsRow"

    /** Чип дня: `dayChip_MONDAY`. */
    fun chip(day: DayOfWeek): String = "dayChip_${day.name}"
}

object PresetChipsTestTags {
    const val ROW = "presetChips"
    const val WEEKDAYS = "presetChipWeekdays"
    const val WEEKEND = "presetChipWeekend"
    const val EVERY_DAY = "presetChipEveryDay"
}

/**
 * Тег по умолчанию у [SettingRow]. На экране с несколькими строками вызывающий задаёт свой через `modifier`:
 * свой тег стоит в цепочке раньше внутреннего и перекрывает его (проверяется в `EditorComponentsTest`).
 */
object SettingRowTestTags {
    const val ROW = "settingRow"
}

object SingleChoiceDialogTestTags {
    const val DIALOG = "singleChoiceDialog"
    const val DISMISS = "singleChoiceDialogDismiss"

    /** Вариант по индексу: `singleChoiceOption_0`. */
    fun option(index: Int): String = "singleChoiceOption_$index"
}

object LabelFieldTestTags {
    const val FIELD = "labelField"
    const val COUNTER = "labelFieldCounter"
}

object ConfirmDialogTestTags {
    const val DIALOG = "confirmDialog"
    const val CONFIRM = "confirmDialogConfirm"
    const val DISMISS = "confirmDialogDismiss"
}
