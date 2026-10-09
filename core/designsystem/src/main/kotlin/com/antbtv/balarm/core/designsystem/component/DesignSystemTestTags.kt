package com.antbtv.balarm.core.designsystem.component

import java.time.DayOfWeek

// Test-теги компонентов дизайн-системы: общие для Compose UI-тестов модуля и feature-экранов.

object AlarmCardTestTags {
    const val CARD = "alarmCard"
    const val SWITCH = "alarmCardSwitch"
}

object DayPillsTestTags {
    const val ROW = "dayPillsRow"

    /** Точка-индикатор под подписью дня (у невыбранного — прозрачная, но есть: ячейки одинаковой высоты). */
    const val DOT = "dayPillDot"
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

object HealthBannerTestTags {
    const val BANNER = "healthBanner"
}

/**
 * Теги по умолчанию у [HealthStatusRow]. На экране здоровья строк несколько: вызывающий задаёт свой тег строки
 * через `modifier` (перекрывает [ROW]), а кнопку находит внутри строки (`hasTestTag(ACTION)` + `hasAnyAncestor`).
 */
object HealthStatusRowTestTags {
    const val ROW = "healthStatusRow"

    /** Текстовая часть строки: один узел TalkBack с заголовком, состоянием и описанием. */
    const val INFO = "healthStatusRowInfo"
    const val ACTION = "healthStatusRowAction"
}

object OnboardingStepTestTags {
    const val LAYOUT = "onboardingStep"
    const val PROGRESS = "onboardingStepProgress"
    const val TITLE = "onboardingStepTitle"
    const val PRIMARY = "onboardingStepPrimary"
    const val SECONDARY = "onboardingStepSecondary"
    const val WARNING = "onboardingStepWarning"
}

object NavigationBarTestTags {
    const val BAR = "balarmNavigationBar"

    /** Вкладка по индексу: `navBarItem_0`. */
    fun item(index: Int): String = "navBarItem_$index"
}

object TopBarTestTags {
    const val BAR = "balarmTopBar"
    const val BACK = "balarmTopBarBack"
    const val TITLE = "balarmTopBarTitle"
}

/** Теги по умолчанию у [SwitchRow]; на экране с несколькими строками вызывающий задаёт свой через `modifier`. */
object SwitchRowTestTags {
    const val ROW = "switchRow"
}

/** Теги [SliderRow]: строка целиком и сам ползунок (регулируемый узел TalkBack). */
object SliderRowTestTags {
    const val ROW = "sliderRow"
    const val SLIDER = "sliderRowSlider"
    const val VALUE = "sliderRowValue"
}

object TextInputDialogTestTags {
    const val DIALOG = "textInputDialog"
    const val CONFIRM = "textInputDialogConfirm"
    const val DISMISS = "textInputDialogDismiss"
}

object SnackbarTestTags {
    const val SNACKBAR = "balarmSnackbar"
}
