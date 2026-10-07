package com.antbtv.balarm.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antbtv.balarm.core.designsystem.component.BalarmTopBar
import com.antbtv.balarm.core.designsystem.component.HealthStatusRow
import com.antbtv.balarm.core.designsystem.component.HealthStatusUi
import com.antbtv.balarm.core.designsystem.component.PrimaryButton
import com.antbtv.balarm.core.designsystem.component.ScrimEdge
import com.antbtv.balarm.core.designsystem.component.SystemBarScrim
import com.antbtv.balarm.core.designsystem.theme.BalarmDimens
import com.antbtv.balarm.core.designsystem.theme.BalarmShapes
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import com.antbtv.balarm.core.domain.health.HealthItem
import com.antbtv.balarm.core.domain.health.HealthStatus
import com.antbtv.balarm.core.format.rememberClockFormat
import com.antbtv.balarm.core.permissions.titleRes
import com.antbtv.balarm.core.permissions.whyRes

/** Сайт с инструкциями по фоновой работе для телефонов разных производителей (ADR-012 §4). */
internal const val OEM_GUIDE_URL = "https://dontkillmyapp.com"

/**
 * «Здоровье будильника» с ViewModel (FR-REL-7, ADR-014 §5). Статусы перечитываются на каждом `ON_RESUME`
 * (возврат из системных настроек — тот же `ON_RESUME`); эффекты собирает один [HealthEffectsHandler].
 *
 * Полноэкранный (без нижней панели): экран сам обрабатывает `safeDrawing` со всех сторон, `:app` отступов
 * не добавляет. Системный Back экран не перехватывает — его обрабатывает навигация (predictive back), как и
 * в редакторе; кнопка «Назад» в верхней строке вызывает [onClose].
 */
@Composable
fun HealthRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HealthEffectsHandler(effects = viewModel.effects, clockFormat = rememberClockFormat(), onEvent = viewModel::onEvent)
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(HealthEvent.Resumed)
        onPauseOrDispose {}
    }
    HealthScreen(state = state, onEvent = viewModel::onEvent, onClose = onClose, modifier = modifier)
}

/**
 * Список всех пунктов ADR-012 со статусом ✅/⚠️/?, «Исправить» у проблемных, «Повторить планирование»
 * (недоступна, пока идёт повтор), ссылка dontkillmyapp.com и «Я сделал» у OEM-пункта, внизу — «Тестовый
 * будильник через 1 минуту». Пока отчёт загружается — только верхняя строка (без мигания «всё плохо»).
 *
 * @param windowInsets системные отступы (в приложении — `safeDrawing`); тесты и превью задают свои.
 */
@Composable
fun HealthScreen(
    state: HealthUiState,
    onEvent: (HealthEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = BalarmTheme.colors
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(HealthTestTags.ROOT),
        color = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            BalarmTopBar(
                title = stringResource(R.string.health_title),
                onBack = onClose,
                backDescription = stringResource(R.string.settings_back),
            )
            if (!state.loading) {
                val bottomInset = windowInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
                Box(modifier = Modifier.weight(1f)) {
                    HealthList(state = state, bottomInset = bottomInset, onEvent = onEvent)
                    SystemBarScrim(
                        edge = ScrimEdge.Bottom,
                        inset = bottomInset,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag(HealthTestTags.BOTTOM_SCRIM),
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthList(state: HealthUiState, bottomInset: Dp, onEvent: (HealthEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HealthTestTags.LIST),
        contentPadding = PaddingValues(
            start = BalarmDimens.ScreenPadding,
            end = BalarmDimens.ScreenPadding,
            top = BalarmDimens.SpacingSmall,
            bottom = bottomInset + BalarmDimens.ScreenPadding + BalarmDimens.SystemBarScrimFade,
        ),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        items(items = state.items, key = { it.item.name }, contentType = { ITEM_CONTENT_TYPE }) { item ->
            HealthItemCard(
                item = item,
                unscheduledAlarms = state.unscheduledAlarms,
                retrying = state.retrying,
                onEvent = onEvent,
            )
        }
        item(key = TEST_KEY, contentType = TEST_KEY) {
            TestAlarmSection(onClick = { onEvent(HealthEvent.ScheduleTest) })
        }
    }
}

@Composable
private fun HealthItemCard(
    item: HealthItemUi,
    unscheduledAlarms: Int,
    retrying: Boolean,
    onEvent: (HealthEvent) -> Unit,
) {
    val texts = healthItemTexts(item, unscheduledAlarms)
    Surface(
        shape = BalarmShapes.Card,
        color = BalarmTheme.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HealthTestTags.item(item.item)),
    ) {
        Column(modifier = Modifier.padding(vertical = BalarmDimens.SpacingSmall)) {
            HealthStatusRow(
                title = texts.title,
                description = texts.description,
                status = item.status.toUi(),
                actionLabel = texts.actionLabel,
                onAction = {
                    val event = if (item.item == HealthItem.SCHEDULING) {
                        HealthEvent.RetryScheduling
                    } else {
                        HealthEvent.Fix(item.item)
                    }
                    onEvent(event)
                },
                actionEnabled = !(item.item == HealthItem.SCHEDULING && retrying),
            )
            if (item.item == HealthItem.OEM_BACKGROUND) {
                OemExtras(
                    confirmed = item.status == HealthStatus.OK,
                    onConfirmedChange = { onEvent(HealthEvent.SetOemConfirmed(it)) },
                )
            }
        }
    }
}

private class HealthItemTexts(val title: String, val description: String, val actionLabel: String?)

@Composable
private fun healthItemTexts(item: HealthItemUi, unscheduledAlarms: Int): HealthItemTexts {
    val ok = item.status == HealthStatus.OK
    // «Зачем» у планирования и громкости описывает проблему — для ✅ свой текст.
    val why = when {
        ok && item.item == HealthItem.SCHEDULING -> stringResource(R.string.health_scheduling_ok)
        ok && item.item == HealthItem.ALARM_VOLUME -> stringResource(R.string.health_volume_ok)
        else -> stringResource(item.item.whyRes)
    }
    val description = if (!ok && item.item == HealthItem.SCHEDULING && unscheduledAlarms > 0) {
        why + "\n" + stringResource(R.string.health_unscheduled_count, unscheduledAlarms)
    } else {
        why
    }
    val actionLabel = when {
        ok -> null
        item.item == HealthItem.SCHEDULING -> stringResource(R.string.health_retry)
        item.status == HealthStatus.UNCONFIRMED -> stringResource(R.string.health_open_settings)
        else -> stringResource(R.string.health_fix)
    }
    return HealthItemTexts(stringResource(item.item.titleRes), description, actionLabel)
}

/** `HealthStatus` (домен) → `HealthStatusUi` (дизайн-система не знает `:core:domain`). */
internal fun HealthStatus.toUi(): HealthStatusUi = when (this) {
    HealthStatus.OK -> HealthStatusUi.Ok
    HealthStatus.PROBLEM -> HealthStatusUi.Problem
    HealthStatus.UNCONFIRMED -> HealthStatusUi.Unconfirmed
}

/**
 * OEM-пункт: проверить программно нельзя — ссылка на инструкции (браузер, `ACTION_VIEW`; разрешение INTERNET
 * приложению не нужно) и подтверждение «Я сделал». Флажок можно и снять: значение — из статуса пункта.
 */
@Composable
private fun OemExtras(confirmed: Boolean, onConfirmedChange: (Boolean) -> Unit) {
    val colors = BalarmTheme.colors
    val context = LocalContext.current
    // Отступ слева — как у текста строки (после иконки статуса).
    val textStart = BalarmDimens.CardPadding + BalarmDimens.Icon + BalarmDimens.CardGap
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.health_oem_guide),
            style = BalarmTheme.typography.body.copy(textDecoration = TextDecoration.Underline),
            color = colors.primary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BalarmDimens.MinTouch)
                .clickable(
                    onClickLabel = stringResource(R.string.health_oem_guide_action),
                    role = Role.Button,
                ) { openOemGuide(context) }
                .padding(start = textStart, end = BalarmDimens.CardPadding, top = BalarmDimens.SpacingSmall)
                .testTag(HealthTestTags.OEM_GUIDE),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BalarmDimens.MinTouch)
                .toggleable(value = confirmed, role = Role.Checkbox, onValueChange = onConfirmedChange)
                // Флажок (24dp) — в колонке иконки статуса, подпись — по тексту строки.
                .padding(start = BalarmDimens.CardPadding, end = BalarmDimens.CardPadding)
                .testTag(HealthTestTags.OEM_CONFIRM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Флажок без своего обработчика: переключает вся строка (зона тапа — строка целиком).
            Checkbox(
                checked = confirmed,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.primary,
                    uncheckedColor = colors.textSecondary,
                    checkmarkColor = colors.onPrimary,
                ),
            )
            Text(
                text = stringResource(R.string.health_oem_confirm),
                style = BalarmTheme.typography.body,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = BalarmDimens.CardGap),
            )
        }
    }
}

private fun openOemGuide(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, OEM_GUIDE_URL.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.health_no_browser, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.health_no_browser, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun TestAlarmSection(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BalarmDimens.SpacingSmall),
        verticalArrangement = Arrangement.spacedBy(BalarmDimens.CardGap),
    ) {
        Text(
            text = stringResource(R.string.health_test_hint),
            style = BalarmTheme.typography.body,
            color = BalarmTheme.colors.textSecondary,
        )
        PrimaryButton(
            text = stringResource(R.string.health_test_button),
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HealthTestTags.TEST_ALARM),
        )
    }
}

private const val ITEM_CONTENT_TYPE = "health_item"
private const val TEST_KEY = "test_alarm"
