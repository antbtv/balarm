package com.antbtv.balarm.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.antbtv.balarm.core.domain.alarm.AlarmEvent
import com.antbtv.balarm.core.domain.alarm.AlarmEventLog
import com.antbtv.balarm.core.domain.alarm.RescheduleReason
import com.antbtv.balarm.core.domain.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Перепланирование после перезагрузки (в т.ч. до разблокировки), смены времени/зоны/языка и обновления
 * приложения (FR-REL-2, FR-REL-3). Только планирует — звонок никогда не стартует (ADR-006 §7).
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var rescheduler: SafeRescheduler

    @Inject lateinit var log: AlarmEventLog

    @Inject @field:ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val reason = reasonOf(intent.action) ?: return
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        // Операции движка не отменяются (NonCancellable), поэтому не withTimeout, а сторож: broadcast
        // отпускается вовремя (иначе ANR на загрузке), а перепланирование доделывается в общем скоупе.
        val watchdog = scope.launch {
            delay(broadcastBudget.toMillis())
            if (!finished.get()) {
                runCatching { log.log(AlarmEvent.RescheduleAllFailed(reason, "BroadcastTimeout")) }
                finish()
            }
        }
        scope.launch {
            try {
                rescheduler.reschedule(reason)
            } finally {
                watchdog.cancel()
                finish()
            }
        }
    }

    internal companion object {
        /** Запас до лимита `goAsync` (~10 с для системных broadcast). */
        @VisibleForTesting
        var broadcastBudget: Duration = Duration.ofSeconds(8)

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
