package com.antbtv.balarm.core.alarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.antbtv.balarm.core.alarm.ring.RingingService
import com.antbtv.balarm.core.domain.alarm.FireKind
import com.antbtv.balarm.core.domain.alarm.ScheduleRequest
import com.antbtv.balarm.core.model.AlarmId
import java.time.Instant

/**
 * Интенты срабатывания (ADR-006 §2). Идентичность `PendingIntent` задаёт data URI `balarm://alarm/<id>`:
 * один будильник — один интент при любом `Long` id, без коллизий `requestCode`.
 */
internal object AlarmIntents {
    const val ACTION_FIRE = "com.antbtv.balarm.action.FIRE"
    const val ACTION_RING = "com.antbtv.balarm.action.RING"
    const val ACTION_DISMISS = "com.antbtv.balarm.action.DISMISS"
    const val ACTION_SNOOZE = "com.antbtv.balarm.action.SNOOZE"
    const val EXTRA_SCHEDULED_FOR = "scheduled_for"
    const val EXTRA_KIND = "kind"

    private const val SCHEME = "balarm"
    private const val HOST = "alarm"

    fun uri(id: AlarmId): Uri = Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(id.value.toString()).build()

    fun fire(context: Context, id: AlarmId): Intent = Intent(ACTION_FIRE, uri(id), context, AlarmReceiver::class.java)

    fun fire(context: Context, request: ScheduleRequest): Intent = fire(context, request.alarmId)
        .putExtra(EXTRA_SCHEDULED_FOR, request.triggerAt.toEpochMilli())
        .putExtra(EXTRA_KIND, request.kind.name)

    /** Тот же запрос — сервису звонка (`AlarmReceiver` → `RingingService`). */
    fun ring(context: Context, request: ScheduleRequest): Intent =
        Intent(ACTION_RING, uri(request.alarmId), context, RingingService::class.java)
            .putExtra(EXTRA_SCHEDULED_FOR, request.triggerAt.toEpochMilli())
            .putExtra(EXTRA_KIND, request.kind.name)

    /** Команда текущему звонку; id в data отсекает команды из устаревшего уведомления. */
    fun command(context: Context, action: String, id: AlarmId): Intent =
        Intent(action, uri(id), context, RingingService::class.java)

    fun alarmId(intent: Intent): AlarmId? {
        val data = intent.data?.takeIf { it.scheme == SCHEME } ?: return null
        return data.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 }?.let(::AlarmId)
    }

    /** Разбор интента срабатывания; `null` — чужой или повреждённый интент. */
    fun parse(intent: Intent): ScheduleRequest? {
        val id = alarmId(intent) ?: return null
        val kind = FireKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: FireKind.REGULAR
        val scheduledFor = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_SCHEDULED_FOR, System.currentTimeMillis()))
        return ScheduleRequest(id, scheduledFor, kind)
    }
}
