package com.antbtv.balarm.feature.alarmedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.ConfirmDialog
import com.antbtv.balarm.core.designsystem.component.DayChipsRow
import com.antbtv.balarm.core.designsystem.component.LabelField
import com.antbtv.balarm.core.designsystem.component.PresetChips
import com.antbtv.balarm.core.designsystem.component.PrimaryButton
import com.antbtv.balarm.core.designsystem.component.ScrimEdge
import com.antbtv.balarm.core.designsystem.component.SecondaryButton
import com.antbtv.balarm.core.designsystem.component.SettingRow
import com.antbtv.balarm.core.designsystem.component.SingleChoiceDialog
import com.antbtv.balarm.core.designsystem.component.SystemBarScrim
import com.antbtv.balarm.core.designsystem.component.TimeWheelPicker
import com.antbtv.balarm.core.designsystem.component.labelLength
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.format.rememberClockFormat
import com.antbtv.balarm.core.format.rememberWeekdayFormat
import com.antbtv.balarm.core.model.Alarm
import com.antbtv.balarm.core.model.AlarmId
import com.antbtv.balarm.core.model.SnoozeSettings
import com.antbtv.balarm.core.model.SoundRef
import java.text.DateFormatSymbols
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.util.Locale

/**
 * Редактор будильника с ViewModel (ADR-009: навигация — колбэком Route). [alarmId] `null` — новый будильник.
 * [onClose] вызывается один раз: после сохранения (с тостом «зазвонит через …»), удаления, Back без правок
 * («Отменить изменения?» → «Не сохранять»), а также если будильника уже нет или он не загрузился.
 *
 * Back без правок редактор не перехватывает — его обрабатывает навигация (predictive back с анимацией);
 * `onClose` в этом случае не вызывается.
 *
 * Insets: экран сам обрабатывает `WindowInsets.safeDrawing` (edge-to-edge, вместе с клавиатурой). `:app` не
 * добавляет отступов и не оборачивает экран в `Scaffold` / `padding(innerPadding)` — иначе отступы удвоятся.
 *
 * Мелодия (ADR-016 §8): тап по строке «Мелодия» → [onPickSound] с текущим выбором — `:app` открывает пикер;
 * выбор возвращается параметром [pickedSound] (Result API в `:app`), редактор применяет его один раз и сообщает
 * [onSoundPickConsumed] — `:app` очищает результат, чтобы он не применился повторно после поворота.
 */
@Composable
fun AlarmEditRoute(
    alarmId: AlarmId?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onPickSound: (current: SoundRef) -> Unit = {},
    pickedSound: SoundRef? = null,
    onSoundPickConsumed: () -> Unit = {},
) {
    val viewModel = hiltViewModel<AlarmEditViewModel, AlarmEditViewModel.Factory>(
        creationCallback = { factory -> factory.create(alarmId?.value) },
    )
    AlarmEditRoute(
        viewModel = viewModel,
        onClose = onClose,
        modifier = modifier,
        onPickSound = onPickSound,
        pickedSound = pickedSound,
        onSoundPickConsumed = onSoundPickConsumed,
    )
}

/** То же с готовой ViewModel: сквозные тесты собирают её на фейках без Hilt. */
@Composable
internal fun AlarmEditRoute(
    viewModel: AlarmEditViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onPickSound: (current: SoundRef) -> Unit = {},
    pickedSound: SoundRef? = null,
    onSoundPickConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlarmEditEffectsHandler(effects = viewModel.effects, onClose = onClose, onPickSound = onPickSound)
    val currentOnSoundPickConsumed by rememberUpdatedState(onSoundPickConsumed)
    LaunchedEffect(pickedSound, viewModel) {
        if (pickedSound != null) {
            viewModel.onEvent(AlarmEditEvent.SoundSelected(pickedSound))
            currentOnSoundPickConsumed()
        }
    }
    // Свернули/ушли с экрана — превью громкости замолкает (ADR-017 §6).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onEvent(AlarmEditEvent.StopSoundPreview) }
    AlarmEditScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Редактор (FR-EDIT-1…11, скилл alarmy-ui): заголовок, колесо времени, пресеты и дни, метка, snooze и «Звук»
 * (каждая — если включён её флаг), «Тест» и «Удалить»; «Сохранить» закреплена снизу и видна при любой прокрутке.
 *
 * Пока будильник загружается — только заголовок: форма со значениями по умолчанию не мигает перед настоящими.
 * Во время сохранения/удаления кнопки недоступны. Тексты диалогов и значения — здесь, состояние диалогов —
 * в ViewModel (выбор варианта закрывает диалог редуктор).
 */
@Composable
fun AlarmEditScreen(state: AlarmEditUiState, onEvent: (AlarmEditEvent) -> Unit, modifier: Modifier = Modifier) {
    AlarmEditScreen(
        state = state,
        clockFormat = rememberClockFormat(),
        weekdayFormat = rememberWeekdayFormat(),
        onEvent = onEvent,
        modifier = modifier,
    )
}

/**
 * То же с явными форматами: превью и тесты задают 12/24 ч и локаль, не трогая системные настройки.
 *
 * @param windowInsets системные отступы (в приложении — `safeDrawing`); тесты и превью задают свои.
 */
@Composable
internal fun AlarmEditScreen(
    state: AlarmEditUiState,
    clockFormat: ClockFormat,
    weekdayFormat: WeekdayFormat,
    onEvent: (AlarmEditEvent) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    // С правками — Back к ViewModel («Отменить изменения?»); во время сохранения — тоже (ViewModel его
    // игнорирует: экран закроется сам после тоста, а не потеряет его). Без правок обработчик выключен —
    // Back достаётся навигации с системной анимацией predictive back.
    BackHandler(enabled = state.isDirty || state.saving) { onEvent(AlarmEditEvent.Back) }

    val colors = BalarmTheme.colors
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(AlarmEditTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        val topInset = windowInsets.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal)),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                EditorContent(
                    state = state,
                    clockFormat = clockFormat,
                    weekdayFormat = weekdayFormat,
                    topInset = topInset,
                    onEvent = onEvent,
                )
                // Контент прокручивается под прозрачный статус-бар; снизу — мягкий переход к «Сохранить».
                SystemBarScrim(
                    edge = ScrimEdge.Top,
                    inset = topInset,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .testTag(AlarmEditTestTags.TOP_SCRIM),
                )
                if (!state.loading) {
                    SystemBarScrim(
                        edge = ScrimEdge.Bottom,
                        inset = 0.dp,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            if (!state.loading) {
                SaveButton(
                    enabled = !state.saving,
                    onSave = { onEvent(AlarmEditEvent.Save) },
                    bottomInsets = windowInsets.only(WindowInsetsSides.Bottom),
                )
            }
        }
    }

    EditorDialog(state = state, clockFormat = clockFormat, onEvent = onEvent)
}

@Composable
private fun EditorContent(
    state: AlarmEditUiState,
    clockFormat: ClockFormat,
    weekdayFormat: WeekdayFormat,
    topInset: Dp,
    onEvent: (AlarmEditEvent) -> Unit,
) {
    val draft = state.draft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(AlarmEditTestTags.CONTENT)
            // Снизу — ещё и высота градиента над «Сохранить»: последний элемент в конце прокрутки им не перекрыт.
            .padding(
                top = topInset + BalarmDimens.ScreenPadding,
                bottom = BalarmDimens.ScreenPadding + BalarmDimens.SystemBarScrimFade,
            ),
        // Колесо (Row в BoxWithConstraints) само не центрируется — центрирует колонка.
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.ScreenPadding),
    ) {
        EditorTitle(isNew = state.isNew)
        if (state.loading) return@Column
        TimePicker(time = draft.time, clockFormat = clockFormat, onEvent = onEvent)
        DaysSection(repeatDays = draft.repeatDays, weekdayFormat = weekdayFormat, onEvent = onEvent)
        LabelSection(label = draft.label, onEvent = onEvent)
        if (state.snoozeVisible) {
            SnoozeSection(
                snooze = draft.snooze,
                locale = clockFormat.locale,
                enabled = !state.saving,
                onEvent = onEvent,
            )
        }
        if (state.soundVisible) {
            SoundSection(
                sound = draft.sound,
                soundName = state.soundName,
                vibrate = draft.vibrate,
                locale = clockFormat.locale,
                enabled = !state.saving,
                onEvent = onEvent,
            )
        }
        ActionButtons(canDelete = state.canDelete, enabled = !state.saving, onEvent = onEvent)
    }
}

@Composable
private fun EditorTitle(isNew: Boolean) {
    Text(
        text = stringResource(if (isNew) R.string.alarm_edit_title_new else R.string.alarm_edit_title_existing),
        style = BalarmTheme.typography.title,
        color = BalarmTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BalarmDimens.ScreenPadding)
            .semantics { heading() }
            .testTag(AlarmEditTestTags.TITLE),
    )
}

@Composable
private fun TimePicker(time: LocalTime, clockFormat: ClockFormat, onEvent: (AlarmEditEvent) -> Unit) {
    val amPm = remember(clockFormat.locale) { DateFormatSymbols.getInstance(clockFormat.locale).amPmStrings }
    val currentOnEvent by rememberUpdatedState(onEvent)
    val onTimeChange = remember {
        { hour: Int, minute: Int -> currentOnEvent(AlarmEditEvent.TimeChanged(LocalTime.of(hour, minute))) }
    }
    TimeWheelPicker(
        hour = time.hour,
        minute = time.minute,
        onTimeChange = onTimeChange,
        is24Hour = clockFormat.is24Hour,
        hoursLabel = stringResource(R.string.alarm_edit_wheel_hours),
        minutesLabel = stringResource(R.string.alarm_edit_wheel_minutes),
        periodLabel = stringResource(R.string.alarm_edit_wheel_period),
        amLabel = amPm[0],
        pmLabel = amPm[1],
        increaseLabel = stringResource(R.string.alarm_edit_wheel_increase),
        decreaseLabel = stringResource(R.string.alarm_edit_wheel_decrease),
    )
}

@Composable
private fun DaysSection(repeatDays: Set<DayOfWeek>, weekdayFormat: WeekdayFormat, onEvent: (AlarmEditEvent) -> Unit) {
    val selected = stringResource(R.string.alarm_edit_selected)
    val notSelected = stringResource(R.string.alarm_edit_not_selected)
    // Нестабильный List<DayChipUi> пересобирается только при смене дней или локали.
    val chips = remember(repeatDays, weekdayFormat) { dayChips(repeatDays, weekdayFormat) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        PresetChips(
            selectedDays = repeatDays,
            onSelect = { next -> pressedPreset(repeatDays, next)?.let { onEvent(AlarmEditEvent.PresetSelected(it)) } },
            weekdaysLabel = stringResource(R.string.alarm_edit_preset_weekdays),
            weekendLabel = stringResource(R.string.alarm_edit_preset_weekend),
            everyDayLabel = stringResource(R.string.alarm_edit_preset_every_day),
            selectedDescription = selected,
            notSelectedDescription = notSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BalarmDimens.ScreenPadding),
        )
        // Отступ строки дней — 8dp, а не 20dp экрана: на 360dp ячейка (1/7 ширины) иначе уже 48dp.
        DayChipsRow(
            days = chips,
            onToggle = { onEvent(AlarmEditEvent.DayToggled(it)) },
            selectedDescription = selected,
            notSelectedDescription = notSelected,
            modifier = Modifier.padding(horizontal = BalarmDimens.SpacingSmall),
        )
    }
}

@Composable
private fun LabelSection(label: String, onEvent: (AlarmEditEvent) -> Unit) {
    val maxLength = Alarm.MAX_LABEL_LENGTH
    LabelField(
        value = label,
        onValueChange = { onEvent(AlarmEditEvent.LabelChanged(it)) },
        label = stringResource(R.string.alarm_edit_label),
        maxLength = maxLength,
        // Форма слова «символов» согласуется с лимитом: «12 из 40 символов».
        counterDescription = pluralStringResource(
            R.plurals.alarm_edit_label_counter,
            maxLength,
            labelLength(label),
            maxLength,
        ),
        modifier = Modifier.padding(horizontal = BalarmDimens.ScreenPadding),
    )
}

@Composable
private fun SnoozeSection(
    snooze: SnoozeSettings,
    locale: Locale,
    enabled: Boolean,
    onEvent: (AlarmEditEvent) -> Unit,
) {
    val resources = LocalResources.current
    val snoozeTitle = stringResource(R.string.alarm_edit_snooze)
    val limitTitle = stringResource(R.string.alarm_edit_snooze_limit)
    val changeLabel = stringResource(R.string.alarm_edit_action_change)
    val interval = snooze.interval
    val intervalShort = remember(interval, resources, locale) {
        snoozeIntervalText(interval, resources, locale, wide = false)
    }
    val intervalWide = remember(interval, resources, locale) {
        snoozeIntervalText(interval, resources, locale, wide = true)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BalarmDimens.ScreenPadding),
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
    ) {
        Column {
            SettingRow(
                title = snoozeTitle,
                value = intervalShort,
                onClick = { onEvent(AlarmEditEvent.ShowSnoozeIntervalDialog) },
                onClickLabel = changeLabel,
                // TalkBack — полное «5 минут», а не «5 мин».
                contentDescription = stringResource(R.string.alarm_edit_row_description, snoozeTitle, intervalWide),
                enabled = enabled,
                modifier = Modifier.testTag(AlarmEditTestTags.SNOOZE_INTERVAL),
            )
            // Лимит без интервала не имеет смысла (редуктор и не откроет его диалог) — строка скрыта.
            if (snooze.isEnabled) {
                SettingRow(
                    title = limitTitle,
                    value = remember(snooze.maxCount, resources) { snoozeLimitText(snooze.maxCount, resources) },
                    onClick = { onEvent(AlarmEditEvent.ShowSnoozeLimitDialog) },
                    onClickLabel = changeLabel,
                    enabled = enabled,
                    modifier = Modifier.testTag(AlarmEditTestTags.SNOOZE_LIMIT),
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(canDelete: Boolean, enabled: Boolean, onEvent: (AlarmEditEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BalarmDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        SecondaryButton(
            text = stringResource(R.string.alarm_edit_test),
            onClick = { onEvent(AlarmEditEvent.Test) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AlarmEditTestTags.TEST),
        )
        if (canDelete) {
            SecondaryButton(
                text = stringResource(R.string.alarm_edit_delete),
                onClick = { onEvent(AlarmEditEvent.Delete) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AlarmEditTestTags.DELETE),
            )
        }
    }
}

/** «Сохранить» закреплена снизу — над навигационной панелью и клавиатурой (`safeDrawing` включает IME). */
@Composable
private fun SaveButton(enabled: Boolean, onSave: () -> Unit, bottomInsets: WindowInsets) {
    PrimaryButton(
        text = stringResource(R.string.alarm_edit_save),
        onClick = onSave,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(bottomInsets)
            .padding(
                start = BalarmDimens.ScreenPadding,
                end = BalarmDimens.ScreenPadding,
                top = BalarmDimens.SpacingSmall,
                bottom = BalarmDimens.ScreenPadding,
            )
            .testTag(AlarmEditTestTags.SAVE),
    )
}

@Composable
private fun EditorDialog(state: AlarmEditUiState, clockFormat: ClockFormat, onEvent: (AlarmEditEvent) -> Unit) {
    val onDismiss = { onEvent(AlarmEditEvent.DialogDismissed) }
    when (state.dialog) {
        EditDialog.SnoozeInterval -> SnoozeIntervalDialog(
            current = state.draft.snooze.interval,
            locale = clockFormat.locale,
            onEvent = onEvent,
        )

        EditDialog.SnoozeLimit -> SnoozeLimitDialog(current = state.draft.snooze.maxCount, onEvent = onEvent)

        EditDialog.FadeIn -> FadeInDialog(
            current = state.draft.sound.fadeIn,
            locale = clockFormat.locale,
            onEvent = onEvent,
        )

        EditDialog.ConfirmDelete -> ConfirmDialog(
            title = stringResource(R.string.alarm_edit_delete_title),
            // Время сохранённого будильника, а не черновика: удаляется то, что в списке.
            text = stringResource(R.string.alarm_edit_delete_text, clockFormat.time(state.initial.time)),
            confirmText = stringResource(R.string.alarm_edit_delete),
            dismissText = stringResource(R.string.alarm_edit_cancel),
            onConfirm = { onEvent(AlarmEditEvent.ConfirmDelete) },
            onDismiss = onDismiss,
            destructive = true,
        )

        EditDialog.ConfirmDiscard -> ConfirmDialog(
            title = stringResource(R.string.alarm_edit_discard_title),
            text = stringResource(R.string.alarm_edit_discard_text),
            confirmText = stringResource(R.string.alarm_edit_discard_confirm),
            dismissText = stringResource(R.string.alarm_edit_discard_dismiss),
            onConfirm = { onEvent(AlarmEditEvent.DiscardConfirmed) },
            onDismiss = onDismiss,
            destructive = true,
        )

        null -> Unit
    }
}

/** Выбор варианта шлёт событие — диалог закрывает редуктор вместе с правкой черновика. */
@Composable
private fun SnoozeIntervalDialog(current: Duration?, locale: Locale, onEvent: (AlarmEditEvent) -> Unit) {
    val resources = LocalResources.current
    val options = remember(current) { snoozeIntervalOptions(current) }
    // В диалоге места хватает — полные «5 минут» (и TalkBack читает их без сокращений).
    val labels = remember(options, resources, locale) {
        options.map { snoozeIntervalText(it, resources, locale, wide = it != null) }
    }
    SingleChoiceDialog(
        title = stringResource(R.string.alarm_edit_snooze_interval_title),
        options = labels,
        selectedIndex = options.indexOf(current),
        onSelect = { onEvent(AlarmEditEvent.SnoozeIntervalSelected(options[it])) },
        onDismiss = { onEvent(AlarmEditEvent.DialogDismissed) },
        dismissText = stringResource(R.string.alarm_edit_cancel),
    )
}

@Composable
private fun SnoozeLimitDialog(current: Int?, onEvent: (AlarmEditEvent) -> Unit) {
    val resources = LocalResources.current
    val options = remember(current) { snoozeLimitOptions(current) }
    val labels = remember(options, resources) { options.map { snoozeLimitText(it, resources) } }
    SingleChoiceDialog(
        title = stringResource(R.string.alarm_edit_snooze_limit),
        options = labels,
        selectedIndex = options.indexOf(current),
        onSelect = { onEvent(AlarmEditEvent.SnoozeLimitSelected(options[it])) },
        onDismiss = { onEvent(AlarmEditEvent.DialogDismissed) },
        dismissText = stringResource(R.string.alarm_edit_cancel),
    )
}
