package com.antbtv.balarm.core.designsystem.component

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek

/**
 * Один день в [DayChipsRow]. День — `java.time.DayOfWeek` (тип JDK, не `:core:model`): тем же типом
 * хранит дни повтора модель, вызывающему не нужен маппинг.
 *
 * @property day день недели — ключ чипа и значение колбэка `onToggle`.
 * @property label короткая подпись («Пн», «M»).
 * @property description полное название дня для TalkBack («понедельник»).
 * @property selected будильник звонит в этот день.
 */
@Immutable
data class DayChipUi(val day: DayOfWeek, val label: String, val description: String, val selected: Boolean)
