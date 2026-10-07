package com.antbtv.balarm.feature.alarmlist

import com.antbtv.balarm.core.model.AlarmId

/**
 * Теги экрана списка. Внутри карточки — теги дизайн-системы (`AlarmCardTestTags.CARD/SWITCH`), искать их
 * под [card] конкретного будильника.
 */
object AlarmListTestTags {
    const val ROOT = "alarmListRoot"
    const val LIST = "alarmListList"
    const val FAB = "alarmListFab"
    const val EMPTY = "alarmListEmpty"
    const val MENU_DELETE = "alarmListMenuDelete"
    const val HEALTH_BANNER = "alarmListHealthBanner"

    /** Подложки под статус-баром и навигационной панелью (`SystemBarScrim`). */
    const val TOP_SCRIM = "alarmListTopScrim"
    const val BOTTOM_SCRIM = "alarmListBottomScrim"

    /** Обёртка карточки: `alarmListCard_42`. */
    fun card(id: AlarmId): String = "alarmListCard_${id.value}"
}
