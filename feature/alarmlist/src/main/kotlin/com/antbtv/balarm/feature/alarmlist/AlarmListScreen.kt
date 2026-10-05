package com.antbtv.balarm.feature.alarmlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.AlarmCard
import com.antbtv.balarm.core.designsystem.component.BalarmFab
import com.antbtv.balarm.core.designsystem.component.BalarmIcons
import com.antbtv.balarm.core.designsystem.component.ConfirmDialog
import com.antbtv.balarm.core.designsystem.component.NextAlarmHeader
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.schedule.TimeUntil
import com.antbtv.balarm.core.format.ClockFormat
import com.antbtv.balarm.core.format.WeekdayFormat
import com.antbtv.balarm.core.format.formatTimeUntil
import com.antbtv.balarm.core.format.rememberClockFormat
import com.antbtv.balarm.core.format.rememberWeekdayFormat
import com.antbtv.balarm.core.model.AlarmId

/**
 * Список будильников с ViewModel (ADR-009: навигация — колбэками Route, не событиями ViewModel).
 * Состояние — с учётом жизненного цикла; эффекты собираются здесь одним сборщиком (тосты).
 *
 * Insets: экран сам обрабатывает `WindowInsets.safeDrawing` (edge-to-edge). `:app` не добавляет отступов
 * и не оборачивает экран в `Scaffold` / `padding(innerPadding)` — иначе отступ под системные бары будет двойным.
 */
@Composable
fun AlarmListRoute(
    onAddAlarm: () -> Unit,
    onOpenAlarm: (AlarmId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AlarmListEffectsHandler(viewModel.effects)
    AlarmListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onAddAlarm = onAddAlarm,
        onOpenAlarm = onOpenAlarm,
        modifier = modifier,
    )
}

/**
 * Список будильников (FR-LIST-1…4, скилл alarmy-ui): шапка «Следующий будильник через …», карточки, FAB справа
 * снизу. Долгий тап по карточке — меню «Удалить» → подтверждение. Пока идёт первая загрузка — ни шапки,
 * ни «пусто»: «Нет будильников» не мигает перед списком.
 *
 * Тумблер шлёт `Toggle(id, enabled = !item.active)`, значение из `onToggle` намеренно игнорируется: команда
 * идемпотентна, а быстрый повторный тап до эмиссии БД просто даёт повторный `Toggle`.
 */
@Composable
fun AlarmListScreen(
    state: AlarmListUiState,
    onEvent: (AlarmListEvent) -> Unit,
    onAddAlarm: () -> Unit,
    onOpenAlarm: (AlarmId) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlarmListScreen(
        state = state,
        clockFormat = rememberClockFormat(),
        weekdayFormat = rememberWeekdayFormat(),
        onEvent = onEvent,
        onAddAlarm = onAddAlarm,
        onOpenAlarm = onOpenAlarm,
        modifier = modifier,
    )
}

/** То же с явными форматами: превью и тесты задают 12/24 ч, не трогая системную настройку. */
@Composable
internal fun AlarmListScreen(
    state: AlarmListUiState,
    clockFormat: ClockFormat,
    weekdayFormat: WeekdayFormat,
    onEvent: (AlarmListEvent) -> Unit,
    onAddAlarm: () -> Unit,
    onOpenAlarm: (AlarmId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BalarmTheme.colors
    // Id будильника с открытым меню / с диалогом удаления. Long — чтобы переживать пересоздание Activity.
    var menuFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteFor by rememberSaveable { mutableStateOf<Long?>(null) }
    // Будильник удалён в другом месте (или после восстановления его уже нет) — забываем меню и диалог, чтобы
    // они не всплыли, если id снова появится. Пока идёт загрузка, список ещё пуст — не трогаем.
    LaunchedEffect(state.loading, state.alarms) {
        if (!state.loading) {
            val ids = state.alarms.mapTo(HashSet()) { it.id.value }
            if (menuFor != null && menuFor !in ids) menuFor = null
            if (deleteFor != null && deleteFor !in ids) deleteFor = null
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(AlarmListTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlarmList(
                state = state,
                clockFormat = clockFormat,
                weekdayFormat = weekdayFormat,
                menuFor = menuFor,
                onEvent = onEvent,
                onOpenAlarm = onOpenAlarm,
                onShowMenu = { menuFor = it.value },
                onDismissMenu = { menuFor = null },
                onDeleteRequest = {
                    menuFor = null
                    deleteFor = it.value
                },
            )
            BalarmFab(
                onClick = onAddAlarm,
                contentDescription = stringResource(R.string.alarm_list_add),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(BalarmDimens.ScreenPadding)
                    .testTag(AlarmListTestTags.FAB),
            )
        }
    }

    // Будильник мог исчезнуть — диалог скрывается сразу, не дожидаясь очистки в LaunchedEffect.
    val toDelete = deleteFor?.let { id -> state.alarms.firstOrNull { it.id.value == id } }
    if (toDelete != null) {
        DeleteAlarmDialog(
            item = toDelete,
            clockFormat = clockFormat,
            onConfirm = {
                deleteFor = null
                onEvent(AlarmListEvent.Delete(toDelete.id))
            },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun AlarmList(
    state: AlarmListUiState,
    clockFormat: ClockFormat,
    weekdayFormat: WeekdayFormat,
    menuFor: Long?,
    onEvent: (AlarmListEvent) -> Unit,
    onOpenAlarm: (AlarmId) -> Unit,
    onShowMenu: (AlarmId) -> Unit,
    onDismissMenu: () -> Unit,
    onDeleteRequest: (AlarmId) -> Unit,
) {
    // По бокам — не под вырезами/барами. Сверху и снизу — отступ контента: в начале шапка не под статус-баром,
    // при прокрутке карточки уходят под прозрачные бары, а последняя поднимается над FAB и навигацией.
    val verticalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical).asPaddingValues()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .testTag(AlarmListTestTags.LIST),
        contentPadding = PaddingValues(
            start = BalarmDimens.ScreenPadding,
            end = BalarmDimens.ScreenPadding,
            top = verticalInsets.calculateTopPadding() + BalarmDimens.ScreenPadding,
            bottom = verticalInsets.calculateBottomPadding() + BalarmDimens.Fab + BalarmDimens.ScreenPadding * 2,
        ),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        if (!state.loading) {
            item(key = HEADER_KEY, contentType = HEADER_KEY) {
                ListHeader(
                    nextIn = state.nextIn,
                    modifier = Modifier.padding(bottom = BalarmDimens.SpacingSmall),
                )
            }
        }
        if (state.isEmpty) {
            item(key = EMPTY_KEY, contentType = EMPTY_KEY) { EmptyState() }
        }
        items(items = state.alarms, key = { it.id.value }, contentType = { CARD_CONTENT_TYPE }) { item ->
            AlarmListItem(
                item = item,
                clockFormat = clockFormat,
                weekdayFormat = weekdayFormat,
                menuExpanded = menuFor == item.id.value,
                onToggle = { onEvent(AlarmListEvent.Toggle(item.id, enabled = !item.active)) },
                onClick = { onOpenAlarm(item.id) },
                onLongClick = { onShowMenu(item.id) },
                onDismissMenu = onDismissMenu,
                onDeleteRequest = { onDeleteRequest(item.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ListHeader(nextIn: TimeUntil?, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val short = remember(nextIn, locale) { nextIn?.let { formatTimeUntil(it, locale) } }
    val wide = remember(nextIn, locale) { nextIn?.let { formatTimeUntil(it, locale, wide = true) } }
    val title = if (short == null) {
        stringResource(R.string.alarm_list_no_active)
    } else {
        stringResource(R.string.alarm_list_next_in, short)
    }
    // TalkBack — полная форма («7 часов 12 минут»), на экране — короткая.
    val titleDescription = wide?.let { stringResource(R.string.alarm_list_next_in, it) }
    NextAlarmHeader(title = title, subtitle = null, titleDescription = titleDescription, modifier = modifier)
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = BalarmTheme.colors
    val type = BalarmTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = BalarmDimens.ScreenPadding)
            .testTag(AlarmListTestTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.SpacingSmall),
    ) {
        Text(
            text = stringResource(R.string.alarm_list_empty_title),
            style = type.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.alarm_list_empty_hint),
            style = type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlarmListItem(
    item: AlarmItemUi,
    clockFormat: ClockFormat,
    weekdayFormat: WeekdayFormat,
    menuExpanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val texts = remember(item, clockFormat, weekdayFormat, resources) {
        alarmCardTexts(item, clockFormat, weekdayFormat, resources)
    }
    // Нестабильный List<DayPillUi> пересобирается только при смене дней или локали (иначе — лишние рекомпозиции).
    val days = remember(item.repeatDays, weekdayFormat) { dayPills(item.repeatDays, weekdayFormat) }
    Box(modifier = modifier.testTag(AlarmListTestTags.card(item.id))) {
        AlarmCard(
            time = texts.time,
            amPm = texts.amPm,
            label = item.label,
            days = days,
            active = item.active,
            subtitle = texts.subtitle,
            contentDescription = texts.contentDescription,
            toggleDescription = texts.toggleDescription,
            // Не оптимистично: тумблер покажет новое значение, когда его запишет движок (поток из БД).
            onToggle = { onToggle() },
            onClick = onClick,
            onClickLabel = stringResource(R.string.alarm_list_action_edit),
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.alarm_list_action_more),
        )
        AlarmActionsMenu(expanded = menuExpanded, onDismiss = onDismissMenu, onDelete = onDeleteRequest)
    }
}

@Composable
private fun AlarmActionsMenu(expanded: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val colors = BalarmTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = BalarmShapes.Button,
        containerColor = colors.surfaceVariant,
    ) {
        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.alarm_list_delete), style = BalarmTheme.typography.body) },
            onClick = onDelete,
            leadingIcon = {
                Icon(
                    painter = painterResource(BalarmIcons.Delete),
                    contentDescription = null,
                    modifier = Modifier.size(BalarmDimens.Icon),
                )
            },
            colors = MenuDefaults.itemColors(textColor = colors.textPrimary, leadingIconColor = colors.textPrimary),
            modifier = Modifier.testTag(AlarmListTestTags.MENU_DELETE),
        )
    }
}

@Composable
internal fun DeleteAlarmDialog(
    item: AlarmItemUi,
    clockFormat: ClockFormat,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.alarm_list_delete_title),
        text = stringResource(R.string.alarm_list_delete_text, clockFormat.time(item.time)),
        confirmText = stringResource(R.string.alarm_list_delete),
        dismissText = stringResource(R.string.alarm_list_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

private const val HEADER_KEY = "header"
private const val EMPTY_KEY = "empty"
private const val CARD_CONTENT_TYPE = "card"
