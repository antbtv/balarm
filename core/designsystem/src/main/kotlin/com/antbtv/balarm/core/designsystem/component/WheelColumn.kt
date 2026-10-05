package com.antbtv.balarm.core.designsystem.component

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.antbtv.balarm.core.designsystem.theme.BalarmTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val WHEEL_ITEM_CONTENT_TYPE = 0

/** Размеры, общие для всех колонок колеса. */
@Immutable
internal class WheelGeometry(val rowHeight: Dp, val visibleRows: Int)

/** Подписи действий TalkBack «больше/меньше» — общие для всех колонок. */
@Immutable
internal class WheelActionLabels(val increase: String, val decrease: String)

/**
 * Одна колонка колеса: `LazyColumn` со snap к центру. `contentPadding` сверху и снизу на (visibleRows / 2) строк
 * ставит «первую видимую» строку ровно в центр, поэтому `scrollToItem(i)` центрирует строку i.
 *
 * Новое значение в центре — haptic + [onValueChange], но только если оно отличается от последнего известного
 * ([value] снаружи или своё предыдущее): начальная установка и внешняя синхронизация не вибрируют.
 *
 * TalkBack: колонка — один узел с именем [name], состоянием (подпись значения), `SetProgress` и действиями
 * «больше/меньше»; строки скрыты от доступности.
 */
@Composable
internal fun WheelColumn(
    labels: List<String>,
    value: Int,
    infinite: Boolean,
    onValueChange: (Int) -> Unit,
    textStyle: TextStyle,
    width: Dp,
    geometry: WheelGeometry,
    name: String,
    actionLabels: WheelActionLabels,
    modifier: Modifier = Modifier,
) {
    val state = rememberWheelColumnState(value, labels.size, infinite, geometry.rowHeight)
    WheelColumnEffects(state, value, onValueChange)
    val scope = rememberCoroutineScope()
    val centered = state.centered
    LazyColumn(
        state = state.listState,
        flingBehavior = rememberSnapFlingBehavior(state.listState, SnapPosition.Center),
        contentPadding = PaddingValues(vertical = geometry.rowHeight * (geometry.visibleRows / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(width)
            .height(geometry.rowHeight * geometry.visibleRows)
            .wheelSemantics(state, centered, labels, name, actionLabels, scope),
    ) {
        items(count = state.itemCount, contentType = { WHEEL_ITEM_CONTENT_TYPE }) { index ->
            WheelRow(
                text = labels[indexToValue(index, state.n)],
                centered = index == state.centered,
                style = textStyle,
                height = geometry.rowHeight,
            )
        }
    }
}

/** Состояние колонки: прокрутка, строка в центре и последнее известное значение. */
@Stable
internal class WheelColumnState(
    val listState: LazyListState,
    val n: Int,
    val infinite: Boolean,
    rowHeightPx: () -> Int,
    initialValue: Int,
) {
    val itemCount: Int = wheelItemCount(n, infinite)

    /** Строка в центре колеса. */
    val centered: Int by derivedStateOf {
        centeredIndex(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, rowHeightPx())
            .coerceIn(0, itemCount - 1)
    }

    /** Последнее известное значение: внешнее или своё отправленное. Отличает шаг пользователя от эха колбэка. */
    var known: Int by mutableIntStateOf(initialValue)

    suspend fun scrollToValue(value: Int) {
        listState.scrollToItem(nearestIndexForValue(centered, value, n, infinite))
    }
}

@Composable
private fun rememberWheelColumnState(value: Int, n: Int, infinite: Boolean, rowHeight: Dp): WheelColumnState {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = valueToStartIndex(value, n, infinite))
    val rowHeightPx = rememberUpdatedState(with(LocalDensity.current) { rowHeight.roundToPx() })
    // Высота строки не в ключах: поворот/fontScale не должны пересоздавать состояние и терять `known`.
    return remember(listState, n, infinite) {
        WheelColumnState(listState, n, infinite, rowHeightPx = { rowHeightPx.value }, initialValue = value)
    }
}

@Composable
private fun WheelColumnEffects(state: WheelColumnState, value: Int, onValueChange: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val currentHaptic by rememberUpdatedState(haptic)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // Значение меняется по мере прохождения центра (не после остановки): «Сохранить» посреди fling берёт видимое.
    LaunchedEffect(state) {
        snapshotFlow { indexToValue(state.centered, state.n) }
            .distinctUntilChanged()
            .collect { centeredValue ->
                if (centeredValue != state.known) {
                    state.known = centeredValue
                    currentHaptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    currentOnValueChange(centeredValue)
                }
            }
    }
    // Внешняя смена значения: мгновенный переход без промежуточных значений → ни haptic, ни колбэка.
    // Во время жеста источник правды — пользователь: родитель возвращает значения с задержкой (ViewModel → StateFlow),
    // и «эхо» устаревшего значения откатывало бы колесо посреди fling. Синхронизируем, когда прокрутка остановилась.
    val currentValue by rememberUpdatedState(value)
    LaunchedEffect(state, value) {
        snapshotFlow { state.listState.isScrollInProgress }.first { !it }
        val target = currentValue
        if (target != state.known) {
            state.known = target
            state.scrollToValue(target)
        }
    }
}

private fun Modifier.wheelSemantics(
    state: WheelColumnState,
    centered: Int,
    labels: List<String>,
    name: String,
    actionLabels: WheelActionLabels,
    scope: CoroutineScope,
): Modifier = semantics {
    val n = state.n
    val current = indexToValue(centered, n)
    contentDescription = name
    stateDescription = labels[current]
    progressBarRangeInfo = ProgressBarRangeInfo(
        current = current.toFloat(),
        range = 0f..(n - 1).toFloat(),
        steps = (n - 2).coerceAtLeast(0),
    )
    setProgress { target ->
        val newValue = progressTargetValue(target, current, n)
        if (newValue != null) scope.launch { state.scrollToValue(newValue) }
        newValue != null
    }
    customActions = listOf(
        CustomAccessibilityAction(actionLabels.increase) { scope.step(state, centered, 1) },
        CustomAccessibilityAction(actionLabels.decrease) { scope.step(state, centered, -1) },
    )
}

/** Шаг колонки на [delta] (действия TalkBack); `false` — край конечной колонки. */
private fun CoroutineScope.step(state: WheelColumnState, centered: Int, delta: Int): Boolean {
    val target = stepIndex(centered, delta, state.n, state.infinite) ?: return false
    launch { state.listState.scrollToItem(target) }
    return true
}

@Composable
private fun WheelRow(text: String, centered: Boolean, style: TextStyle, height: Dp) {
    val colors = BalarmTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // Строки — визуал; TalkBack читает колонку целиком (имя + состояние).
            .semantics { hideFromAccessibility() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = if (centered) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            softWrap = false,
        )
    }
}
