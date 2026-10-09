package com.antbtv.balarm.feature.settings

import com.antbtv.balarm.core.domain.health.HealthItem

/** Теги вкладки «Настройки». Верхняя строка вложенных экранов — `TopBarTestTags` дизайн-системы. */
object SettingsTestTags {
    const val ROOT = "settingsRoot"
    const val HEALTH_ROW = "settingsHealthRow"
    const val SOUNDS_ROW = "settingsSoundsRow"
    const val ABOUT_ROW = "settingsAboutRow"
}

/**
 * Теги экрана «Здоровье будильника». Внутри строки пункта кнопка действия — `HealthStatusRowTestTags.ACTION`,
 * искать её под [item] конкретного пункта.
 */
object HealthTestTags {
    const val ROOT = "healthRoot"
    const val LIST = "healthList"
    const val OEM_GUIDE = "healthOemGuide"
    const val OEM_CONFIRM = "healthOemConfirm"
    const val TEST_ALARM = "healthTestAlarm"
    const val BOTTOM_SCRIM = "healthBottomScrim"

    /** Карточка пункта: `healthItem_NOTIFICATIONS`. */
    fun item(item: HealthItem): String = "healthItem_${item.name}"
}

object AboutTestTags {
    const val ROOT = "aboutRoot"
    const val VERSION = "aboutVersion"
    const val LICENSES = "aboutLicenses"
}
