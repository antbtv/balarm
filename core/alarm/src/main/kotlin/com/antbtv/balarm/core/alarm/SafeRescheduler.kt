package com.antbtv.balarm.core.alarm

import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * `rescheduleAll` для системных событий и запуска приложения: ошибка целиком (не открылась БД)
 * логируется и не роняет процесс — следующее событие или запуск попробуют снова.
 */
class SafeRescheduler @Inject constructor(private val engine: AlarmEngine, private val log: AlarmEventLog) {

    @Suppress("TooGenericExceptionCaught") // страховочный вызов не должен ронять приложение-будильник
    suspend fun reschedule(reason: RescheduleReason) {
        try {
            engine.rescheduleAll(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Сбой самого лога тоже не должен уронить процесс.
            runCatching { log.log(AlarmEvent.RescheduleAllFailed(reason, e.javaClass.simpleName)) }
        }
    }
}
