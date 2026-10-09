package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.AlarmId
import java.time.Instant

/** Почему перепланируются все будильники (ADR-006 §7). */
enum class RescheduleReason {
    LOCKED_BOOT,
    BOOT,
    TIME_SET,
    TIMEZONE_CHANGED,
    PACKAGE_REPLACED,
    LOCALE_CHANGED,
    APP_LAUNCH,

    /** «Повторить планирование» на экране здоровья (ADR-015). */
    USER_RETRY,
    DEBUG,
}

/**
 * События движка для лога `Balarm` (формат `EVENT key=value`, без пользовательских данных —
 * метки будильников не логируются). По ним работают сценарии скилла verify-alarm-reliability.
 */
sealed interface AlarmEvent {
    val name: String
    val fields: Map<String, Any?>

    fun toLogLine(): String = buildString {
        append(name)
        fields.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
    }

    data class Scheduled(val id: AlarmId, val at: Instant, val kind: FireKind) : AlarmEvent {
        override val name = "SCHEDULED"
        override val fields = mapOf("id" to id.value, "at" to at, "kind" to kind)
    }

    data class ScheduleFailed(val id: AlarmId, val at: Instant) : AlarmEvent {
        override val name = "SCHEDULE_FAILED"
        override val fields = mapOf("id" to id.value, "at" to at)
    }

    /** Тестовый звонок запланирован ([AlarmId.TEST], ADR-010); без метки и других данных пользователя. */
    data class TestScheduled(val at: Instant) : AlarmEvent {
        override val name = "TEST_SCHEDULED"
        override val fields = mapOf("at" to at)
    }

    data class Cancelled(val id: AlarmId) : AlarmEvent {
        override val name = "CANCELLED"
        override val fields = mapOf("id" to id.value)
    }

    data class CatchUp(val id: AlarmId, val missedAt: Instant) : AlarmEvent {
        override val name = "CATCH_UP"
        override val fields = mapOf("id" to id.value, "missed_at" to missedAt)
    }

    data class Fired(val id: AlarmId, val kind: FireKind, val lateMs: Long) : AlarmEvent {
        override val name = "ALARM_FIRED"
        override val fields = mapOf("id" to id.value, "kind" to kind, "late_ms" to lateMs)
    }

    data class FireSkipped(val id: AlarmId, val reason: SkipReason) : AlarmEvent {
        override val name = "FIRE_SKIPPED"
        override val fields = mapOf("id" to id.value, "reason" to reason)
    }

    /** Движок не смог прочитать состояние — звонок всё равно идёт с настройками по умолчанию. */
    data class FireDegraded(val id: AlarmId, val error: String) : AlarmEvent {
        override val name = "FIRE_DEGRADED"
        override val fields = mapOf("id" to id.value, "error" to error)
    }

    data class Snoozed(val id: AlarmId, val until: Instant, val count: Int) : AlarmEvent {
        override val name = "SNOOZED"
        override val fields = mapOf("id" to id.value, "until" to until, "count" to count)
    }

    data class Dismissed(val id: AlarmId, val reason: DismissReason) : AlarmEvent {
        override val name = "DISMISSED"
        override val fields = mapOf("id" to id.value, "reason" to reason)
    }

    /**
     * Звук звонка пошёл ([source] — `raw` или `tone`). [volume] — громкость `STREAM_ALARM` в виде `текущая/макс`:
     * при `0/…` будильник молчит при «успешном» старте (громкость — M4, FR-SND-7).
     */
    data class SoundStarted(val source: String, val volume: String) : AlarmEvent {
        override val name = "SOUND_STARTED"
        override val fields = mapOf("source" to source, "volume" to volume)
    }

    /** Громкость `STREAM_ALARM` для звонка (FR-SND-7): [original] — до звонка, [applied] — теперь, [max] — предел. */
    data class VolumeApplied(val original: Int, val applied: Int, val max: Int) : AlarmEvent {
        override val name = "VOLUME_APPLIED"
        override val fields = mapOf("original" to original, "applied" to applied, "max" to max)
    }

    /** Система не применила громкость (Android 17 hardening, ADR-017 §2); звонок идёт на текущей. */
    data class VolumeNotApplied(val requested: Int, val actual: Int) : AlarmEvent {
        override val name = "VOLUME_NOT_APPLIED"
        override val fields = mapOf("requested" to requested, "actual" to actual)
    }

    /** Громкость потока фиксирована (`isVolumeFixed`) — не трогаем. */
    data object VolumeFixed : AlarmEvent {
        override val name = "VOLUME_FIXED"
        override val fields = emptyMap<String, Any?>()
    }

    /** Исходная громкость возвращена после звонка (или после падения процесса). */
    data class VolumeRestored(val to: Int) : AlarmEvent {
        override val name = "VOLUME_RESTORED"
        override val fields = mapOf("to" to to)
    }

    /** Превью мелодии не заиграло (редактор/пикер); на звонок не влияет. */
    data class PreviewFailed(val reason: String) : AlarmEvent {
        override val name = "PREVIEW_FAILED"
        override val fields = mapOf("reason" to reason)
    }

    /** Вибрация не включилась; звук при этом продолжается. */
    data class VibrationFailed(val error: String) : AlarmEvent {
        override val name = "VIBRATION_FAILED"
        override val fields = mapOf("error" to error)
    }

    /** Основной звук не заиграл ([reason] — `timeout`/`error`/исключение) → резервный тон (FR-SND-5). */
    data class SoundFallback(val reason: String) : AlarmEvent {
        override val name = "SOUND_FALLBACK"
        override val fields = mapOf("reason" to reason)
    }

    /** Звонок начался: звук, экран, уведомление (критерий M1-T11 — ≤ 2 с после ALARM_FIRED). */
    data class RingingStarted(val id: AlarmId, val degraded: Boolean) : AlarmEvent {
        override val name = "RINGING_STARTED"
        override val fields = mapOf("id" to id.value, "degraded" to degraded)
    }

    /** Звонок окончен ([reason] — `dismiss`, `snooze`, `skip`, `destroyed`). */
    data class RingingStopped(val id: AlarmId, val reason: String) : AlarmEvent {
        override val name = "RINGING_STOPPED"
        override val fields = mapOf("id" to id.value, "reason" to reason)
    }

    /** Команда сессии звонка не выполнилась; звонок продолжается ([command] — `ring`/`dismiss`/`snooze`/`notify`). */
    data class RingingCommandFailed(val command: String, val error: String) : AlarmEvent {
        override val name = "RINGING_COMMAND_FAILED"
        override val fields = mapOf("command" to command, "error" to error)
    }

    /** Процесс падает посреди звонка — звонок перепланирован как RESUME через ~3 с (NFR-5, ADR-007 §7). */
    data class CrashRearmed(val id: AlarmId, val at: Instant) : AlarmEvent {
        override val name = "CRASH_REARMED"
        override val fields = mapOf("id" to id.value, "at" to at)
    }

    /** Срабатывание ждёт окончания текущего звонка (FR-RING-7). */
    data class RingingQueued(val id: AlarmId) : AlarmEvent {
        override val name = "RINGING_QUEUED"
        override val fields = mapOf("id" to id.value)
    }

    /** Foreground service не стартовал — звонит fallback-уведомление (ADR-002 §6). */
    data class ForegroundStartFailed(val id: AlarmId, val error: String) : AlarmEvent {
        override val name = "FGS_START_FAILED"
        override val fields = mapOf("id" to id.value, "error" to error)
    }

    /** Будильник не удалось перепланировать; остальные планируются дальше. */
    data class RescheduleError(val id: AlarmId, val error: String) : AlarmEvent {
        override val name = "RESCHEDULE_ERROR"
        override val fields = mapOf("id" to id.value, "error" to error)
    }

    data class RescheduledAll(val reason: RescheduleReason, val count: Int) : AlarmEvent {
        override val name = "RESCHEDULE_ALL"
        override val fields = mapOf("reason" to reason, "count" to count)
    }

    /** Перепланирование целиком не выполнено (например, не открылась БД); повтор — при следующем событии. */
    data class RescheduleAllFailed(val reason: RescheduleReason, val error: String) : AlarmEvent {
        override val name = "RESCHEDULE_ALL_FAILED"
        override val fields = mapOf("reason" to reason, "error" to error)
    }
}

fun interface AlarmEventLog {
    fun log(event: AlarmEvent)
}
