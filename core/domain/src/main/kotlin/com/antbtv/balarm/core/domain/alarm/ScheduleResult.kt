package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.AlarmId
import java.time.Instant

/**
 * Итог изменения расписания для UI (ADR-011 §5).
 * [nextTriggerAt] — ближайшее срабатывание, отданное системе (`null` — будильник выключен, ничего не запланировано);
 * [scheduled] `false` — система отказала (нет права на точные будильники): тост «не удалось запланировать».
 * При отказе [nextTriggerAt] — момент, сохранённый для повтора (следующий `rescheduleAll` попробует снова), а не
 * реально запланированный: список и шапка не должны выдавать его за «зазвонит через …» без учёта статуса.
 */
data class ScheduleResult(val id: AlarmId, val nextTriggerAt: Instant?, val scheduled: Boolean)
