package com.antbtv.balarm.core.domain.alarm

import com.antbtv.balarm.core.model.Alarm
import java.time.Instant

/**
 * Сообщает пользователю, что будильник не прозвенел и пропущен дольше [AlarmEngine.LATE_GRACE]
 * (телефон был выключен, время перевели вперёд; FR-REL-6). Вызывается из перепланирования:
 * реализация не должна бросать и блокировать надолго. [missedAt] — момент, который должен был прозвенеть;
 * вызывается после сохранения расписания.
 */
fun interface MissedAlarmNotifier {
    fun onMissed(alarm: Alarm, missedAt: Instant)
}
