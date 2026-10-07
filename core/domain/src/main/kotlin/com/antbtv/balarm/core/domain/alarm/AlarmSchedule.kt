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
 * Если система отказала в точном будильнике (`scheduleFailed`, ADR-015), runtime хранит лишь момент «для повтора»,
 * а в `AlarmManager` ничего нет — такой будильник «следующего срабатывания» не имеет (`null`); карточка показывает
 * «Не запланирован», баннер и экран здоровья — [unscheduledCount].
 */
fun AlarmWithRuntime.upcomingTrigger(now: Instant): Instant? {
    val state = runtime?.takeUnless { it.scheduleFailed } ?: return null
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

/** Сколько будильников система отказалась запланировать (пункт SCHEDULING экрана здоровья, ADR-015). */
fun List<AlarmWithRuntime>.unscheduledCount(): Int = count { it.runtime?.scheduleFailed == true }
