package com.antbtv.balarm.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.antbtv.balarm.core.designsystem.component.SettingRow
import com.antbtv.balarm.core.designsystem.component.SingleChoiceDialog
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.settings.SettingsPolicy
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.format.rememberWeekdayFormat
import com.antbtv.balarm.core.model.AutoStop
import java.time.DayOfWeek

/** Какой диалог выбора открыт; переживает поворот (`rememberSaveable`). */
private enum class GeneralDialog { AUTO_STOP, MISSION_IDLE, MISSION_VOLUME, FIRST_DAY }

private val AutoStopOptions: List<AutoStop> = AutoStop.entries

/** «Как в регионе» и три распространённых первых дня; другой сохранённый день показывается в строке как есть. */
private val FirstDayOptions: List<DayOfWeek?> = listOf(null, DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/**
 * Секция «Общие» (PRD §3.9, ADR-021): заголовок, карточка из четырёх строк, подпись «Изменения применятся со
 * следующего звонка». Строка открывает [SingleChoiceDialog] с пояснением; выбор сразу уходит в [onEvent] и
 * закрывает диалог. Пока настройки читаются ([general] `null`), строки без значений.
 *
 * TalkBack: строка — кнопка «Автостоп звонка, 30 минут», действие «Изменить»; заголовок секции — heading.
 */
@Composable
internal fun GeneralSettingsSection(
    general: GeneralSettingsUi?,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    val weekdays = rememberWeekdayFormat()
    var dialog by rememberSaveable { mutableStateOf<GeneralDialog?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.GENERAL_SECTION),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        Text(
            text = stringResource(R.string.settings_general),
            style = BalarmTheme.typography.body,
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BalarmDimens.CardPadding)
                .semantics { heading() },
        )
        GeneralCard(general = general, weekdays = weekdays, onOpen = { dialog = it })
        Text(
            text = stringResource(R.string.settings_general_apply_next),
            style = BalarmTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BalarmDimens.CardPadding)
                .testTag(SettingsTestTags.APPLY_NEXT),
        )
    }

    val close = { dialog = null }
    when (dialog) {
        GeneralDialog.AUTO_STOP -> AutoStopDialog(general?.autoStop, onEvent, close)
        GeneralDialog.MISSION_IDLE -> MissionIdleDialog(general?.missionIdleSeconds, onEvent, close)
        GeneralDialog.MISSION_VOLUME -> MissionVolumeDialog(general?.missionDuckPercent, onEvent, close)
        GeneralDialog.FIRST_DAY -> FirstDayDialog(general, weekdays, onEvent, close)
        null -> Unit
    }
}

/** Карточка из четырёх строк; тап открывает диалог выбора [onOpen]. */
@Composable
private fun GeneralCard(general: GeneralSettingsUi?, weekdays: WeekdayFormat, onOpen: (GeneralDialog) -> Unit) {
    Surface(shape = BalarmShapes.Card, color = BalarmTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            GeneralRow(
                title = stringResource(R.string.settings_auto_stop),
                value = general?.let { autoStopShort(it.autoStop) },
                valueDescription = general?.let { autoStopFull(it.autoStop) },
                onClick = { onOpen(GeneralDialog.AUTO_STOP) },
                testTag = SettingsTestTags.AUTO_STOP_ROW,
            )
            GeneralRow(
                title = stringResource(R.string.settings_mission_idle),
                value = general?.let { secondsShort(it.missionIdleSeconds) },
                valueDescription = general?.let { secondsFull(it.missionIdleSeconds) },
                onClick = { onOpen(GeneralDialog.MISSION_IDLE) },
                testTag = SettingsTestTags.MISSION_IDLE_ROW,
            )
            GeneralRow(
                title = stringResource(R.string.settings_mission_volume),
                value = general?.let { percentShort(it.missionDuckPercent) },
                valueDescription = general?.let { percentFull(it.missionDuckPercent) },
                onClick = { onOpen(GeneralDialog.MISSION_VOLUME) },
                testTag = SettingsTestTags.MISSION_VOLUME_ROW,
            )
            val firstDay = general?.let { firstDayValue(it.firstDayOfWeek, weekdays) }
            GeneralRow(
                title = stringResource(R.string.settings_first_day),
                value = firstDay,
                valueDescription = firstDay,
                onClick = { onOpen(GeneralDialog.FIRST_DAY) },
                testTag = SettingsTestTags.FIRST_DAY_ROW,
            )
        }
    }
}

@Composable
private fun GeneralRow(
    title: String,
    value: String?,
    valueDescription: String?,
    onClick: () -> Unit,
    testTag: String,
) {
    SettingRow(
        title = title,
        value = value,
        onClick = onClick,
        onClickLabel = stringResource(R.string.settings_action_change),
        contentDescription = valueDescription?.let { stringResource(R.string.settings_row_description, title, it) },
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun AutoStopDialog(current: AutoStop?, onEvent: (SettingsEvent) -> Unit, onClose: () -> Unit) {
    val options = AutoStopOptions.map { option ->
        if (option ==
            AutoStop.NEVER
        ) {
            stringResource(R.string.settings_auto_stop_never_option)
        } else {
            autoStopShort(option)
        }
    }
    SingleChoiceDialog(
        title = stringResource(R.string.settings_auto_stop),
        description = stringResource(R.string.settings_auto_stop_hint),
        options = options,
        selectedIndex = AutoStopOptions.indexOf(current),
        onSelect = { index ->
            onEvent(SettingsEvent.AutoStopChanged(AutoStopOptions[index]))
            onClose()
        },
        onDismiss = onClose,
        dismissText = stringResource(R.string.settings_cancel),
    )
}

@Composable
private fun MissionIdleDialog(current: Long?, onEvent: (SettingsEvent) -> Unit, onClose: () -> Unit) {
    val values = SettingsPolicy.IDLE_TIMEOUT_SECONDS
    SingleChoiceDialog(
        title = stringResource(R.string.settings_mission_idle),
        description = stringResource(R.string.settings_mission_idle_hint),
        options = values.map { secondsShort(it) },
        selectedIndex = values.indexOf(current),
        onSelect = { index ->
            onEvent(SettingsEvent.MissionIdleChanged(values[index]))
            onClose()
        },
        onDismiss = onClose,
        dismissText = stringResource(R.string.settings_cancel),
    )
}

@Composable
private fun MissionVolumeDialog(current: Int?, onEvent: (SettingsEvent) -> Unit, onClose: () -> Unit) {
    val values = SettingsPolicy.DUCK_PERCENTS
    SingleChoiceDialog(
        title = stringResource(R.string.settings_mission_volume),
        description = stringResource(R.string.settings_mission_volume_hint),
        options = values.map { percentShort(it) },
        selectedIndex = values.indexOf(current),
        onSelect = { index ->
            onEvent(SettingsEvent.MissionDuckChanged(values[index]))
            onClose()
        },
        onDismiss = onClose,
        dismissText = stringResource(R.string.settings_cancel),
    )
}

@Composable
private fun FirstDayDialog(
    general: GeneralSettingsUi?,
    weekdays: WeekdayFormat,
    onEvent: (SettingsEvent) -> Unit,
    onClose: () -> Unit,
) {
    val options = FirstDayOptions.map { day ->
        if (day == null) {
            stringResource(R.string.settings_first_day_locale_option, weekdays.fullName(weekdays.firstDay))
        } else {
            dayName(day, weekdays)
        }
    }
    SingleChoiceDialog(
        title = stringResource(R.string.settings_first_day),
        options = options,
        // Пока настройки читаются — ничего не выбрано (null в списке означает «Как в регионе»).
        selectedIndex = if (general == null) -1 else FirstDayOptions.indexOf(general.firstDayOfWeek),
        onSelect = { index ->
            onEvent(SettingsEvent.FirstDayOfWeekChanged(FirstDayOptions[index]))
            onClose()
        },
        onDismiss = onClose,
        dismissText = stringResource(R.string.settings_cancel),
    )
}

@Composable
private fun autoStopShort(value: AutoStop): String = when (val duration = value.duration) {
    null -> stringResource(R.string.settings_auto_stop_never)
    else -> stringResource(R.string.settings_minutes_short, duration.toMinutes().toInt())
}

@Composable
private fun autoStopFull(value: AutoStop): String = when (val duration = value.duration) {
    null -> stringResource(R.string.settings_auto_stop_never)
    else -> duration.toMinutes().toInt().let { pluralStringResource(R.plurals.settings_minutes, it, it) }
}

@Composable
private fun secondsShort(seconds: Long): String = stringResource(R.string.settings_seconds_short, seconds.toInt())

@Composable
private fun secondsFull(seconds: Long): String =
    seconds.toInt().let { pluralStringResource(R.plurals.settings_seconds, it, it) }

@Composable
private fun percentShort(percent: Int): String = stringResource(R.string.settings_percent, percent)

@Composable
private fun percentFull(percent: Int): String = pluralStringResource(R.plurals.settings_percent_full, percent, percent)

@Composable
private fun firstDayValue(day: DayOfWeek?, weekdays: WeekdayFormat): String =
    if (day == null) stringResource(R.string.settings_first_day_locale) else dayName(day, weekdays)

/** «Понедельник» — с заглавной: в русском полные имена дней строчные. */
private fun dayName(day: DayOfWeek, weekdays: WeekdayFormat): String =
    weekdays.fullName(day).replaceFirstChar { it.titlecase(weekdays.locale) }
