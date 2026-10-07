package com.antbtv.balarm.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.antbtv.balarm.core.designsystem.R

/**
 * Состояние пункта для [HealthStatusRow]. Собственный UI-enum дизайн-системы: `:core:designsystem` не зависит
 * от `:core:domain`, отображение `HealthStatus → HealthStatusUi` делает feature-модуль.
 */
enum class HealthStatusUi(@param:DrawableRes internal val icon: Int, @param:StringRes internal val label: Int) {
    /** ✅ В порядке. */
    Ok(BalarmIcons.StatusOk, R.string.designsystem_health_status_ok),

    /** ⚠️ Проблема: будильник может не сработать или сработать без экрана. */
    Problem(BalarmIcons.StatusWarning, R.string.designsystem_health_status_problem),

    /** Не проверяется программно (OEM-автозапуск) и пользователь ещё не подтвердил. */
    Unconfirmed(BalarmIcons.StatusUnconfirmed, R.string.designsystem_health_status_unconfirmed),
}
