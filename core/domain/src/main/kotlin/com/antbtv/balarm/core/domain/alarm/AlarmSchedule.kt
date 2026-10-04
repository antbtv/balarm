package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.TriggerKind
import java.time.Instant

/**
 * Что список и шапка показывают о будильнике (ADR-011 §2). Источник — `runtime.nextTriggerAt`, то есть ровно то,
 * что отдано `setAlarmClock`, а не пересчёт в UI: так учитываются snooze и догон, а после смены зоны/времени
 * данные обновляет `rescheduleAll`.
 *
 * Ближайшее срабатывание (обычное, snooze или догон) строго после [now] или `null`. Момент в прошлом
 * (пропуск, который ещё не перепланирован) и выключенный будильник без ожидающего snooze — `null`.
 *
 * Если система отказала в точном будильнике (`ScheduleResult.scheduled = false`), runtime всё равно хранит момент
 * «для повтора», и функция его вернёт, хотя в `AlarmManager` ничего нет. На minSdk 34 `USE_EXACT_ALARM` выдаётся
 * при установке и не отзывается, поэтому в списке это не проверяется; статус — на экране здоровья (M3).
 */
fun AlarmWithRuntime.upcomingTrigger(now: Instant): Instant? {
    val state = runtime ?: return null
    val next = state.nextTriggerAt?.takeIf { it.isAfter(now) } ?: return null
    return next.takeIf { alarm.enabled || state.nextTriggerKind != TriggerKind.REGULAR }
}

/**
 * Активен = включён **или** есть ожидающий snooze/догон: разовый будильник после срабатывания выключен
 * (`enabled = false`), но его snooze впереди — тумблер должен честно показывать «вкл».
 */
fun AlarmWithRuntime.isActive(now: Instant): Boolean = alarm.enabled || upcomingTrigger(now) != null

/** Шапка списка (FR-LIST-2): самое раннее из ближайших срабатываний; `null` — «Нет активных будильников». */
fun List<AlarmWithRuntime>.nextTrigger(now: Instant): Instant? = mapNotNull { it.upcomingTrigger(now) }.minOrNull()
