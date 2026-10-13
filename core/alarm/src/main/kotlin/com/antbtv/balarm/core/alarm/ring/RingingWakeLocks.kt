package com.antbtv.balarm.core.alarm.ring

import android.content.Context
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import com.antbtv.balarm.core.domain.alarm.RingingPolicy
import java.time.Duration

/**
 * WakeLock-и цепочки звонка (ADR-007 §1–2). Доставочный — статический: держит CPU от `AlarmReceiver`
 * до старта сервиса; сессионный — на весь звонок. Оба не считают ссылки: повторный acquire продлевает.
 */
internal object RingingWakeLocks {
    private val DELIVERY_TIMEOUT: Duration = Duration.ofSeconds(60)

    /** Автостоп по умолчанию + запас: если сессия зависнет, CPU всё равно отпустим. */
    val SESSION_TIMEOUT: Duration = RingingPolicy.AUTO_STOP_AFTER.plusMinutes(1)

    /** Автостоп «никогда»: WakeLock с таймаутом без автостопа не берём, а продлеваем (ADR-021 §5). */
    val UNLIMITED_TIMEOUT: Duration = Duration.ofMinutes(30)
    val UNLIMITED_RENEW_EVERY: Duration = Duration.ofMinutes(25)

    fun sessionTimeout(remaining: Duration): Duration = remaining.plusMinutes(1)

    private var delivery: PowerManager.WakeLock? = null

    @Synchronized
    fun acquireDelivery(context: Context) {
        val lock = delivery ?: newLock(context, "Balarm:delivery").also { delivery = it }
        lock.acquire(DELIVERY_TIMEOUT.toMillis())
    }

    @Synchronized
    fun releaseDelivery() {
        delivery?.let { if (it.isHeld) it.release() }
    }

    /** Держится ли доставочный WakeLock (для тестов и диагностики). */
    @Synchronized
    fun isDeliveryHeld(): Boolean = delivery?.isHeld == true

    @VisibleForTesting
    @Synchronized
    fun resetForTest() {
        delivery = null
    }

    fun newSessionLock(context: Context): PowerManager.WakeLock = newLock(context, "Balarm:ringing")

    private fun newLock(context: Context, tag: String): PowerManager.WakeLock =
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            .apply { setReferenceCounted(false) }
}
