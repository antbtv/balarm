package com.antbtv.balarm.core.domain.health

import com.antbtv.balarm.core.domain.health.Severity.CRITICAL
import com.antbtv.balarm.core.domain.health.Severity.INFO
import com.antbtv.balarm.core.domain.health.Severity.RECOMMENDED
import kotlinx.coroutines.flow.Flow

/** Насколько пункт важен: [CRITICAL] без него будильник не звонит (или звонит без экрана) — баннер FR-LIST-5. */
enum class Severity { CRITICAL, RECOMMENDED, INFO }

/** Пункты здоровья будильника (ADR-012). Порядок = порядок онбординга и экрана здоровья. */
enum class HealthItem(val severity: Severity) {
    NOTIFICATIONS(CRITICAL),
    EXACT_ALARMS(CRITICAL),
    FULL_SCREEN_INTENT(CRITICAL),
    OVERLAY(RECOMMENDED),
    BATTERY_OPTIMIZATION(RECOMMENDED),
    BACKGROUND_RESTRICTION(CRITICAL),
    OEM_BACKGROUND(RECOMMENDED),
    DO_NOT_DISTURB(INFO),
    ALARM_VOLUME(INFO),
    SCHEDULING(CRITICAL),
}

/** [UNCONFIRMED] — только [HealthItem.OEM_BACKGROUND], пока пользователь не нажал «Я сделал». */
enum class HealthStatus { OK, PROBLEM, UNCONFIRMED }

/** Сырые значения платформы; заполняет `:core:permissions`. */
data class PermissionSnapshot(
    val notificationsEnabled: Boolean,
    val ringingChannelEnabled: Boolean,
    val exactAlarms: Boolean,
    val fullScreenIntent: Boolean,
    val overlay: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val backgroundRestricted: Boolean,
    val alarmsAllowedByDnd: Boolean,
    val alarmVolumeMuted: Boolean,
) {
    /** Разрешение выдано И канал `alarm_ringing` не выключен/не понижен (ADR-007 §8). */
    val notificationsReady: Boolean get() = notificationsEnabled && ringingChannelEnabled
}

data class HealthCheck(val item: HealthItem, val status: HealthStatus)

data class HealthReport(val checks: List<HealthCheck>, val unscheduledAlarms: Int) {
    /** Баннер FR-LIST-5: хотя бы один [Severity.CRITICAL] пункт в [HealthStatus.PROBLEM]. */
    val needsAttention: Boolean
        get() = checks.any { it.item.severity == CRITICAL && it.status == HealthStatus.PROBLEM }

    fun status(item: HealthItem): HealthStatus = checks.first { it.item == item }.status
}

/** Платформенные подтверждения, которые нельзя прочитать у системы (ADR-013). */
data class SetupState(val onboardingCompleted: Boolean = false, val oemBackgroundConfirmed: Boolean = false)

interface SetupStateRepository {
    val state: Flow<SetupState>

    suspend fun completeOnboarding()

    suspend fun setOemBackgroundConfirmed(confirmed: Boolean)
}

/** Синхронно и дёшево (несколько binder-вызовов); вызывается на каждый `ON_RESUME`. */
interface PermissionHealthChecker {
    fun snapshot(): PermissionSnapshot
}

/** Единственное место правил «что считать проблемой». */
fun healthReport(snapshot: PermissionSnapshot, setup: SetupState, unscheduledAlarms: Int): HealthReport = HealthReport(
    checks = HealthItem.entries.map { HealthCheck(it, statusOf(it, snapshot, setup, unscheduledAlarms)) },
    unscheduledAlarms = unscheduledAlarms,
)

private fun statusOf(
    item: HealthItem,
    snapshot: PermissionSnapshot,
    setup: SetupState,
    unscheduledAlarms: Int,
): HealthStatus {
    val ok = when (item) {
        HealthItem.NOTIFICATIONS -> snapshot.notificationsReady
        HealthItem.EXACT_ALARMS -> snapshot.exactAlarms
        HealthItem.FULL_SCREEN_INTENT -> snapshot.fullScreenIntent
        HealthItem.OVERLAY -> snapshot.overlay
        HealthItem.BATTERY_OPTIMIZATION -> snapshot.ignoringBatteryOptimizations
        HealthItem.BACKGROUND_RESTRICTION -> !snapshot.backgroundRestricted
        HealthItem.OEM_BACKGROUND -> setup.oemBackgroundConfirmed
        HealthItem.DO_NOT_DISTURB -> snapshot.alarmsAllowedByDnd
        HealthItem.ALARM_VOLUME -> !snapshot.alarmVolumeMuted
        HealthItem.SCHEDULING -> unscheduledAlarms == 0
    }
    return when {
        ok -> HealthStatus.OK
        item == HealthItem.OEM_BACKGROUND -> HealthStatus.UNCONFIRMED
        else -> HealthStatus.PROBLEM
    }
}
