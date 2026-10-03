package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.domain.schedule.NextTriggerCalculator
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.AlarmRuntimeState
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.TriggerKind
import com.antbtv.balarm.core.model.feature.Feature
import com.antbtv.balarm.core.model.feature.FeatureFlagProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Единственная точка изменения расписания (ADR-006 §8): любое изменение будильника
 * синхронно отражается в `AlarmManager`.
 *
 * * Все операции сериализованы общим [Mutex] — почти одновременные системные события
 *   (LOCKED_BOOT + BOOT, TIME_SET + TIMEZONE) не перетирают друг друга.
 * * Начатая операция не отменяется ([NonCancellable]): вызовы `AlarmManager` не откатываются вместе
 *   с транзакцией, поэтому отмена посередине развела бы БД и систему.
 * * Звонок важнее консистентности: [onFired] не ждёт движок дольше [FIRE_LOCK_TIMEOUT] и при любой
 *   ошибке всё равно звонит («в сомнении — звони»).
 */
@Suppress("TooGenericExceptionCaught") // ошибки одного будильника не должны ломать остальные и сам звонок
@Singleton
class AlarmEngine @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
    private val flags: FeatureFlagProvider,
    private val log: AlarmEventLog,
) {
    private val mutex = Mutex()

    /** Сохраняет будильник и планирует его заново с чистым runtime (новые настройки → обычное расписание). */
    suspend fun save(alarm: Alarm): AlarmId = locked {
        val id = repository.save(alarm)
        val saved = alarm.copy(id = id)
        applySchedule(saved, regularPlan(saved, AlarmRuntimeState(id)))
        id
    }

    suspend fun setEnabled(id: AlarmId, enabled: Boolean): Unit = locked {
        repository.setEnabled(id, enabled)
        val alarm = repository.get(id) ?: return@locked
        applySchedule(alarm, regularPlan(alarm, AlarmRuntimeState(id)))
    }

    /** Сначала удаление из БД: если оно упадёт, будильник останется и запланированным, и в списке. */
    suspend fun delete(id: AlarmId): Unit = locked {
        repository.delete(id)
        scheduler.cancel(id)
        log.log(AlarmEvent.Cancelled(id))
    }

    /**
     * Перепланирует все будильники после перезагрузки, смены времени/зоны, обновления, запуска UI.
     * Идемпотентно; ошибка одного будильника не мешает остальным. Пропуск ≤ [LATE_GRACE]
     * (телефон перезагружался в момент звонка) догоняется через [CATCH_UP_DELAY].
     * Возвращает число будильников, успешно отданных системе.
     */
    suspend fun rescheduleAll(reason: RescheduleReason): Int = locked {
        val count = repository.loadAll().count { (alarm, runtime) ->
            try {
                applySchedule(alarm, restoredPlan(alarm, runtime)) == Outcome.SCHEDULED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.log(AlarmEvent.RescheduleError(alarm.id, e.javaClass.simpleName))
                false
            }
        }
        log.log(AlarmEvent.RescheduledAll(reason, count))
        count
    }

    /**
     * Будильник сработал. Фиксирует срабатывание и **сразу** планирует следующее (разовый выключается),
     * чтобы падение звонка не потеряло завтрашний будильник. Повторная доставка того же срабатывания —
     * [SkipReason.DUPLICATE]. Занятый движок или ошибка хранилища → звонок с настройками по умолчанию.
     */
    suspend fun onFired(id: AlarmId, scheduledFor: Instant, kind: FireKind): FireDecision = try {
        locked(timeout = FIRE_LOCK_TIMEOUT) { fire(id, scheduledFor, kind) } ?: degraded(id, "EngineBusy")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        degraded(id, e.javaClass.simpleName)
    }

    /**
     * Откладывает текущий звонок. [SnoozeResult.NotAllowed] — фича выключена, snooze запрещён, лимит исчерпан
     * или система не приняла будильник (тогда звонок должен продолжаться). Повторное нажатие, пока snooze
     * уже стоит, лимит не тратит.
     */
    suspend fun snooze(id: AlarmId): SnoozeResult = locked {
        val alarm = repository.get(id) ?: return@locked SnoozeResult.NotAllowed
        val runtime = repository.getRuntime(id) ?: AlarmRuntimeState(id)
        val now = clock.instant()
        val pendingUntil = runtime.nextTriggerAt?.takeIf {
            runtime.nextTriggerKind == TriggerKind.SNOOZE && it.isAfter(now)
        }
        val interval = alarm.snooze.interval
        when {
            pendingUntil != null -> SnoozeResult.Snoozed(pendingUntil)
            interval == null || !canSnooze(alarm, runtime.snoozeCount) -> SnoozeResult.NotAllowed
            else -> scheduleSnooze(alarm, runtime, now + interval)
        }
    }

    /**
     * Звонок окончен (кнопка «Отключить», миссия, автостоп). Счётчик snooze сбрасывается, дальше — обычное
     * расписание; разовый остаётся выключенным. Для «Отложить» вызывается только [snooze], не [dismiss].
     */
    suspend fun dismiss(id: AlarmId, reason: DismissReason): Unit = locked {
        val alarm = repository.get(id) ?: return@locked
        val runtime = (repository.getRuntime(id) ?: AlarmRuntimeState(id)).copy(snoozeCount = 0)
        applySchedule(alarm, regularPlan(alarm, runtime))
        log.log(AlarmEvent.Dismissed(id, reason))
    }

    // region Срабатывание

    private suspend fun fire(id: AlarmId, scheduledFor: Instant, kind: FireKind): FireDecision {
        val alarm = repository.get(id) ?: return skip(id, SkipReason.DELETED)
        val runtime = repository.getRuntime(id) ?: AlarmRuntimeState(id)
        val alreadyFired = runtime.lastFiredAt?.let { !it.isBefore(scheduledFor) } == true
        return when {
            // Перезапуск после падения процесса: состояние уже зафиксировано первым срабатыванием.
            kind == FireKind.RESUME -> ring(alarm, runtime.snoozeCount)

            alreadyFired -> skip(id, SkipReason.DUPLICATE)

            scheduledFor.isBefore(clock.instant() - LATE_GRACE) -> skipStale(alarm, runtime)

            else -> recordFire(alarm, runtime, scheduledFor, kind)
        }
    }

    private suspend fun recordFire(
        stored: Alarm,
        runtime: AlarmRuntimeState,
        scheduledFor: Instant,
        kind: FireKind,
    ): FireDecision.Ring {
        val now = clock.instant()
        val snoozeCount = if (kind == FireKind.SNOOZE) runtime.snoozeCount else 0
        val alarm = if (stored.isOneShot) stored.copy(enabled = false) else stored
        try {
            if (alarm.enabled != stored.enabled) repository.setEnabled(alarm.id, false)
            applySchedule(alarm, regularPlan(alarm, runtime.copy(snoozeCount = snoozeCount, lastFiredAt = now)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Следующее срабатывание перепланирует ближайший rescheduleAll; сейчас главное — звонить.
            log.log(AlarmEvent.RescheduleError(alarm.id, e.javaClass.simpleName))
        }
        log.log(AlarmEvent.Fired(alarm.id, kind, lateMs = Duration.between(scheduledFor, now).toMillis()))
        return ring(alarm, snoozeCount)
    }

    private fun skip(id: AlarmId, reason: SkipReason): FireDecision.Skip {
        if (reason == SkipReason.DELETED) scheduler.cancel(id)
        log.log(AlarmEvent.FireSkipped(id, reason))
        return FireDecision.Skip(reason)
    }

    private suspend fun skipStale(alarm: Alarm, runtime: AlarmRuntimeState): FireDecision.Skip {
        val current = disableIfMissedOneShot(alarm)
        applySchedule(current, regularPlan(current, runtime.copy(snoozeCount = 0)))
        return skip(alarm.id, SkipReason.STALE)
    }

    private fun degraded(id: AlarmId, error: String): FireDecision.Ring {
        log.log(AlarmEvent.FireDegraded(id, error))
        val now = clock.instant().atZone(clock.zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        return FireDecision.Ring(Alarm(id = id, time = now), canSnooze = false, snoozesLeft = 0, degraded = true)
    }

    private fun ring(alarm: Alarm, snoozeCount: Int): FireDecision.Ring = FireDecision.Ring(
        alarm = alarm,
        canSnooze = canSnooze(alarm, snoozeCount),
        snoozesLeft = alarm.snooze.remaining(snoozeCount),
    )

    private fun canSnooze(alarm: Alarm, used: Int): Boolean =
        flags.isEnabled(Feature.SNOOZE) && alarm.snooze.isEnabled && alarm.snooze.remaining(used) != 0

    /** Runtime сохраняется только если система приняла будильник — иначе потеряли бы обычное расписание. */
    private suspend fun scheduleSnooze(alarm: Alarm, runtime: AlarmRuntimeState, until: Instant): SnoozeResult {
        val count = runtime.snoozeCount + 1
        val snoozed = runtime.copy(nextTriggerAt = until, nextTriggerKind = TriggerKind.SNOOZE, snoozeCount = count)
        if (applySchedule(alarm, Plan(snoozed, FireKind.SNOOZE), persistOnFailure = false) != Outcome.SCHEDULED) {
            return SnoozeResult.NotAllowed
        }
        log.log(AlarmEvent.Snoozed(alarm.id, until, count))
        return SnoozeResult.Snoozed(until)
    }

    // endregion

    // region Планирование

    private data class Plan(val runtime: AlarmRuntimeState, val kind: FireKind?)

    private enum class Outcome { SCHEDULED, FAILED, CANCELLED }

    private suspend fun restoredPlan(stored: Alarm, storedRuntime: AlarmRuntimeState?): Plan {
        val runtime = storedRuntime ?: AlarmRuntimeState(stored.id)
        val next = runtime.nextTriggerAt ?: return regularPlan(stored, runtime)
        val now = clock.instant()
        val pending = runtime.nextTriggerKind != TriggerKind.REGULAR
        return when {
            // Отложенный или догоняющий звонок ещё впереди (время могли перевести назад — ограничиваем).
            pending && next.isAfter(
                now,
            ) -> Plan(clampPending(runtime, stored, now), runtime.nextTriggerKind.toFireKind())

            // Звонок должен был случиться недавно, но не случился (перезагрузка, выключение).
            isMissed(runtime, next, now) && (stored.enabled || pending) -> {
                log.log(AlarmEvent.CatchUp(stored.id, next))
                val catchUp = runtime.copy(nextTriggerAt = now + CATCH_UP_DELAY, nextTriggerKind = TriggerKind.CATCH_UP)
                Plan(catchUp, FireKind.CATCH_UP)
            }

            else -> {
                val fired = runtime.lastFiredAt?.let { !it.isBefore(next) } == true
                val alarm = if (next.isAfter(now) || fired) stored else disableIfMissedOneShot(stored)
                regularPlan(alarm, runtime.copy(nextTriggerKind = TriggerKind.REGULAR))
            }
        }
    }

    /** Отложенный звонок не может быть дальше самого длинного snooze — иначе время перевели назад. */
    private fun clampPending(runtime: AlarmRuntimeState, alarm: Alarm, now: Instant): AlarmRuntimeState {
        val next = runtime.nextTriggerAt
        val tooFar = next != null && next.isAfter(now + SnoozeSettings.MAX_INTERVAL)
        return if (tooFar) runtime.copy(nextTriggerAt = now + (alarm.snooze.interval ?: CATCH_UP_DELAY)) else runtime
    }

    /** Разовый будильник, чей момент давно прошёл, выключается, а не переносится на завтра (FR-REL-6). */
    private suspend fun disableIfMissedOneShot(alarm: Alarm): Alarm {
        if (!alarm.isOneShot || !alarm.enabled) return alarm
        repository.setEnabled(alarm.id, false)
        log.log(AlarmEvent.FireSkipped(alarm.id, SkipReason.MISSED))
        return alarm.copy(enabled = false)
    }

    private fun isMissed(runtime: AlarmRuntimeState, next: Instant, now: Instant): Boolean {
        val fired = runtime.lastFiredAt?.let { !it.isBefore(next) } == true
        return !next.isAfter(now) && next.isAfter(now - LATE_GRACE) && !fired
    }

    /**
     * Ближайшее обычное срабатывание. Отсчёт — от последнего звонка, если часы перевели назад:
     * иначе будильник, уже прозвеневший в 07:00, зазвонил бы снова после перевода на 06:00.
     */
    private fun regularPlan(alarm: Alarm, runtime: AlarmRuntimeState): Plan {
        if (!alarm.enabled) return Plan(runtime.copy(nextTriggerAt = null, nextTriggerKind = TriggerKind.REGULAR), null)
        val now = clock.instant()
        val after = runtime.lastFiredAt?.takeIf { it.isAfter(now) } ?: now
        val at = NextTriggerCalculator.next(alarm.time, alarm.repeatDays, after, clock.zone)
        return Plan(runtime.copy(nextTriggerAt = at, nextTriggerKind = TriggerKind.REGULAR), FireKind.REGULAR)
    }

    /**
     * Отдаёт план в `AlarmManager` и сохраняет runtime. При отказе системы момент по умолчанию всё равно
     * сохраняется (следующий rescheduleAll попробует снова); [persistOnFailure] = false — не сохранять.
     */
    private suspend fun applySchedule(alarm: Alarm, plan: Plan, persistOnFailure: Boolean = true): Outcome {
        val at = plan.runtime.nextTriggerAt
        if (plan.kind == null || at == null) {
            scheduler.cancel(alarm.id)
            repository.updateRuntime(plan.runtime)
            return Outcome.CANCELLED
        }
        val accepted = scheduler.schedule(ScheduleRequest(alarm.id, at, plan.kind))
        log.log(
            if (accepted) AlarmEvent.Scheduled(alarm.id, at, plan.kind) else AlarmEvent.ScheduleFailed(alarm.id, at),
        )
        if (accepted || persistOnFailure) repository.updateRuntime(plan.runtime)
        return if (accepted) Outcome.SCHEDULED else Outcome.FAILED
    }

    private fun TriggerKind.toFireKind(): FireKind = when (this) {
        TriggerKind.REGULAR -> FireKind.REGULAR
        TriggerKind.SNOOZE -> FireKind.SNOOZE
        TriggerKind.CATCH_UP -> FireKind.CATCH_UP
    }

    // endregion

    private suspend fun <R> locked(block: suspend () -> R): R = checkNotNull(locked(timeout = null, block))

    /** `null` — не удалось занять движок за [timeout]. Сама операция после захвата не отменяется. */
    private suspend fun <R> locked(timeout: Duration?, block: suspend () -> R): R? {
        val acquired = timeout?.let { withTimeoutOrNull(it.toMillis()) { mutex.lock() } != null }
            ?: run {
                mutex.lock()
                true
            }
        if (!acquired) return null
        return try {
            withContext(NonCancellable) { repository.transaction(block) }
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        /** Насколько «просроченный» звонок ещё догоняется (ADR-006 §7). */
        val LATE_GRACE: Duration = Duration.ofMinutes(10)

        /** Задержка догоняющего звонка — чтобы успела подняться система после загрузки. */
        val CATCH_UP_DELAY: Duration = Duration.ofSeconds(3)

        /** Сколько срабатывание ждёт занятый движок, прежде чем звонить с настройками по умолчанию. */
        val FIRE_LOCK_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}
