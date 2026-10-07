package com.antbtv.balarm.core.permissions

import androidx.annotation.StringRes
import com.antbtv.balarm.core.domain.health.HealthItem

/** Заголовок пункта — общий для онбординга и экрана здоровья. */
val HealthItem.titleRes: Int
    @StringRes get() = when (this) {
        HealthItem.NOTIFICATIONS -> R.string.health_notifications_title
        HealthItem.EXACT_ALARMS -> R.string.health_exact_alarms_title
        HealthItem.FULL_SCREEN_INTENT -> R.string.health_full_screen_title
        HealthItem.OVERLAY -> R.string.health_overlay_title
        HealthItem.BATTERY_OPTIMIZATION -> R.string.health_battery_title
        HealthItem.BACKGROUND_RESTRICTION -> R.string.health_background_title
        HealthItem.OEM_BACKGROUND -> R.string.health_oem_title
        HealthItem.DO_NOT_DISTURB -> R.string.health_dnd_title
        HealthItem.ALARM_VOLUME -> R.string.health_volume_title
        HealthItem.SCHEDULING -> R.string.health_scheduling_title
    }

/** «Зачем это нужно» (PRD §3.7). */
val HealthItem.whyRes: Int
    @StringRes get() = when (this) {
        HealthItem.NOTIFICATIONS -> R.string.health_notifications_why
        HealthItem.EXACT_ALARMS -> R.string.health_exact_alarms_why
        HealthItem.FULL_SCREEN_INTENT -> R.string.health_full_screen_why
        HealthItem.OVERLAY -> R.string.health_overlay_why
        HealthItem.BATTERY_OPTIMIZATION -> R.string.health_battery_why
        HealthItem.BACKGROUND_RESTRICTION -> R.string.health_background_why
        HealthItem.OEM_BACKGROUND -> R.string.health_oem_why
        HealthItem.DO_NOT_DISTURB -> R.string.health_dnd_why
        HealthItem.ALARM_VOLUME -> R.string.health_volume_why
        HealthItem.SCHEDULING -> R.string.health_scheduling_why
    }
