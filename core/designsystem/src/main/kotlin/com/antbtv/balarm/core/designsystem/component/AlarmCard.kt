package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.preview.PreviewWeekdays
import com.antbtv.balarm.core.designsystem.preview.PreviewWeekdaysRu
import com.antbtv.balarm.core.designsystem.theme.BalarmColors
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme

/** Метка в карточке — максимум две строки, дальше многоточие. */
private const val LABEL_MAX_LINES = 2

/**
 * Карточка будильника в списке. Принимает только готовые строки: форматирование времени, дней и подписи — у
 * вызывающего (`:core:format`), модель `:core:model` компонент не знает.
 *
 * Семантика: карточка — один узел TalkBack с [contentDescription] («Будильник 07:30, будни, включён»),
 * внутренние тексты из дерева исключены; переключатель — отдельный узел с ролью Switch, состоянием
 * и описанием [toggleDescription].
 *
 * @param time время без AM/PM («07:30»).
 * @param amPm «AM»/«PM» в 12-часовом формате, `null` — 24-часовой.
 * @param label метка; пустая — не показывается.
 * @param days дни повтора для [DayPillsRow]; пустой список — строка не показывается.
 * @param active включён ли будильник: выключенный — время и метка цветом `textSecondary`.
 * @param subtitle «Завтра», «Отложен до 07:05»; `null` — нет.
 * @param contentDescription описание карточки для TalkBack целиком («Будильник 07:30, будни, включён»).
 * @param toggleDescription описание переключателя без состояния («Будильник 07:30»): вкл/выкл TalkBack
 * озвучивает сам по роли Switch, повтор состояния из [contentDescription] не нужен.
 * @param onToggle новое значение переключателя.
 * @param onClick тап по карточке (открыть редактор).
 * @param onLongClick долгий тап по карточке (удаление).
 * @param onClickLabel название действия тапа для TalkBack («Изменить»).
 * @param onLongClickLabel название действия долгого тапа для TalkBack («Удалить»).
 */
@Composable
fun AlarmCard(
    time: String,
    amPm: String?,
    label: String,
    days: List<DayPillUi>,
    active: Boolean,
    subtitle: String?,
    contentDescription: String,
    toggleDescription: String,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onClickLabel: String,
    onLongClick: () -> Unit,
    onLongClickLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BalarmShapes.Card,
        color = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = BalarmDimens.MinTouch)
                .testTag(AlarmCardTestTags.CARD)
                .combinedClickable(
                    onClickLabel = onClickLabel,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .semantics { this.contentDescription = contentDescription }
                .padding(BalarmDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
        ) {
            AlarmCardContent(
                time = time,
                amPm = amPm,
                label = label,
                days = days,
                active = active,
                subtitle = subtitle,
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {},
            )
            BalarmSwitch(
                checked = active,
                onCheckedChange = onToggle,
                contentDescription = toggleDescription,
                modifier = Modifier.testTag(AlarmCardTestTags.SWITCH),
            )
        }
    }
}

@Composable
private fun AlarmCardContent(
    time: String,
    amPm: String?,
    label: String,
    days: List<DayPillUi>,
    active: Boolean,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    val mainColor = alarmCardTextColor(active, colors)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingTiny)) {
        Row {
            Text(
                text = time,
                color = mainColor,
                style = type.timeLarge,
                maxLines = 1,
                softWrap = false,
                // fontScale 2 на 360dp: 44sp × 2 не влезает рядом с переключателем — ужимаем, а не обрезаем.
                autoSize = TextAutoSize.StepBased(maxFontSize = type.timeLarge.fontSize),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline(),
            )
            if (amPm != null) {
                Text(
                    text = amPm,
                    color = mainColor,
                    style = type.body,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = BalarmDimens.SpacingTiny)
                        .alignByBaseline(),
                )
            }
        }
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = mainColor,
                style = type.body,
                maxLines = LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(text = subtitle, color = colors.textSecondary, style = type.caption)
        }
        if (days.isNotEmpty()) {
            DayPillsRow(
                days = days,
                active = active,
                modifier = Modifier.padding(top = BalarmDimens.SpacingTiny),
            )
        }
    }
}

/** Цвет времени и метки: выключенный будильник «гаснет» до `textSecondary`. */
internal fun alarmCardTextColor(active: Boolean, colors: BalarmColors): Color =
    if (active) colors.textPrimary else colors.textSecondary

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@Composable
private fun AlarmCardSheet() {
    Column(
        modifier = Modifier.padding(BalarmDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        PreviewAlarmCard(
            time = "06:30",
            label = "Workout",
            subtitle = "Tomorrow",
            contentDescription = "Alarm 06:30, Workout, weekdays, on",
        )
        PreviewAlarmCard(
            time = "7:05",
            amPm = "AM",
            subtitle = "Snoozed until 7:10 AM",
            contentDescription = "Alarm 7:05 AM, snoozed, on",
        )
        PreviewAlarmCard(
            time = "09:00",
            label = "A very long label that does not fit into a single line at all",
            active = false,
            contentDescription = "Alarm 09:00, off",
        )
        // Самые широкие подписи дней (RU) — проверка fontScale 2 на 360dp.
        PreviewAlarmCard(
            time = "23:59",
            label = "Подъём",
            days = PreviewWeekdaysRu,
            subtitle = "Завтра",
            contentDescription = "Будильник 23:59, Подъём, будни, включён",
        )
    }
}

@Composable
private fun PreviewAlarmCard(
    time: String,
    contentDescription: String,
    amPm: String? = null,
    label: String = "",
    days: List<DayPillUi> = PreviewWeekdays,
    active: Boolean = true,
    subtitle: String? = null,
) {
    AlarmCard(
        time = time,
        amPm = amPm,
        label = label,
        days = days,
        active = active,
        subtitle = subtitle,
        contentDescription = contentDescription,
        toggleDescription = "Alarm $time",
        onToggle = {},
        onClick = {},
        onClickLabel = "Edit",
        onLongClick = {},
        onLongClickLabel = "Delete",
    )
}

@BalarmComponentPreviews
@Composable
private fun AlarmCardPreview() {
    BalarmTheme { AlarmCardSheet() }
}
