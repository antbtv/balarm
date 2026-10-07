package com.antbtv.balarm.core.designsystem.component

import androidx.annotation.DrawableRes
import com.antbtv.balarm.core.designsystem.R

/**
 * Векторные иконки дизайн-системы (24dp, авторские штриховые рисунки, лицензия — `docs/LICENSES.md`).
 * Использовать через `Icon(painterResource(BalarmIcons.Add), …)`: цвет берётся из `LocalContentColor`.
 * `material-icons-extended` не подключаем — он тяжёлый.
 */
object BalarmIcons {
    @DrawableRes
    val Add: Int = R.drawable.ic_add

    @DrawableRes
    val Delete: Int = R.drawable.ic_delete

    /** Зеркалится в RTL (`autoMirrored`). */
    @DrawableRes
    val ChevronRight: Int = R.drawable.ic_chevron_right

    @DrawableRes
    val Keyboard: Int = R.drawable.ic_keyboard

    /** Будильник: вкладка «Будильники» нижней панели. */
    @DrawableRes
    val Alarm: Int = R.drawable.ic_alarm

    /** Шестерёнка: вкладка «Настройки» нижней панели. */
    @DrawableRes
    val Settings: Int = R.drawable.ic_settings

    /** Статус «в порядке» — галочка в круге. */
    @DrawableRes
    val StatusOk: Int = R.drawable.ic_status_ok

    /** Статус «требует внимания» — восклицательный знак в треугольнике (и иконка `HealthBanner`). */
    @DrawableRes
    val StatusWarning: Int = R.drawable.ic_status_warning

    /** Статус «не подтверждено» (не проверяется программно) — вопросительный знак в круге. */
    @DrawableRes
    val StatusUnconfirmed: Int = R.drawable.ic_status_unconfirmed
}
