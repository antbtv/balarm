package com.antbtv.balarm.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antbtv.balarm.core.domain.alarm.AlarmEngine
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Перепланирование после перезагрузки (в т.ч. до разблокировки), смены времени/зоны/языка и обновления
 * приложения (FR-REL-2, FR-REL-3). Только планирует — звонок никогда не стартует (ADR-006 §7).
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: AlarmEngine

    @Inject lateinit var log: AlarmEventLog

    @Suppress("TooGenericExceptionCaught") // любая ошибка: залогировать и отпустить broadcast, не падать на загрузке
    override fun onReceive(context: Context, intent: Intent) {
        val reason = reasonOf(intent.action) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                engine.rescheduleAll(reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Сбой самого лога не должен уронить процесс на загрузке.
                runCatching { log.log(AlarmEvent.RescheduleAllFailed(reason, e.javaClass.simpleName)) }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        // Живёт вместе с процессом; goAsync держит процесс до finish(). Заменится @ApplicationScope в M1-T14.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun reasonOf(action: String?): RescheduleReason? = when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> RescheduleReason.LOCKED_BOOT
            Intent.ACTION_BOOT_COMPLETED -> RescheduleReason.BOOT
            Intent.ACTION_TIME_CHANGED -> RescheduleReason.TIME_SET
            Intent.ACTION_TIMEZONE_CHANGED -> RescheduleReason.TIMEZONE_CHANGED
            Intent.ACTION_MY_PACKAGE_REPLACED -> RescheduleReason.PACKAGE_REPLACED
            Intent.ACTION_LOCALE_CHANGED -> RescheduleReason.LOCALE_CHANGED
            else -> null
        }
    }
}
