package com.antbtv.balarm.core.alarm.notification

import android.app.PendingIntent

/** Команды звонка из уведомления; интенты строит `RingingService` (M1-T11). */
data class RingingActions(
    /** `null` — кнопки нет: с M5 будильник с миссией отключается только через миссию (ADR-007 §2.1). */
    val dismiss: PendingIntent?,
    /** `null` — отложить нельзя (флаг `feature.snooze` выключен или лимит исчерпан). */
    val snooze: PendingIntent?,
)
