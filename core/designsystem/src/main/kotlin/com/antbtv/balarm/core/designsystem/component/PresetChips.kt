package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.antbtv.balarm.core.designsystem.preview.BalarmComponentPreviews
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import java.time.DayOfWeek

/**
 * Пресеты дней «Будни / Выходные / Каждый день» (FR-EDIT-2) над [DayChipsRow].
 *
 * Пресет выбран, если [selectedDays] совпадает с его набором ([DayPreset.matches]). Тап отдаёт в [onSelect] новый
 * набор дней ([DayPreset.toggle]): дни пресета или — повторный тап по выбранному — пустой набор (разовый будильник).
 *
 * Выбранный чип — обводка и текст `primary` на `surface` (не только цвет: появляется обводка); заливка `primary`
 * не используется — текст 16sp на ней в тёмной теме не проходит 4.5:1. Чипы переносятся на новую строку,
 * если не влезают (fontScale 2 на 360dp).
 *
 * TalkBack: каждый чип — флажок (Role.Checkbox), имя — подпись, состояние — [selectedDescription] /
 * [notSelectedDescription].
 */
@Composable
fun PresetChips(
    selectedDays: Set<DayOfWeek>,
    onSelect: (Set<DayOfWeek>) -> Unit,
    weekdaysLabel: String,
    weekendLabel: String,
    everyDayLabel: String,
    selectedDescription: String,
    notSelectedDescription: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.testTag(PresetChipsTestTags.ROW),
        horizontalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        DayPreset.entries.forEach { preset ->
            val selected = preset.matches(selectedDays)
            PresetChip(
                label = when (preset) {
                    DayPreset.WEEKDAYS -> weekdaysLabel
                    DayPreset.WEEKEND -> weekendLabel
                    DayPreset.EVERY_DAY -> everyDayLabel
                },
                selected = selected,
                stateDescription = if (selected) selectedDescription else notSelectedDescription,
                onClick = { onSelect(preset.toggle(selectedDays)) },
                modifier = Modifier.testTag(preset.testTag),
            )
        }
    }
}

private val DayPreset.testTag: String
    get() = when (this) {
        DayPreset.WEEKDAYS -> PresetChipsTestTags.WEEKDAYS
        DayPreset.WEEKEND -> PresetChipsTestTags.WEEKEND
        DayPreset.EVERY_DAY -> PresetChipsTestTags.EVERY_DAY
    }

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    // Зона тапа ≥ 48dp по высоте, видимая «таблетка» — 40dp по центру.
    Box(
        modifier = modifier
            .heightIn(min = BalarmDimens.MinTouch)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { this.stateDescription = stateDescription },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = BalarmShapes.Circle,
            color = if (selected) colors.surface else colors.surfaceVariant,
            contentColor = if (selected) colors.primary else colors.textPrimary,
            modifier = Modifier
                .heightIn(min = BalarmDimens.PresetChipHeight)
                .widthIn(min = BalarmDimens.MinTouch)
                .border(
                    width = BalarmDimens.SelectedBorder,
                    color = if (selected) colors.primary else Color.Transparent,
                    shape = BalarmShapes.Circle,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = BalarmTheme.typography.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = BalarmDimens.ChipPaddingHorizontal),
                )
            }
        }
    }
}

// Превью-only: подписи не локализуются, это витрина компонентов для разработчика.

@Composable
private fun PreviewPresets(initial: Set<DayOfWeek>, ru: Boolean) {
    var days by remember { mutableStateOf(initial) }
    PresetChips(
        selectedDays = days,
        onSelect = { days = it },
        weekdaysLabel = if (ru) "Будни" else "Weekdays",
        weekendLabel = if (ru) "Выходные" else "Weekends",
        everyDayLabel = if (ru) "Каждый день" else "Every day",
        selectedDescription = if (ru) "Выбрано" else "Selected",
        notSelectedDescription = if (ru) "Не выбрано" else "Not selected",
    )
}

@BalarmComponentPreviews
@Composable
private fun PresetChipsPreview() {
    BalarmTheme {
        Column(
            modifier = Modifier.padding(BalarmDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
        ) {
            PreviewPresets(initial = DayPreset.WEEKDAYS.days, ru = false)
            PreviewPresets(initial = DayPreset.EVERY_DAY.days, ru = true)
            PreviewPresets(initial = emptySet(), ru = true)
        }
    }
}
