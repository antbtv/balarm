package com.antbtv.balarm.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

/**
 * Вкладка [BalarmNavigationBar].
 *
 * @param label подпись («Будильники»); её же читает TalkBack.
 * @param icon векторная иконка (`BalarmIcons.Alarm`, `BalarmIcons.Settings`), декоративная.
 */
@Immutable
data class NavBarItem(val label: String, @param:DrawableRes val icon: Int)
