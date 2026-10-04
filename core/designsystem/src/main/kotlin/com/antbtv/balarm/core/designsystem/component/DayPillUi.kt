package com.antbtv.balarm.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * Один день в [DayPillsRow].
 *
 * @property label короткая подпись («Пн», «M»).
 * @property selected будильник звонит в этот день.
 * @property description полное название дня для TalkBack («понедельник»).
 */
@Immutable
data class DayPillUi(val label: String, val selected: Boolean, val description: String)
