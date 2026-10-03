package com.antbtv.balarm.core.alarm

import android.content.Intent

/**
 * Интенты экранов приложения для уведомлений и `AlarmClockInfo` (ADR-007). Реализация — в `:app`,
 * чтобы `:core:alarm` не зависел от feature-модулей.
 */
interface AlarmUiIntents {
    fun ringingScreen(): Intent

    fun alarmList(): Intent
}
