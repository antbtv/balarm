package com.antbtv.balarm.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Настройки будильника (ADR-004, схема v1). Пишет редактор, движок читает. */
@Entity(tableName = "alarm")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Битмаска ISO-дней: Пн = 1 shl 0 … Вс = 1 shl 6; 0 — разовый. */
    @ColumnInfo(name = "repeat_days") val repeatDays: Int,
    @ColumnInfo(defaultValue = "") val label: String,
    val enabled: Boolean,
    @ColumnInfo(defaultValue = "1") val vibrate: Boolean,
    /** 0 — отложить нельзя. */
    @ColumnInfo(name = "snooze_interval_min", defaultValue = "5") val snoozeIntervalMin: Int,
    /** -1 — без ограничения. */
    @ColumnInfo(name = "snooze_limit", defaultValue = "3") val snoozeLimit: Int,
    /** `SoundRef.encode()` (схема v3, ADR-016 §6). */
    @ColumnInfo(defaultValue = "'builtin:alarm_default'") val sound: String = DEFAULT_SOUND,
    @ColumnInfo(name = "volume_percent", defaultValue = "80") val volumePercent: Int = DEFAULT_VOLUME_PERCENT,
    @ColumnInfo(name = "fade_in_sec", defaultValue = "0") val fadeInSec: Int = 0,
) {
    companion object {
        const val DEFAULT_SOUND = "builtin:alarm_default"
        const val DEFAULT_VOLUME_PERCENT = 80
    }
}

/** Своя мелодия; файл — `DE files/sounds/<id>` (схема v3, ADR-016 §3, §6). */
@Entity(tableName = "custom_sound")
data class CustomSoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** Служебное состояние, переживающее перезагрузку (ADR-004, ADR-006 §5). Пишет только движок. */
@Entity(
    tableName = "alarm_runtime",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarm_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AlarmRuntimeEntity(
    @PrimaryKey @ColumnInfo(name = "alarm_id") val alarmId: Long,
    /** Epoch ms — ровно то, что отдано `setAlarmClock`. */
    @ColumnInfo(name = "next_trigger_at") val nextTriggerAt: Long?,
    @ColumnInfo(name = "next_trigger_kind", defaultValue = "REGULAR") val nextTriggerKind: String,
    @ColumnInfo(name = "snooze_count", defaultValue = "0") val snoozeCount: Int,
    @ColumnInfo(name = "last_fired_at") val lastFiredAt: Long?,
    /** Система отказала в `setAlarmClock` (схема v2, ADR-015). */
    @ColumnInfo(name = "schedule_failed", defaultValue = "0") val scheduleFailed: Boolean = false,
)
