package com.antbtv.balarm.feature.settings

import androidx.compose.runtime.Immutable
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthStatus
import java.time.Instant

@Immutable
data class HealthItemUi(val item: HealthItem, val status: HealthStatus)

/** Экран «Здоровье будильника» (FR-REL-7). */
@Immutable
data class HealthUiState(
    val loading: Boolean = true,
    val items: List<HealthItemUi> = emptyList(),
    val unscheduledAlarms: Int = 0,
    /** Идёт «Повторить планирование»: кнопка заблокирована. */
    val retrying: Boolean = false,
)

sealed interface HealthEvent {
    /** `ON_RESUME`: перечитать платформенные статусы. */
    data object Resumed : HealthEvent

    /** «Исправить» у пункта; для планирования — повтор, для остальных — эффект [HealthEffect.OpenFix]. */
    data class Fix(val item: HealthItem) : HealthEvent

    data object RetryScheduling : HealthEvent

    /** «Тестовый будильник через 1 минуту». */
    data object ScheduleTest : HealthEvent

    data class SetOemConfirmed(val confirmed: Boolean) : HealthEvent
}

sealed interface HealthEffect {
    /** Экран открывает системные настройки через `rememberHealthFixLauncher`. */
    data class OpenFix(val item: HealthItem) : HealthEffect

    /** [at] — момент звонка, `null` — система отказала. */
    data class TestScheduled(val at: Instant?) : HealthEffect

    data object RetryFailed : HealthEffect

    data object SaveFailed : HealthEffect
}

/** Строка «Настройки → Здоровье будильника» со сводкой (ADR-014 §5). */
@Immutable
data class SettingsUiState(
    val loading: Boolean = true,
    /** Пункты в состоянии проблемы; 0 — «Всё в порядке». */
    val problems: Int = 0,
)
