package com.antbtv.balarm.feature.alarmlist

import androidx.compose.runtime.Immutable
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.model.AlarmId
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Состояние списка (FR-LIST-1, 2). Без строк и локали: время, дни и «через …» форматирует экран через `:core:format`
 * (12/24 ч и язык могут измениться, не трогая ViewModel).
 *
 * @property loading первая выборка из БД ещё не пришла: экран не показывает «пусто» раньше времени.
 * @property nextIn время до ближайшего срабатывания любого будильника; `null` — «Нет активных будильников».
 */
@Immutable
data class AlarmListUiState(
    val loading: Boolean = true,
    val alarms: List<AlarmItemUi> = emptyList(),
    val nextIn: TimeUntil? = null,
) {
    val isEmpty: Boolean get() = !loading && alarms.isEmpty()
}

/**
 * Карточка будильника.
 *
 * @property active тумблер «вкл»: включён **или** ждёт snooze/догон (разовый после звонка выключен, но отложен).
 * @property subtitle когда зазвонит ближайший раз; `null` — неактивен или звонок не сегодня и не завтра.
 */
@Immutable
data class AlarmItemUi(
    val id: AlarmId,
    val time: LocalTime,
    val label: String,
    val repeatDays: Set<DayOfWeek>,
    val active: Boolean,
    val subtitle: AlarmSubtitle?,
)

/** Подзаголовок карточки; текст — на экране. */
sealed interface AlarmSubtitle {
    data object Today : AlarmSubtitle

    data object Tomorrow : AlarmSubtitle

    /** Ожидает повторного звонка (snooze) в [time], локальное время текущей зоны. */
    data class SnoozedUntil(val time: LocalTime) : AlarmSubtitle
}

sealed interface AlarmListEvent {
    /** [enabled] — желаемое состояние тумблера (UI передаёт `!active`); тумблер не оптимистичный, следует за БД. */
    data class Toggle(val id: AlarmId, val enabled: Boolean) : AlarmListEvent

    data class Delete(val id: AlarmId) : AlarmListEvent
}

/** Одноразовые сообщения экрану (тост); тексты — на экране. */
sealed interface AlarmListEffect {
    /** Тумблер включил будильник: «Будильник зазвонит через …» (FR-EDIT-11). */
    data class RingsIn(val until: TimeUntil) : AlarmListEffect

    /** Система отказала в точном будильнике или включение упало: «Не удалось запланировать». */
    data object ScheduleFailed : AlarmListEffect

    /** Выключение упало, будильник остался включённым: «Не удалось выключить». */
    data object DisableFailed : AlarmListEffect

    data object DeleteFailed : AlarmListEffect
}
